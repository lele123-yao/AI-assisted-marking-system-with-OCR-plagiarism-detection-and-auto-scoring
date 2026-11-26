package com.marking.aimarkingsystem.answer.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marking.aimarkingsystem.answer.dto.PaperAnswerQueryDTO;
import com.marking.aimarkingsystem.answer.dto.PaperImageUploadDTO;
import com.marking.aimarkingsystem.answer.dto.RosterImportDTO;
import com.marking.aimarkingsystem.answer.dto.RosterRowDTO;
import com.marking.aimarkingsystem.answer.dto.TeacherScoreDTO;
import com.marking.aimarkingsystem.answer.entity.ExamAnswer;
import com.marking.aimarkingsystem.answer.entity.ExamAnswerItem;
import com.marking.aimarkingsystem.answer.enums.AnswerSourceEnum;
import com.marking.aimarkingsystem.answer.enums.AnswerStatusEnum;
import com.marking.aimarkingsystem.answer.excel.RosterImportListener;
import com.marking.aimarkingsystem.answer.mapper.ExamAnswerMapper;
import com.marking.aimarkingsystem.answer.vo.PaperAnswerDetailVO;
import com.marking.aimarkingsystem.answer.vo.PaperAnswerListVO;
import com.marking.aimarkingsystem.answer.vo.QuestionResultVO;
import com.marking.aimarkingsystem.answer.vo.RosterImportResultVO;
import com.marking.aimarkingsystem.common.constant.RedisKeyConstant;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.PageResult;
import com.marking.aimarkingsystem.common.result.ResultCode;
import com.marking.aimarkingsystem.common.util.MinioUtil;
import com.marking.aimarkingsystem.common.util.RedisUtil;
import com.marking.aimarkingsystem.mark.service.MarkTaskProducerService;
import com.marking.aimarkingsystem.paper.entity.ExamPaper;
import com.marking.aimarkingsystem.paper.entity.ExamQuestion;
import com.marking.aimarkingsystem.paper.mapper.ExamPaperMapper;
import com.marking.aimarkingsystem.paper.mapper.ExamQuestionMapper;
import com.marking.aimarkingsystem.paper.service.PaperService;
import com.marking.aimarkingsystem.user.entity.User;
import com.marking.aimarkingsystem.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 纸质试卷线下批改辅助服务。
 *
 * 覆盖四个业务动作（本阶段不写 Controller）：
 * 1. 批量导入考生名单：EasyExcel 解析 Excel，为名单中每个考生生成一条
 *    answer_source = PAPER_SCAN 的空白答卷（状态 0 待录入）；
 * 2. 上传答卷扫描图片：图片存 MinIO，objectKey 回填到 exam_answer.paper_img_url；
 * 3. 触发 OCR：只登记 OCR 任务记录（Redis）并把答卷状态推进到 1（OCR识别中），
 *    真正的 MQ 投递与 OCR 调用由模块4实现，本阶段只留 TODO；
 * 4. 教师录分：逐题校验（不超过题目满分、合计等于最终分），
 *    教师可以直接采纳 AI 参考分，确认后状态置为 5 已归档。
 *
 * 数据权限（占位，暂不实现）：
 * 任课教师只能操作自己课程下的纸质答卷。完整实现时：
 * - 先由 paper.courseId 找到 course.teacherId；
 * - 校验 course.teacherId == 当前登录用户 id，不通过抛 403；
 * - 名单导入、图片上传、触发 OCR、录分、查询等所有入口都要走同一校验，
 *   建议抽成 requireTeacherOwnsPaper(paper) 私有方法统一调用。
 * 本阶段只在各入口保留注释，不写实际过滤逻辑，避免把尚不完整的课程归属规则写死。
 *
 * 存储说明：
 * - 逐题得分明细暂以 JSON 形式写入 exam_answer.answer_content
 *   （纸质答卷本身没有线上作答内容，该列在 PAPER_SCAN 场景下专用于得分明细）；
 *   后续若学情分析需要按题目 / 知识点做 SQL 聚合，再拆 exam_answer_item 明细表；
 * - 本阶段不新增任何表，OCR 任务记录放在 Redis（24 小时过期），模块4 接入 MQ 时替换为任务表。
 */
@Service
public class PaperAnswerService {

    private static final Logger log = LoggerFactory.getLogger(PaperAnswerService.class);

    /** 扫描图片在 MinIO 中的目录前缀 */
    private static final String IMAGE_DIRECTORY = "paper";

    /** 单文件大小上限，与 application.yml 的 multipart.max-file-size 保持一致 */
    private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

    /** 允许的扫描件扩展名 */
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "pdf");

    /** 允许的名单文件扩展名 */
    private static final Set<String> ALLOWED_ROSTER_EXTENSIONS = Set.of("xlsx", "xls");

    /** 姓名 / 班级 / 学号的最大长度，与 exam_answer 列定义保持一致 */
    private static final int MAX_TEXT_LENGTH = 64;

    /** OCR 任务记录 TTL：24 小时，模块4 换成任务表后删除 */
    private static final long OCR_TASK_TTL_HOURS = 24L;

    /** OCR 触发锁 TTL：2 分钟，只用于挡住并发重复点击 */
    private static final long OCR_TRIGGER_LOCK_SECONDS = 120L;

    /** 导入批次号中的时间部分 */
    private static final DateTimeFormatter BATCH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** 生成默认批次号时的随机位数 */
    private static final int BATCH_RANDOM_LENGTH = 3;

    private final ExamAnswerMapper examAnswerMapper;

    private final ExamPaperMapper examPaperMapper;

    private final ExamQuestionMapper examQuestionMapper;

    private final UserMapper userMapper;

    private final MinioUtil minioUtil;

    private final RedisUtil redisUtil;

    private final MarkTaskProducerService markTaskProducerService;

    private final PaperAnswerItemService paperAnswerItemService;

    public PaperAnswerService(ExamAnswerMapper examAnswerMapper,
                              ExamPaperMapper examPaperMapper,
                              ExamQuestionMapper examQuestionMapper,
                              UserMapper userMapper,
                              MinioUtil minioUtil,
                              RedisUtil redisUtil,
                              MarkTaskProducerService markTaskProducerService,
                              PaperAnswerItemService paperAnswerItemService) {
        this.examAnswerMapper = examAnswerMapper;
        this.examPaperMapper = examPaperMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.userMapper = userMapper;
        this.minioUtil = minioUtil;
        this.redisUtil = redisUtil;
        this.markTaskProducerService = markTaskProducerService;
        this.paperAnswerItemService = paperAnswerItemService;
    }

    // ==================== 1. 批量导入考生名单 ====================

    /**
     * 批量导入考生名单，为每名考生生成一条空白 PAPER_SCAN 答卷。
     *
     * 校验与容错策略：
     * - 单行错误（学号为空、姓名缺失、文件内重复）只记录到失败明细，不影响其他人导入；
     * - 同一试卷下已存在相同学号的答卷则跳过（重复导入是幂等的）；
     * - 学号能匹配 sys_user.username 就回填 studentId，匹配不到不报错（纸质考生可能没有系统账号）；
     * - 所有写入在同一个事务内，数据库异常时整批回滚，不会留下半批数据。
     *
     * 数据权限（占位）：需校验 paper.courseId 对应课程的 teacherId 是否为当前登录教师。
     *
     * @param dto  导入参数（目标试卷、默认班级）
     * @param file 名单 Excel（列：学号 / 姓名 / 班级）
     * @return 导入结果（批次号、成功/跳过/失败数量与失败明细）
     */
    @Transactional(rollbackFor = Exception.class)
    public RosterImportResultVO importRoster(RosterImportDTO dto, MultipartFile file) {
        ExamPaper paper = requirePaper(dto.getPaperId());
        if (Objects.equals(paper.getStatus(), PaperService.STATUS_ARCHIVED)) {
            throw new BusinessException("试卷已归档，不能再导入考生名单");
        }
        validateRosterFile(file);

        List<RosterRowDTO> rows = readRoster(file);
        if (rows.isEmpty()) {
            throw new BusinessException("Excel 中没有可导入的数据，请检查模板");
        }

        String batchNo = newBatchNo();

        // 已存在的学号：整批一次查询，避免逐行查库
        Set<String> existingStudentNos = examAnswerMapper.selectList(Wrappers.<ExamAnswer>lambdaQuery()
                        .eq(ExamAnswer::getPaperId, paper.getId()))
                .stream()
                .map(ExamAnswer::getStudentNo)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        // 批量匹配系统账号：学号即登录账号
        Set<String> candidateNos = rows.stream()
                .map(row -> trim(row.getStudentNo()))
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Map<String, Long> userIds = loadUserIds(candidateNos);

        RosterImportResultVO result = new RosterImportResultVO();
        result.setBatchNo(batchNo);
        result.setTotalRows(rows.size());

        Set<String> seenInFile = new HashSet<>();
        int success = 0;
        int skipped = 0;

        for (int index = 0; index < rows.size(); index++) {
            RosterRowDTO row = rows.get(index);
            // 表头占第 1 行，数据从第 2 行开始，便于前端定位问题行
            int rowNum = index + 2;

            String studentNo = trim(row.getStudentNo());
            String studentName = trim(row.getStudentName());
            String studentClass = StringUtils.hasText(trim(row.getStudentClass()))
                    ? trim(row.getStudentClass())
                    : trim(dto.getDefaultClass());

            String error = validateRosterRow(studentNo, studentName, studentClass, seenInFile);
            if (error != null) {
                addFailure(result, rowNum, studentNo, error);
                continue;
            }

            if (existingStudentNos.contains(studentNo)) {
                // 重复导入视为"已存在"，跳过而不是报错，保证接口幂等
                skipped++;
                continue;
            }

            ExamAnswer answer = new ExamAnswer();
            answer.setAnswerSource(AnswerSourceEnum.PAPER_SCAN.name());
            answer.setPaperId(paper.getId());
            answer.setStudentId(userIds.get(studentNo));
            answer.setStudentNo(studentNo);
            answer.setStudentName(studentName);
            answer.setStudentClass(studentClass);
            answer.setImportBatchNo(batchNo);
            // 空白答卷：图片、OCR、分数都为空，等待后续上传与识别
            answer.setMarkingStatus(AnswerStatusEnum.PENDING.getCode());
            examAnswerMapper.insert(answer);
            success++;
        }

        result.setSuccessCount(success);
        result.setSkippedCount(skipped);
        result.setFailedCount(result.getFailures().size());
        log.info("考生名单导入完成: paperId={}, batchNo={}, total={}, success={}, skipped={}, failed={}",
                paper.getId(), batchNo, rows.size(), success, skipped, result.getFailures().size());
        return result;
    }

    // ==================== 2. 上传答卷扫描图片 ====================

    /**
     * 上传纸质答卷扫描图片并绑定答卷记录。
     *
     * 处理顺序：先上传 MinIO 拿到 objectKey，再写数据库；
     * 数据库失败时删除刚上传的文件做补偿，避免产生无引用垃圾文件。
     *
     * 状态规则：
     * - 名单导入时已存在的答卷：回填 paperImgUrl，并把旧图片对象删掉；
     * - 名单中没有的学号：以 PAPER_SCAN 来源补建一条答卷；
     * - 若原答卷已有 AI 参考分或 OCR 文本，说明图片被替换，旧识别结果作废，
     *   统一清空并把状态重置为 0（待录入），由教师重新触发 OCR；
     * - OCR / AI 处理中、已归档的答卷不允许替换图片。
     *
     * 数据权限（占位）：同上，需校验试卷归属当前教师。
     *
     * @return MinIO 对象键（写入 exam_answer.paper_img_url）
     */
    @Transactional(rollbackFor = Exception.class)
    public String uploadPaperImage(PaperImageUploadDTO dto, MultipartFile file) {
        ExamPaper paper = requirePaper(dto.getPaperId());
        if (Objects.equals(paper.getStatus(), PaperService.STATUS_ARCHIVED)) {
            throw new BusinessException("试卷已归档，不能再上传答卷图片");
        }
        validateImageFile(file);

        String studentNo = trim(dto.getStudentNo());
        ExamAnswer answer = findPaperAnswer(paper.getId(), studentNo);
        boolean createNew = answer == null;
        if (createNew) {
            answer = new ExamAnswer();
            answer.setAnswerSource(AnswerSourceEnum.PAPER_SCAN.name());
            answer.setPaperId(paper.getId());
            answer.setStudentNo(studentNo);
            answer.setStudentName(trim(dto.getStudentName()));
            answer.setStudentId(loadUserIds(Set.of(studentNo)).get(studentNo));
            answer.setMarkingStatus(AnswerStatusEnum.PENDING.getCode());
        } else {
            requireImageReplaceable(answer);
        }

        String oldObjectKey = answer.getPaperImgUrl();
        String objectKey = minioUtil.upload(file, IMAGE_DIRECTORY);

        try {
            if (createNew) {
                answer.setPaperImgUrl(objectKey);
                examAnswerMapper.insert(answer);
            } else {
                // 图片变了，旧的 OCR 文本 / AI 参考分全部失效，必须重新识别
                examAnswerMapper.update(null, Wrappers.<ExamAnswer>lambdaUpdate()
                        .eq(ExamAnswer::getId, answer.getId())
                        .set(ExamAnswer::getPaperImgUrl, objectKey)
                        .set(ExamAnswer::getMarkingStatus, AnswerStatusEnum.PENDING.getCode())
                        .set(ExamAnswer::getOcrText, null)
                        .set(ExamAnswer::getAiReferenceScore, null)
                        .set(ExamAnswer::getMarkingError, null));
            }
        } catch (RuntimeException e) {
            // 数据库写入失败，删除刚上传的对象，避免桶里出现无主文件
            safeDeleteObject(objectKey);
            throw e;
        }

        if (StringUtils.hasText(oldObjectKey)) {
            safeDeleteObject(oldObjectKey);
        }
        log.info("答卷图片上传成功: answerId={}, paperId={}, studentNo={}, objectKey={}",
                answer.getId(), paper.getId(), studentNo, objectKey);
        return objectKey;
    }

    // ==================== 3. 触发 OCR ====================

    /**
     * 触发 OCR 识别任务。
     *
     * 本阶段只做三件事，不调用 OCR、不发 MQ：
     * 1. 校验答卷已上传图片、状态允许重新识别；
     * 2. 生成 taskId，把任务信息写入 Redis（ocr:task:{taskId}，24 小时过期）；
     * 3. 把答卷状态推进到 1（OCR识别中），异常时回滚状态并清理任务记录。
     *
     * 幂等：Redis SETNX 抢占 ocr:trigger:{answerId}，并发下只有一个请求能创建任务；
     * 同时状态 1/2 本身也会拒绝重复触发，形成双重保护。
     *
     * 数据权限（占位）：同上。
     *
     * TODO 模块4：把任务投递到 MQ（如 ocr.recognize.queue），由消费者调用 OCR 服务，
     * 识别结果回填 ocr_text 并把状态推进到 2（AI辅助批改中）。
     *
     * @param answerId 答卷ID
     * @return OCR 任务ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String triggerOcr(Long answerId) {
        ExamAnswer answer = requireAnswer(answerId);
        requirePaper(answer.getPaperId());

        if (!StringUtils.hasText(answer.getPaperImgUrl())) {
            throw new BusinessException("该答卷尚未上传扫描图片，无法触发OCR");
        }
        Integer status = answer.getMarkingStatus();
        if (Objects.equals(status, AnswerStatusEnum.PROCESSING.getCode())
                || Objects.equals(status, AnswerStatusEnum.AI_MARKING.getCode())) {
            throw new BusinessException("OCR或AI任务处理中，请勿重复触发");
        }
        if (AnswerStatusEnum.isArchived(status)) {
            throw new BusinessException("答卷已确认归档，不能重新识别");
        }

        String lockKey = RedisKeyConstant.OCR_TRIGGER + answerId;
        Boolean locked = redisUtil.setIfAbsent(lockKey, "1", OCR_TRIGGER_LOCK_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException("OCR任务触发中，请稍后重试");
        }

        try {
            // 落库 mark_task(status=0) + 发 MQ，幂等内部处理
            String taskNo = markTaskProducerService.submitOcr(answerId);

            examAnswerMapper.update(null, Wrappers.<ExamAnswer>lambdaUpdate()
                    .eq(ExamAnswer::getId, answerId)
                    .set(ExamAnswer::getMarkingStatus, AnswerStatusEnum.PROCESSING.getCode())
                    .set(ExamAnswer::getMarkingError, null));

            log.info("OCR任务已投递: taskNo={}, answerId={}, objectKey={}",
                    taskNo, answerId, answer.getPaperImgUrl());
            return taskNo;
        } catch (RuntimeException e) {
            safeDeleteKey(lockKey);
            throw e;
        }
    }

    // ==================== 4. 教师手动录入分数 ====================

    /**
     * 教师手动录入逐题得分并确认最终成绩。
     *
     * 校验链（按顺序）：
     * 1. 试卷未归档，OCR / AI 不在处理中（避免人工分数被异步回写覆盖）；
     * 2. 每道题必须属于该试卷，且得分不超过该题满分，同一题不能重复录入；
     * 3. 各题得分之和必须等于 teacherFinalScore（总分不能与明细脱节）。
     *
     * 说明：教师可以直接采纳 AI 参考分，最终成绩以教师提交的为准，
     * 系统不再强制"最终分必须与 AI 总分不同"。
     *
     * 写入：
     * - teacherFinalScore = 最终分；
     * - markingStatus = 5（教师已确认归档）；
     * - markTime = 当前时间；
     * - answerContent = 逐题得分明细 JSON（纸质答卷无线上作答内容，该列在此场景专用于明细）。
     *
     * 数据权限（占位）：同上。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveTeacherScore(Long answerId, TeacherScoreDTO dto) {
        ExamAnswer answer = requireAnswer(answerId);
        ExamPaper paper = requirePaper(answer.getPaperId());

        if (Objects.equals(paper.getStatus(), PaperService.STATUS_ARCHIVED)) {
            throw new BusinessException("试卷已归档，成绩不可修改");
        }
        Integer status = answer.getMarkingStatus();
        if (Objects.equals(status, AnswerStatusEnum.PROCESSING.getCode())
                || Objects.equals(status, AnswerStatusEnum.AI_MARKING.getCode())) {
            throw new BusinessException("OCR或AI任务处理中，暂不能录入分数");
        }

        BigDecimal finalScore = scale(dto.getTeacherFinalScore());
        BigDecimal sum = validateAndSumItems(paper.getId(), dto.getItems());
        if (sum.compareTo(finalScore) != 0) {
            throw new BusinessException("各题得分合计 " + sum + " 与最终得分 " + finalScore + " 不一致");
        }

        examAnswerMapper.update(null, Wrappers.<ExamAnswer>lambdaUpdate()
                .eq(ExamAnswer::getId, answerId)
                .set(ExamAnswer::getTeacherFinalScore, finalScore)
                .set(ExamAnswer::getMarkingStatus, AnswerStatusEnum.ARCHIVED.getCode())
                .set(ExamAnswer::getMarkTime, LocalDateTime.now())
                .set(ExamAnswer::getAnswerContent, JSONUtil.toJsonStr(dto.getItems()))
                .set(ExamAnswer::getMarkingError, null));

        // 逐题教师最终分与评语单独落表，AI 评语保留在 ai_* 列不被覆盖
        paperAnswerItemService.saveFinalItems(answerId, dto.getItems());

        log.info("教师确认成绩: answerId={}, paperId={}, aiReferenceScore={}, teacherFinalScore={}",
                answerId, paper.getId(), answer.getAiReferenceScore(), finalScore);
    }

    // ==================== 5. 分页查询 ====================

    /**
     * 纸质答卷分页查询，只返回 answer_source = PAPER_SCAN 的记录。
     *
     * 实现要点：
     * - 用 MyBatis-Plus 分页插件（PaginationInnerInterceptor）自动完成 count 与 limit；
     * - 试卷标题不能靠 JOIN：先分页查出答卷，再用 selectBatchIds 一次性取试卷标题，
     *   避免 N+1 查询，也不把多表 SQL 写进 Mapper；
     * - keyword 同时模糊匹配学号与姓名，条件用 and(...or...) 包一层括号，
     *   否则会与前面的状态条件形成"或"导致越权/串数据。
     *
     * 数据权限（占位）：应追加"试卷所属课程属于当前教师"的过滤条件。
     */
    public PageResult<PaperAnswerListVO> pageAnswers(PaperAnswerQueryDTO query) {
        Page<ExamAnswer> page = new Page<>(query.getPageNum(), query.getPageSize());
        String keyword = trim(query.getKeyword());

        LambdaQueryWrapper<ExamAnswer> wrapper = Wrappers.<ExamAnswer>lambdaQuery()
                .eq(ExamAnswer::getAnswerSource, AnswerSourceEnum.PAPER_SCAN.name())
                .eq(query.getPaperId() != null, ExamAnswer::getPaperId, query.getPaperId())
                .eq(query.getMarkingStatus() != null, ExamAnswer::getMarkingStatus, query.getMarkingStatus())
                .and(StringUtils.hasText(keyword), condition -> condition
                        .like(ExamAnswer::getStudentNo, keyword)
                        .or()
                        .like(ExamAnswer::getStudentName, keyword))
                .orderByDesc(ExamAnswer::getCreateTime);

        IPage<ExamAnswer> answerPage = examAnswerMapper.selectPage(page, wrapper);
        List<ExamAnswer> answers = answerPage.getRecords();
        if (answers.isEmpty()) {
            return PageResult.of(answerPage, Collections.emptyList());
        }

        Set<Long> paperIds = answers.stream()
                .map(ExamAnswer::getPaperId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> paperTitles = paperIds.isEmpty()
                ? Collections.emptyMap()
                : examPaperMapper.selectBatchIds(paperIds).stream()
                .collect(Collectors.toMap(ExamPaper::getId, ExamPaper::getPaperTitle, (a, b) -> a));

        List<PaperAnswerListVO> records = answers.stream()
                .map(answer -> toListVO(answer, paperTitles.get(answer.getPaperId())))
                .toList();
        return PageResult.of(answerPage, records);
    }

    // ==================== 6. 查看纸质答卷详情 ====================

    /**
     * 查看纸质答卷详情：OCR 文本、AI 参考分、教师最终分、原图地址。
     *
     * 图片同时返回 objectKey 与临时签名地址：
     * - paperImgUrl 是库中永久值，前端可用于重新换取地址；
     * - paperImgAccessUrl 是现算的短时效地址，签名失败（MinIO 不可用）时为 null，
     *   但不影响 OCR 文本、分数等核心数据返回，避免"图片服务抖动导致详情页 500"。
     *
     * 数据权限（占位）：需校验试卷归属当前教师，学生只能查看自己的答卷。
     */
    public PaperAnswerDetailVO getDetail(Long answerId) {
        ExamAnswer answer = requireAnswer(answerId);
        // 线上作答与纸质扫描共用详情视图：线上卷的考生原文存在 answer_content 中
        ExamPaper paper = examPaperMapper.selectById(answer.getPaperId());
        // 数据权限（占位）：校验 paper.courseId 所属课程是否属于当前教师
        PaperAnswerDetailVO vo = toDetailVO(answer, paper);
        vo.setQuestionResults(buildQuestionResults(answer));
        return vo;
    }

    /**
     * 组装逐题结果：题目信息 + AI 分/评语 + 教师分/评语。
     *
     * 兼容老数据：本次改造前 AI 逐题结果只写在 exam_answer.answer_content，
     * 新表没有明细时从该 JSON 里兜底解析 questionId / score / comment，
     * 保证历史答卷仍能展示 AI 逐题评语。
     */
    private List<QuestionResultVO> buildQuestionResults(ExamAnswer answer) {
        List<ExamQuestion> questions = examQuestionMapper.selectList(
                Wrappers.<ExamQuestion>lambdaQuery()
                        .eq(ExamQuestion::getPaperId, answer.getPaperId())
                        .orderByAsc(ExamQuestion::getSortOrder)
                        .orderByAsc(ExamQuestion::getId));
        if (questions.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, ExamAnswerItem> itemMap = paperAnswerItemService.listByAnswer(answer.getId()).stream()
                .collect(Collectors.toMap(ExamAnswerItem::getQuestionId, item -> item, (a, b) -> a));

        Map<Long, BigDecimal> legacyScores = new HashMap<>();
        Map<Long, String> legacyComments = new HashMap<>();
        Map<Long, String> studentAnswers = new HashMap<>();
        Map<Long, List<Map<String, Object>>> pointsByQuestion = new HashMap<>();
        Map<Long, String> gradedByByQuestion = new HashMap<>();
        if (StringUtils.hasText(answer.getAnswerContent())) {
            String content = answer.getAnswerContent().trim();
            try {
                if (JSONUtil.isTypeJSONArray(content)) {
                    JSONArray array = JSONUtil.parseArray(content);
                    for (int i = 0; i < array.size(); i++) {
                        JSONObject object = array.getJSONObject(i);
                        Long questionId = object.getLong("questionId");
                        if (questionId == null) {
                            continue;
                        }
                        String studentAnswer = object.getStr("answer");
                        if (StringUtils.hasText(studentAnswer)) {
                            studentAnswers.put(questionId, studentAnswer);
                        }
                        // 得分点明细与判分来源：让教师能核对"这一分是怎么来的"
                        JSONArray pointArray = object.getJSONArray("points");
                        if (pointArray != null && !pointArray.isEmpty()) {
                            List<Map<String, Object>> pointList = new ArrayList<>();
                            for (int j = 0; j < pointArray.size(); j++) {
                                JSONObject point = pointArray.getJSONObject(j);
                                if (point == null) {
                                    continue;
                                }
                                Map<String, Object> detail = new HashMap<>();
                                detail.put("item", point.getStr("item", ""));
                                detail.put("hit", point.getBool("hit", false));
                                detail.put("score", point.getBigDecimal("score"));
                                pointList.add(detail);
                            }
                            pointsByQuestion.put(questionId, pointList);
                        }
                        String gradedBy = object.getStr("gradedBy");
                        if (StringUtils.hasText(gradedBy)) {
                            gradedByByQuestion.put(questionId, gradedBy);
                        }
                        if (itemMap.isEmpty()) {
                            if (object.get("score") != null) {
                                legacyScores.put(questionId, object.getBigDecimal("score"));
                            }
                            String comment = object.getStr("comment");
                            if (StringUtils.hasText(comment)) {
                                legacyComments.put(questionId, comment);
                            }
                        }
                    }
                } else if (JSONUtil.isTypeJSONObject(content)) {
                    // 兼容更早的历史数据：{"题号": "答案"} 形式的线上作答
                    Map<String, Long> idByNo = new HashMap<>();
                    questions.forEach(question -> {
                        if (question.getQuestionNo() != null) {
                            idByNo.put(question.getQuestionNo().trim(), question.getId());
                        }
                    });
                    JSONObject object = JSONUtil.parseObj(content);
                    object.keySet().forEach(no -> {
                        Long questionId = idByNo.get(no.trim());
                        String studentAnswer = object.getStr(no);
                        if (questionId != null && StringUtils.hasText(studentAnswer)) {
                            studentAnswers.put(questionId, studentAnswer);
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("解析历史逐题作答与评语失败: answerId={}", answer.getId(), e);
            }
        }

        return questions.stream().map(question -> {
            QuestionResultVO vo = new QuestionResultVO();
            vo.setQuestionId(question.getId());
            vo.setQuestionNo(question.getQuestionNo());
            vo.setSectionName(question.getSectionName());
            vo.setQuestionType(question.getQuestionType());
            vo.setQuestionContent(question.getQuestionContent());
            vo.setStandardAnswer(question.getStandardAnswer());
            vo.setScoringRule(question.getScoringRule());
            vo.setFullScore(question.getScore());
            vo.setStudentAnswer(studentAnswers.get(question.getId()));

            ExamAnswerItem item = itemMap.get(question.getId());
            if (item != null) {
                vo.setAiScore(item.getAiScore());
                vo.setAiComment(item.getAiComment());
                vo.setFinalScore(item.getFinalScore());
                vo.setFinalComment(item.getFinalComment());
            } else {
                vo.setAiScore(legacyScores.get(question.getId()));
                vo.setAiComment(legacyComments.get(question.getId()));
            }
            vo.setPoints(pointsByQuestion.get(question.getId()));
            vo.setGradedBy(gradedByByQuestion.get(question.getId()));
            return vo;
        }).toList();
    }

    // ==================== 内部方法 ====================

    /** 校验试卷存在且为纸质线下批改模式 */
    private ExamPaper requirePaper(Long paperId) {
        ExamPaper paper = examPaperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        if (!Objects.equals(paper.getAnswerMode(), PaperService.ANSWER_MODE_PAPER)) {
            throw new BusinessException("该试卷不是纸质线下批改模式");
        }
        // 数据权限（占位）：校验 paper.courseId 所属课程的 teacherId == 当前登录教师
        return paper;
    }

    private ExamAnswer requireAnswer(Long answerId) {
        ExamAnswer answer = examAnswerMapper.selectById(answerId);
        if (answer == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "答卷不存在");
        }
        return answer;
    }

    /** 按试卷 + 学号查找纸质答卷，找不到返回 null */
    private ExamAnswer findPaperAnswer(Long paperId, String studentNo) {
        List<ExamAnswer> answers = examAnswerMapper.selectList(Wrappers.<ExamAnswer>lambdaQuery()
                .eq(ExamAnswer::getPaperId, paperId)
                .eq(ExamAnswer::getStudentNo, studentNo)
                .eq(ExamAnswer::getAnswerSource, AnswerSourceEnum.PAPER_SCAN.name())
                .orderByAsc(ExamAnswer::getId));
        return answers.isEmpty() ? null : answers.get(0);
    }

    /** 实体 → 列表 VO，只映射列表页需要的字段 */
    private PaperAnswerListVO toListVO(ExamAnswer answer, String paperTitle) {
        PaperAnswerListVO vo = new PaperAnswerListVO();
        vo.setId(answer.getId());
        vo.setPaperId(answer.getPaperId());
        vo.setPaperTitle(paperTitle);
        vo.setStudentId(answer.getStudentId());
        vo.setStudentNo(answer.getStudentNo());
        vo.setStudentName(answer.getStudentName());
        vo.setStudentClass(answer.getStudentClass());
        vo.setImportBatchNo(answer.getImportBatchNo());
        vo.setAiReferenceScore(answer.getAiReferenceScore());
        vo.setTeacherFinalScore(answer.getTeacherFinalScore());
        vo.setMarkingStatus(answer.getMarkingStatus());
        vo.setMarkingStatusDesc(AnswerStatusEnum.descOf(answer.getMarkingStatus()));
        vo.setHasImage(StringUtils.hasText(answer.getPaperImgUrl()));
        vo.setCreateTime(answer.getCreateTime());
        vo.setMarkTime(answer.getMarkTime());
        return vo;
    }

    /** 实体 → 详情 VO，包含 OCR 文本、AI 参考分、教师最终分与图片访问地址 */
    private PaperAnswerDetailVO toDetailVO(ExamAnswer answer, ExamPaper paper) {
        PaperAnswerDetailVO vo = new PaperAnswerDetailVO();
        vo.setId(answer.getId());
        vo.setAnswerSource(answer.getAnswerSource());
        AnswerSourceEnum source = AnswerSourceEnum.of(answer.getAnswerSource());
        vo.setAnswerSourceDesc(source == null ? "" : source.getDesc());
        vo.setPaperId(answer.getPaperId());
        vo.setPaperTitle(paper == null ? null : paper.getPaperTitle());
        vo.setStudentId(answer.getStudentId());
        vo.setStudentNo(answer.getStudentNo());
        vo.setStudentName(answer.getStudentName());
        vo.setStudentClass(answer.getStudentClass());
        vo.setImportBatchNo(answer.getImportBatchNo());
        vo.setOcrText(answer.getOcrText());
        vo.setPaperImgUrl(answer.getPaperImgUrl());
        vo.setPaperImgAccessUrl(presignedUrlOrNull(answer.getPaperImgUrl()));
        vo.setAiReferenceScore(answer.getAiReferenceScore());
        vo.setTeacherFinalScore(answer.getTeacherFinalScore());
        vo.setMarkingStatus(answer.getMarkingStatus());
        vo.setMarkingStatusDesc(AnswerStatusEnum.descOf(answer.getMarkingStatus()));
        vo.setMarkingError(answer.getMarkingError());
        vo.setSubmitTime(answer.getSubmitTime());
        vo.setMarkTime(answer.getMarkTime());
        vo.setCreateTime(answer.getCreateTime());
        vo.setUpdateTime(answer.getUpdateTime());
        return vo;
    }

    /** 生成 MinIO 临时访问地址；失败返回 null，不影响其他字段返回 */
    private String presignedUrlOrNull(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        try {
            return minioUtil.getPresignedUrl(objectKey);
        } catch (Exception e) {
            log.warn("生成答卷图片签名地址失败, objectKey={}", objectKey, e);
            return null;
        }
    }

    /** 校验当前状态是否允许替换扫描图片 */
    private void requireImageReplaceable(ExamAnswer answer) {
        Integer status = answer.getMarkingStatus();
        if (Objects.equals(status, AnswerStatusEnum.PROCESSING.getCode())
                || Objects.equals(status, AnswerStatusEnum.AI_MARKING.getCode())) {
            throw new BusinessException("OCR或AI任务处理中，暂不能替换扫描图片");
        }
        if (AnswerStatusEnum.isArchived(status)) {
            throw new BusinessException("答卷已确认归档，不能替换扫描图片");
        }
    }

    /**
     * 校验逐题得分并返回合计分。
     * 题目从 exam_question 按试卷查询，确保传入的 questionId 属于本试卷。
     */
    private BigDecimal validateAndSumItems(Long paperId, List<TeacherScoreDTO.QuestionScore> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("请至少录入一道题的得分");
        }

        Map<Long, ExamQuestion> questionMap = examQuestionMapper.selectList(
                        Wrappers.<ExamQuestion>lambdaQuery().eq(ExamQuestion::getPaperId, paperId))
                .stream()
                .collect(Collectors.toMap(ExamQuestion::getId, question -> question));

        Set<Long> seen = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (TeacherScoreDTO.QuestionScore item : items) {
            if (!seen.add(item.getQuestionId())) {
                throw new BusinessException("题目重复录入：" + item.getQuestionId());
            }
            ExamQuestion question = questionMap.get(item.getQuestionId());
            if (question == null) {
                throw new BusinessException("题目不属于该试卷：" + item.getQuestionId());
            }
            BigDecimal score = scale(item.getScore());
            if (score.signum() < 0) {
                throw new BusinessException("题目得分不能为负数：" + question.getQuestionNo());
            }
            if (question.getScore() != null && score.compareTo(scale(question.getScore())) > 0) {
                throw new BusinessException("题目 " + question.getQuestionNo()
                        + " 得分 " + score + " 超过满分 " + scale(question.getScore()));
            }
            sum = sum.add(score);
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /** 单行名单校验，返回 null 表示通过，否则返回失败原因 */
    private String validateRosterRow(String studentNo, String studentName, String studentClass,
                                     Set<String> seenInFile) {
        if (!StringUtils.hasText(studentNo)) {
            return "学号为空";
        }
        if (studentNo.length() > MAX_TEXT_LENGTH) {
            return "学号超过 " + MAX_TEXT_LENGTH + " 位";
        }
        if (!StringUtils.hasText(studentName)) {
            return "姓名为空";
        }
        if (studentName.length() > MAX_TEXT_LENGTH) {
            return "姓名超过 " + MAX_TEXT_LENGTH + " 位";
        }
        if (studentClass != null && studentClass.length() > MAX_TEXT_LENGTH) {
            return "班级超过 " + MAX_TEXT_LENGTH + " 位";
        }
        if (!seenInFile.add(studentNo)) {
            return "文件内学号重复";
        }
        return null;
    }

    private void addFailure(RosterImportResultVO result, int rowNum, String studentNo, String reason) {
        result.getFailures().add(new RosterImportResultVO.FailedRow(rowNum, studentNo, reason));
    }

    /** 按学号批量匹配系统账号，学号即登录账号 */
    private Map<String, Long> loadUserIds(Set<String> studentNos) {
        if (studentNos == null || studentNos.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectList(Wrappers.<User>lambdaQuery().in(User::getUsername, studentNos))
                .stream()
                .collect(Collectors.toMap(User::getUsername, User::getId, (a, b) -> a));
    }

    private List<RosterRowDTO> readRoster(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            RosterImportListener listener = new RosterImportListener();
            EasyExcel.read(in, RosterRowDTO.class, listener).sheet().doRead();
            return listener.getRows();
        } catch (Exception e) {
            log.error("考生名单 Excel 解析失败", e);
            throw new BusinessException("Excel 解析失败，请使用标准模板：" + e.getMessage());
        }
    }

    private void validateRosterFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("名单文件不能为空");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException("名单文件不能超过 20MB");
        }
        String extension = resolveExtension(file.getOriginalFilename());
        if (extension == null || !ALLOWED_ROSTER_EXTENSIONS.contains(extension)) {
            throw new BusinessException("名单文件仅支持 xlsx / xls 格式");
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("扫描图片不能为空");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException("扫描图片不能超过 20MB");
        }
        String extension = resolveExtension(file.getOriginalFilename());
        if (extension == null || !ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new BusinessException("扫描件仅支持 jpg/jpeg/png/webp/pdf 格式");
        }
    }

    /** 生成导入批次号：yyyyMMdd-HHmmss-随机3位 */
    private String newBatchNo() {
        return LocalDateTime.now().format(BATCH_TIME_FORMAT) + "-"
                + RandomUtil.randomNumbers(BATCH_RANDOM_LENGTH);
    }

    /** 分数统一保留两位小数，避免 BigDecimal 精度导致"33.5 与 33.50 不相等" */
    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
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

    /** 删除 MinIO 对象；失败只记录日志，不影响主流程 */
    private void safeDeleteObject(String objectKey) {
        try {
            minioUtil.delete(objectKey);
        } catch (Exception e) {
            log.warn("删除 MinIO 对象失败, objectKey={}", objectKey, e);
        }
    }

    /** 删除 Redis 键；失败只记录日志 */
    private void safeDeleteKey(String key) {
        try {
            redisUtil.delete(key);
        } catch (Exception e) {
            log.warn("删除 Redis 键失败, key={}", key, e);
        }
    }
}
