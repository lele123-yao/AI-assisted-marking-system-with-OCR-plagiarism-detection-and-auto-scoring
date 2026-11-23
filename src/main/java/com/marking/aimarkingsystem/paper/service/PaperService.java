package com.marking.aimarkingsystem.paper.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.PageResult;
import com.marking.aimarkingsystem.common.result.ResultCode;
import com.marking.aimarkingsystem.course.entity.Course;
import com.marking.aimarkingsystem.course.mapper.CourseMapper;
import com.marking.aimarkingsystem.paper.dto.PaperSaveDTO;
import com.marking.aimarkingsystem.paper.entity.ExamPaper;
import com.marking.aimarkingsystem.paper.entity.ExamQuestion;
import com.marking.aimarkingsystem.paper.mapper.ExamPaperMapper;
import com.marking.aimarkingsystem.paper.mapper.ExamQuestionMapper;
import com.marking.aimarkingsystem.paper.vo.PaperVO;
import com.marking.aimarkingsystem.user.entity.User;
import com.marking.aimarkingsystem.user.mapper.UserMapper;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 试卷服务：创建、编辑（草稿保存）、状态流转，并区分线上作答 / 纸质线下阅卷两种模式。
 *
 * 状态机（只允许相邻单向流转）：
 * 0 草稿 DRAFT -> 1 待考试 PENDING -> 2 阅卷中 MARKING -> 3 已归档 ARCHIVED
 *
 * 数据权限说明（占位，暂不实现）：
 * 教师的可见/可操作范围是"自己课程下的试卷"，即 exam_paper.course_id 属于
 * course 表中 teacher_id = 当前登录用户的课程。完整实现时：
 * - 列表查询：先查出当前教师负责的 courseId 集合，再 in 条件过滤；
 * - 单条操作：查出试卷后校验其 courseId 归属，不通过抛 403。
 * 本阶段只保留注释，不做实际过滤。
 */
@Service
public class PaperService {

    /** 状态：草稿（可自由增删改题目） */
    public static final int STATUS_DRAFT = 0;

    /** 状态：待考试（题目应已定稿，等待开考） */
    public static final int STATUS_PENDING = 1;

    /** 状态：阅卷中（已产生答卷，开始 AI / 人工批改） */
    public static final int STATUS_MARKING = 2;

    /** 状态：已归档（成绩定稿，只读） */
    public static final int STATUS_ARCHIVED = 3;

    /** 作答模式：线上作答 */
    public static final int ANSWER_MODE_ONLINE = 1;

    /** 作答模式：纸质线下阅卷 */
    public static final int ANSWER_MODE_PAPER = 2;

    /** AI 阅卷默认开启 */
    private static final int AI_MARKING_ENABLED = 1;

    /** 合法状态流转表：key 为当前状态，value 为允许流转到的目标状态 */
    private static final Map<Integer, Set<Integer>> TRANSITIONS = Map.of(
            STATUS_DRAFT, Set.of(STATUS_PENDING),
            STATUS_PENDING, Set.of(STATUS_MARKING),
            STATUS_MARKING, Set.of(STATUS_ARCHIVED));

    private final ExamPaperMapper examPaperMapper;

    private final ExamQuestionMapper examQuestionMapper;

    private final CourseMapper courseMapper;

    private final UserMapper userMapper;

    public PaperService(ExamPaperMapper examPaperMapper,
                        ExamQuestionMapper examQuestionMapper,
                        CourseMapper courseMapper,
                        UserMapper userMapper) {
        this.examPaperMapper = examPaperMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
    }

    /**
     * 创建试卷，初始状态固定为草稿。
     *
     * 说明：
     * - status / totalScore / questionCount / creatorId 都不从 DTO 取，由服务端决定，防止前端伪造；
     * - 线上作答模式要求填写开考与收卷时间，纸质模式允许为空。
     *
     * 数据权限占位：后续需校验 dto.courseId 对应的课程是否属于当前登录教师。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createPaper(PaperSaveDTO dto) {
        requireCourse(dto.getCourseId());
        assertPaperNoUnique(dto.getPaperNo(), null);
        validateAnswerMode(dto);

        ExamPaper paper = new ExamPaper();
        applyDto(paper, dto);
        paper.setStatus(STATUS_DRAFT);
        paper.setTotalScore(BigDecimal.ZERO);
        paper.setQuestionCount(0);
        paper.setAiMarking(dto.getAiMarking() == null ? AI_MARKING_ENABLED : dto.getAiMarking());
        paper.setCreatorId(requireCurrentUserId());

        examPaperMapper.insert(paper);
        return paper.getId();
    }

    /**
     * 草稿保存（编辑试卷内容）。
     *
     * 只有草稿状态可以编辑：一旦进入待考试，题目与分值已对学生可见，
     * 再改内容会造成"已发卷却变更题目"的数据不一致，因此必须走状态机先撤回草案。
     *
     * 数据权限占位：后续需校验试卷所属课程的教师是否为当前登录用户。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveDraft(Long paperId, PaperSaveDTO dto) {
        ExamPaper exists = requirePaper(paperId);

        if (!Objects.equals(exists.getStatus(), STATUS_DRAFT)) {
            throw new BusinessException("只有草稿状态的试卷才能编辑");
        }

        assertPaperNoUnique(dto.getPaperNo(), paperId);
        requireCourse(dto.getCourseId());
        validateAnswerMode(dto);

        // 线上/纸质两种模式的作答数据来源完全不同，已有题目后切换会造成数据无法对应
        boolean modeChanged = !Objects.equals(exists.getAnswerMode(), dto.getAnswerMode());
        if (modeChanged && exists.getQuestionCount() != null && exists.getQuestionCount() > 0) {
            throw new BusinessException("试卷已录入题目，不允许切换作答模式");
        }

        ExamPaper update = new ExamPaper();
        update.setId(paperId);
        applyDto(update, dto);
        if (update.getAiMarking() == null) {
            update.setAiMarking(exists.getAiMarking());
        }
        examPaperMapper.updateById(update);
    }

    /**
     * 试卷状态流转。
     *
     * 允许的流转：草稿 -> 待考试 -> 阅卷中 -> 已归档，不允许跳级与回退。
     * 进入"待考试"前做完整性校验：必须有题目、总分大于 0，
     * 线上模式还必须有开考与收卷时间（否则学生无法进入作答）。
     *
     * 数据权限占位：后续需校验试卷归属当前登录教师。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long paperId, Integer targetStatus) {
        ExamPaper paper = requirePaper(paperId);

        if (!canTransit(paper.getStatus(), targetStatus)) {
            throw new BusinessException("不允许从当前状态流转到目标状态");
        }

        if (Objects.equals(targetStatus, STATUS_PENDING)) {
            if (paper.getQuestionCount() == null || paper.getQuestionCount() == 0) {
                throw new BusinessException("试卷还没有题目，无法发布");
            }
            if (paper.getTotalScore() == null
                    || paper.getTotalScore().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("试卷总分必须大于 0，无法发布");
            }
            if (Objects.equals(paper.getAnswerMode(), ANSWER_MODE_ONLINE)
                    && (paper.getStartTime() == null || paper.getEndTime() == null)) {
                throw new BusinessException("线上作答试卷必须设置开考时间与收卷时间");
            }
        }

        ExamPaper update = new ExamPaper();
        update.setId(paperId);
        update.setStatus(targetStatus);
        examPaperMapper.updateById(update);
    }

    /**
     * 分页查询试卷。
     *
     * @param courseId   课程过滤，可为空
     * @param keyword    标题/试卷编号模糊关键字，可为空
     * @param status     状态过滤，可为空
     * @param answerMode 作答模式过滤（1 线上，2 纸质），可为空
     *
     * 数据权限占位：后续当登录角色为 TEACHER 时，应只查询自己课程下的试卷，
     * 可先查当前教师的 courseId 集合再 in 过滤；ADMIN 不加限制。当前按传入参数查询。
     */
    public PageResult<PaperVO> pagePapers(Integer pageNum, Integer pageSize, Long courseId,
                                          String keyword, Integer status, Integer answerMode) {
        Page<ExamPaper> page = new Page<>(pageNum, pageSize);
        Page<ExamPaper> paperPage = examPaperMapper.selectPage(page,
                Wrappers.<ExamPaper>lambdaQuery()
                        .eq(courseId != null, ExamPaper::getCourseId, courseId)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(ExamPaper::getPaperTitle, keyword)
                                .or()
                                .like(ExamPaper::getPaperNo, keyword))
                        .eq(status != null, ExamPaper::getStatus, status)
                        .eq(answerMode != null, ExamPaper::getAnswerMode, answerMode)
                        .orderByDesc(ExamPaper::getCreateTime));

        List<PaperVO> records = paperPage.getRecords().stream()
                .map(this::toPaperVO)
                .toList();
        fillCourseNames(records);
        return PageResult.of(paperPage, records);
    }

    /**
     * 刷新试卷汇总字段（题目总分、题目数量）。
     *
     * 由题目服务在新增/编辑题目后调用，属于"冗余字段回写"：
     * 列表页与本阶段的状态校验都直接读 exam_paper 的汇总值，不再实时聚合题目表。
     */
    @Transactional(rollbackFor = Exception.class)
    public void refreshSummary(Long paperId) {
        List<ExamQuestion> questions = examQuestionMapper.selectList(
                Wrappers.<ExamQuestion>lambdaQuery()
                        .select(ExamQuestion::getScore)
                        .eq(ExamQuestion::getPaperId, paperId));

        BigDecimal total = questions.stream()
                .map(ExamQuestion::getScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ExamPaper update = new ExamPaper();
        update.setId(paperId);
        update.setTotalScore(total);
        update.setQuestionCount(questions.size());
        examPaperMapper.updateById(update);
    }

    /** 状态流转合法性判断 */
    private boolean canTransit(Integer current, Integer target) {
        if (current == null || target == null || Objects.equals(current, target)) {
            return false;
        }
        return TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
    }

    /**
     * 作答模式校验：
     * - 线上作答：必须设置开考/收卷时间，且收卷晚于开考；
     * - 纸质线下阅卷：时间字段可为空（考试时间以线下实际安排为准）。
     */
    private void validateAnswerMode(PaperSaveDTO dto) {
        if (Objects.equals(dto.getAnswerMode(), ANSWER_MODE_ONLINE)
                && (dto.getStartTime() == null || dto.getEndTime() == null)) {
            throw new BusinessException("线上作答模式必须填写开考时间与收卷时间");
        }
        if (dto.getStartTime() != null && dto.getEndTime() != null
                && !dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BusinessException("收卷时间必须晚于开考时间");
        }
    }

    private void assertPaperNoUnique(String paperNo, Long excludeId) {
        Long count = examPaperMapper.selectCount(
                Wrappers.<ExamPaper>lambdaQuery()
                        .eq(ExamPaper::getPaperNo, paperNo)
                        .ne(excludeId != null, ExamPaper::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("试卷编号已存在");
        }
    }

    private void requireCourse(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "所属课程不存在");
        }
    }

    private ExamPaper requirePaper(Long paperId) {
        ExamPaper paper = examPaperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        return paper;
    }

    /** 创建人取当前登录用户，不信任前端传值 */
    private Long requireCurrentUserId() {
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
        return user.getId();
    }

    private void applyDto(ExamPaper paper, PaperSaveDTO dto) {
        paper.setPaperNo(dto.getPaperNo());
        paper.setPaperTitle(dto.getPaperTitle());
        paper.setCourseId(dto.getCourseId());
        paper.setAnswerMode(dto.getAnswerMode());
        paper.setPassScore(dto.getPassScore());
        paper.setDuration(dto.getDuration());
        paper.setStartTime(dto.getStartTime());
        paper.setEndTime(dto.getEndTime());
        paper.setAiMarking(dto.getAiMarking());
        paper.setDescription(dto.getDescription());
        paper.setPdfUrl(dto.getPdfUrl());
        paper.setImageUrl(dto.getImageUrl());
    }

    /** 批量填充课程名称，避免分页结果逐条查课程表 */
    private void fillCourseNames(List<PaperVO> papers) {
        if (papers.isEmpty()) {
            return;
        }

        List<Long> courseIds = papers.stream()
                .map(PaperVO::getCourseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (courseIds.isEmpty()) {
            return;
        }

        Map<Long, String> nameMap = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getCourseName));
        papers.forEach(vo -> vo.setCourseName(nameMap.get(vo.getCourseId())));
    }

    private PaperVO toPaperVO(ExamPaper paper) {
        PaperVO vo = new PaperVO();
        vo.setId(paper.getId());
        vo.setPaperNo(paper.getPaperNo());
        vo.setPaperTitle(paper.getPaperTitle());
        vo.setCourseId(paper.getCourseId());
        vo.setAnswerMode(paper.getAnswerMode());
        vo.setStatus(paper.getStatus());
        vo.setTotalScore(paper.getTotalScore());
        vo.setPassScore(paper.getPassScore());
        vo.setQuestionCount(paper.getQuestionCount());
        vo.setDuration(paper.getDuration());
        vo.setStartTime(paper.getStartTime());
        vo.setEndTime(paper.getEndTime());
        vo.setAiMarking(paper.getAiMarking());
        vo.setCreatorId(paper.getCreatorId());
        vo.setDescription(paper.getDescription());
        vo.setPdfUrl(paper.getPdfUrl());
        vo.setImageUrl(paper.getImageUrl());
        vo.setCreateTime(paper.getCreateTime());
        vo.setUpdateTime(paper.getUpdateTime());
        return vo;
    }
}
