package com.marking.aimarkingsystem.answer.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.answer.dto.AnswerSubmitDTO;
import com.marking.aimarkingsystem.answer.entity.ExamAnswer;
import com.marking.aimarkingsystem.answer.mapper.ExamAnswerMapper;
import com.marking.aimarkingsystem.common.constant.RedisKeyConstant;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.ResultCode;
import com.marking.aimarkingsystem.common.util.MinioUtil;
import com.marking.aimarkingsystem.common.util.RedisUtil;
import com.marking.aimarkingsystem.paper.entity.ExamPaper;
import com.marking.aimarkingsystem.paper.mapper.ExamPaperMapper;
import com.marking.aimarkingsystem.paper.service.PaperService;
import com.marking.aimarkingsystem.mark.service.MarkTaskProducerService;
import com.marking.aimarkingsystem.plagiarism.service.PlagiarismService;
import com.marking.aimarkingsystem.user.entity.User;
import com.marking.aimarkingsystem.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 答卷服务（当前阶段只做「线上答卷提交」与「答卷附件上传」）。
 *
 * 本阶段边界：
 * - 只保存答卷记录，不调用 AI、不计算分数；
 * - 异步阅卷任务投递留给模块4（见 submitOnline 末尾的 TODO）；
 * - 纸质扫描答卷的业务流程不在本阶段实现。
 *
 * 幂等设计（防止重复提交产生重复阅卷任务）：
 * 1. 第一道：Redis SET NX EX 抢占幂等键 answer:submit:{paperId}:{studentId}，
 *    抢不到说明同一个学生正在提交 / 刚提交过，直接拒绝；
 * 2. 第二道：数据库按 (paperId, studentId) 统计已有答卷，存在则拒绝。
 *    这是 Redis 过期或重启后的兜底；最终一致性建议再给 exam_answer 加
 *    UNIQUE KEY uk_paper_student (paper_id, student_id) 由数据库兜底；
 * 3. 业务异常时释放幂等键，让用户修正参数后能立即重试；
 *    提交成功则保留到 TTL 结束，避免连点。
 *
 * 数据权限说明：
 * studentId 一律取当前登录用户，绝不从 DTO 接收，学生无法替他人交卷。
 */
@Service
public class AnswerService {

    private static final Logger log = LoggerFactory.getLogger(AnswerService.class);

    /** 答卷来源：线上作答（与建表 SQL 注释中的枚举值保持一致） */
    public static final String SOURCE_ONLINE = "ONLINE";

    /** 阅卷状态：待处理（模块4的异步任务消费后推进到排队中/阅卷中/已完成） */
    public static final int MARKING_STATUS_PENDING = 0;

    /** 允许提交答卷的试卷状态：1 待考试、2 阅卷中（草稿与归档不允许提交） */
    private static final Set<Integer> SUBMITTABLE_STATUS = Set.of(
            PaperService.STATUS_PENDING, PaperService.STATUS_MARKING);

    /** 幂等键有效期：5 分钟，覆盖一次提交请求的正常处理时长 */
    private static final long IDEMPOTENT_TTL_SECONDS = 300L;

    /** 答卷附件允许的扩展名 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "pdf");

    /** 单文件大小上限，与 application.yml 的 multipart.max-file-size 保持一致 */
    private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

    /** 答卷附件在 MinIO 中的目录前缀 */
    private static final String ANSWER_DIRECTORY = "answer";

    private final ExamAnswerMapper examAnswerMapper;

    private final ExamPaperMapper examPaperMapper;

    private final UserMapper userMapper;

    private final RedisUtil redisUtil;

    private final MinioUtil minioUtil;

    private final MarkTaskProducerService markTaskProducerService;

    private final PlagiarismService plagiarismService;

    public AnswerService(ExamAnswerMapper examAnswerMapper,
                         ExamPaperMapper examPaperMapper,
                         UserMapper userMapper,
                         RedisUtil redisUtil,
                         MinioUtil minioUtil,
                         MarkTaskProducerService markTaskProducerService,
                         PlagiarismService plagiarismService) {
        this.examAnswerMapper = examAnswerMapper;
        this.examPaperMapper = examPaperMapper;
        this.userMapper = userMapper;
        this.redisUtil = redisUtil;
        this.minioUtil = minioUtil;
        this.markTaskProducerService = markTaskProducerService;
        this.plagiarismService = plagiarismService;
    }

    /**
     * 学生提交线上答卷。
     *
     * 处理流程：
     * 1. 取当前登录学生（studentId / studentNo 都不信任前端）；
     * 2. 校验试卷存在、是线上模式、处于可提交状态、当前在考试时间窗内；
     * 3. Redis 幂等键抢占，失败即视为重复提交；
     * 4. 数据库兜底查重后插入答卷（来源 ONLINE，状态 0 待处理）；
     * 5. 返回答卷 id，MQ 投递留给模块4。
     *
     * @return 新生成的答卷 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long submitOnline(AnswerSubmitDTO dto) {
        // 1. 身份以 token 为准
        User student = requireCurrentUser();

        // 2. 试卷与考试窗口校验
        ExamPaper paper = examPaperMapper.selectById(dto.getPaperId());
        if (paper == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        validatePaper(paper);

        // 3. Redis 幂等：SET NX EX 是原子操作，并发下只有一个请求能写入成功
        String idempotentKey = RedisKeyConstant.ANSWER_SUBMIT_IDEMPOTENT
                + paper.getId() + ":" + student.getId();
        Boolean acquired = redisUtil.setIfAbsent(
                idempotentKey, String.valueOf(student.getId()), IDEMPOTENT_TTL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException("答卷正在提交中或已提交，请勿重复操作");
        }

        try {
            // 4. 数据库兜底查重：Redis 重启/过期后仍能拦住重复提交
            Long exists = examAnswerMapper.selectCount(Wrappers.<ExamAnswer>lambdaQuery()
                    .eq(ExamAnswer::getPaperId, paper.getId())
                    .eq(ExamAnswer::getStudentId, student.getId()));
            if (exists != null && exists > 0) {
                throw new BusinessException("该试卷已提交过答卷，不能重复提交");
            }

            ExamAnswer answer = new ExamAnswer();
            answer.setAnswerSource(SOURCE_ONLINE);
            answer.setPaperId(paper.getId());
            answer.setStudentId(student.getId());
            // 学号快照：当前系统以登录账号作为学号，后续如有独立学号字段再从用户扩展表取
            answer.setStudentNo(student.getUsername());
            answer.setAnswerContent(dto.getAnswerContent());
            // 初始状态 0 待处理，由模块4的异步阅卷任务负责推进
            answer.setMarkingStatus(MARKING_STATUS_PENDING);
            answer.setSubmitTime(LocalDateTime.now());
            examAnswerMapper.insert(answer);

            // 立即生成查重指纹：AI 阅卷完成后 answerContent 会被逐题得分明细覆盖，
            // 必须赶在异步任务消费前用学生原始作答算好指纹
            plagiarismService.generateFingerprintQuietly(answer.getId());

            markTaskProducerService.submitAiMark(answer.getId());
            log.info("线上答卷提交成功: answerId={}, paperId={}, studentId={}",
                    answer.getId(), paper.getId(), student.getId());
            return answer.getId();
        } catch (RuntimeException e) {
            // 业务失败释放幂等键，否则用户改正参数后 5 分钟内都无法重试
            releaseIdempotentKey(idempotentKey);
            throw e;
        }
    }

    /**
     * 上传答卷附件到 MinIO。
     *
     * 只返回 objectKey（如 answer/2026/11/12/xxx.jpg），前端把它随答卷一起提交；
     * 数据库不保存签名 URL（会过期），展示时再由后端换取临时地址。
     *
     * @return MinIO 对象键
     */
    public String uploadAnswerFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException("答卷附件不能超过 20MB");
        }

        String extension = resolveExtension(file.getOriginalFilename());
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("答卷附件仅支持 jpg/jpeg/png/webp/pdf 格式");
        }

        return minioUtil.upload(file, ANSWER_DIRECTORY);
    }

    // ==================== 内部方法 ====================

    /** 取当前登录用户，身份来源只有 SecurityContext */
    private User requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, authentication.getName()));
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录用户不存在");
        }
        return user;
    }

    /** 校验试卷是否允许线上提交 */
    private void validatePaper(ExamPaper paper) {
        if (!Objects.equals(paper.getAnswerMode(), PaperService.ANSWER_MODE_ONLINE)) {
            throw new BusinessException("该试卷不是线上作答模式，无法在线提交");
        }
        if (!SUBMITTABLE_STATUS.contains(paper.getStatus())) {
            throw new BusinessException("试卷当前状态不允许提交答卷");
        }

        LocalDateTime now = LocalDateTime.now();
        if (paper.getStartTime() != null && now.isBefore(paper.getStartTime())) {
            throw new BusinessException("考试尚未开始，无法提交答卷");
        }
        if (paper.getEndTime() != null && now.isAfter(paper.getEndTime())) {
            throw new BusinessException("考试已结束，无法提交答卷");
        }
    }

    /** 释放幂等键；释放失败只记录日志，不影响原始异常抛出 */
    private void releaseIdempotentKey(String key) {
        try {
            redisUtil.delete(key);
        } catch (Exception e) {
            log.warn("释放答卷幂等键失败, key={}", key, e);
        }
    }

    private String resolveExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
