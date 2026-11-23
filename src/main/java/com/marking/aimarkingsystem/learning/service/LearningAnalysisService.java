package com.marking.aimarkingsystem.learning.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marking.aimarkingsystem.answer.entity.ExamAnswer;
import com.marking.aimarkingsystem.answer.enums.AnswerSourceEnum;
import com.marking.aimarkingsystem.answer.enums.AnswerStatusEnum;
import com.marking.aimarkingsystem.answer.mapper.ExamAnswerMapper;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.PageResult;
import com.marking.aimarkingsystem.common.result.ResultCode;
import com.marking.aimarkingsystem.common.util.CurrentUserUtil;
import com.marking.aimarkingsystem.common.util.DeepSeekLlmUtil;
import com.marking.aimarkingsystem.common.util.MinioUtil;
import com.marking.aimarkingsystem.knowledge.entity.QuestionKnowledge;
import com.marking.aimarkingsystem.knowledge.entity.QuestionKnowledgeRel;
import com.marking.aimarkingsystem.knowledge.mapper.QuestionKnowledgeMapper;
import com.marking.aimarkingsystem.knowledge.mapper.QuestionKnowledgeRelMapper;
import com.marking.aimarkingsystem.learning.dto.LearningAnalysisCreateDTO;
import com.marking.aimarkingsystem.learning.dto.LearningAnalysisQueryDTO;
import com.marking.aimarkingsystem.learning.entity.LearningAnalysisReport;
import com.marking.aimarkingsystem.learning.mapper.LearningAnalysisReportMapper;
import com.marking.aimarkingsystem.learning.util.PptUtil;
import com.marking.aimarkingsystem.learning.vo.LearningAnalysisReportListVO;
import com.marking.aimarkingsystem.learning.vo.LearningAnalysisReportVO;
import com.marking.aimarkingsystem.learning.vo.LearningStatisticsVO;
import com.marking.aimarkingsystem.learning.vo.PptDownloadVO;
import com.marking.aimarkingsystem.learning.vo.StudentLearningCardVO;
import com.marking.aimarkingsystem.paper.entity.ExamPaper;
import com.marking.aimarkingsystem.paper.entity.ExamQuestion;
import com.marking.aimarkingsystem.paper.mapper.ExamPaperMapper;
import com.marking.aimarkingsystem.paper.mapper.ExamQuestionMapper;
import com.marking.aimarkingsystem.user.entity.User;
import com.marking.aimarkingsystem.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 学情智能分析服务（模块8）。
 *
 * 一条链路做三件事：
 * 1. 统计：从 exam_answer / exam_question / question_knowledge_rel 实时聚合出
 *    整体指标、班级指标、高频错题、知识点得分率（不落库，避免与答卷表不一致）；
 * 2. 讲评：把统计快照拼成结构化 Prompt，调用 DeepSeek 生成 Markdown 教学讲评；
 * 3. 课件：用 PptUtil（Apache POI）生成 pptx，上传 MinIO，只把 objectKey 写入报告表。
 *
 * 为什么先插入"生成中"再更新：
 * - 大模型调用 + PPT 生成耗时较长，先落库可以让前端立即拿到 reportId 并轮询状态；
 * - 失败时保留一条 status=2 的记录和 errorMsg，便于教师看到失败原因并重试；
 * - 本方法刻意不加 @Transactional：若整体回滚，失败记录也会被一起回滚，
 *   就失去了"失败可追溯"的意义。报告写入是单表操作，不存在跨表一致性问题。
 *
 * 数据权限（占位）：正式实现时应校验 paper.courseId 所属课程的任课教师是否为当前用户。
 */
@Service
public class LearningAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(LearningAnalysisService.class);

    /** 报告生成状态：0 生成中，1 成功，2 失败 */
    private static final int STATUS_GENERATING = 0;
    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILED = 2;

    /** 当前阶段只支持单试卷报告 */
    private static final String REPORT_TYPE_PAPER = "PAPER";

    /** 薄弱题阈值：得分率低于 60% */
    private static final double WEAK_QUESTION_RATE = 0.6d;

    /** 进入 Prompt 的薄弱题/知识点条数上限，避免 Prompt 过长 */
    private static final int MAX_PROMPT_ITEMS = 10;

    /** 优秀线比例：总分 90% */
    private static final BigDecimal EXCELLENT_RATE = new BigDecimal("0.9");

    /** 及格线兜底比例：总分 60% */
    private static final BigDecimal DEFAULT_PASS_RATE = new BigDecimal("0.6");

    private static final String SYSTEM_PROMPT = "你是一位资深高校课程教学专家，擅长基于考试数据撰写学情分析报告。"
            + "请使用规范的 Markdown 输出，必须包含以下章节：一、整体学情概览；二、班级差异分析；"
            + "三、高频错题讲评；四、知识点薄弱分析；五、教学改进建议。"
            + "要求：所有结论必须基于给定数据，不得编造数据；讲评要具体到题目与知识点；"
            + "建议要可执行、贴合课堂；不要输出代码块，不要输出与报告无关的客套话。";

    private final LearningAnalysisReportMapper learningAnalysisReportMapper;
    private final ExamPaperMapper examPaperMapper;
    private final ExamAnswerMapper examAnswerMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionKnowledgeRelMapper questionKnowledgeRelMapper;
    private final QuestionKnowledgeMapper questionKnowledgeMapper;
    private final DeepSeekLlmUtil deepSeekLlmUtil;
    private final PptUtil pptUtil;
    private final MinioUtil minioUtil;
    private final UserMapper userMapper;

    public LearningAnalysisService(LearningAnalysisReportMapper learningAnalysisReportMapper,
                                   ExamPaperMapper examPaperMapper,
                                   ExamAnswerMapper examAnswerMapper,
                                   ExamQuestionMapper examQuestionMapper,
                                   QuestionKnowledgeRelMapper questionKnowledgeRelMapper,
                                   QuestionKnowledgeMapper questionKnowledgeMapper,
                                   DeepSeekLlmUtil deepSeekLlmUtil,
                                   PptUtil pptUtil,
                                   MinioUtil minioUtil,
                                   UserMapper userMapper) {
        this.learningAnalysisReportMapper = learningAnalysisReportMapper;
        this.examPaperMapper = examPaperMapper;
        this.examAnswerMapper = examAnswerMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.questionKnowledgeRelMapper = questionKnowledgeRelMapper;
        this.questionKnowledgeMapper = questionKnowledgeMapper;
        this.deepSeekLlmUtil = deepSeekLlmUtil;
        this.pptUtil = pptUtil;
        this.minioUtil = minioUtil;
        this.userMapper = userMapper;
    }

    // ==================== 统计 ====================

    /**
     * 计算学情统计快照（不落库）。
     *
     * 统计口径见 {@link LearningStatisticsVO} 类注释；
     * 逐题明细取 exam_answer.answer_content 中 AI 写入的 [{questionId, score, comment}]，
     * 未参与 AI 阅卷 / 未录分的答卷不会污染题目得分率。
     */
    public LearningStatisticsVO statistics(Long paperId) {
        ExamPaper paper = requirePaper(paperId);
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户

        List<ExamAnswer> answers = examAnswerMapper.selectList(Wrappers.<ExamAnswer>lambdaQuery()
                .eq(ExamAnswer::getPaperId, paperId)
                .orderByAsc(ExamAnswer::getStudentNo));
        List<ExamQuestion> questions = examQuestionMapper.selectList(Wrappers.<ExamQuestion>lambdaQuery()
                .eq(ExamQuestion::getPaperId, paperId)
                .orderByAsc(ExamQuestion::getSortOrder));
        Map<Long, ExamQuestion> questionMap = questions.stream()
                .filter(question -> question.getId() != null)
                .collect(Collectors.toMap(ExamQuestion::getId, question -> question, (a, b) -> a));

        BigDecimal totalScore = paper.getTotalScore() == null
                ? new BigDecimal("100.00")
                : scale(paper.getTotalScore());
        BigDecimal passScore = paper.getPassScore() == null
                ? scale(totalScore.multiply(DEFAULT_PASS_RATE))
                : scale(paper.getPassScore());
        BigDecimal excellentLine = scale(totalScore.multiply(EXCELLENT_RATE));

        LearningStatisticsVO vo = new LearningStatisticsVO();
        vo.setPaperId(paper.getId());
        vo.setPaperTitle(paper.getPaperTitle());
        vo.setCourseId(paper.getCourseId());
        vo.setTotalScore(totalScore);
        vo.setPassScore(passScore);

        fillOverall(vo, answers, passScore, excellentLine);
        vo.setClassMetrics(buildClassMetrics(answers, passScore, excellentLine));

        Map<Long, QuestionAgg> questionAggs = aggregateQuestions(answers, questionMap);
        vo.setWeakQuestions(buildWeakQuestions(questions, questionAggs));
        vo.setKnowledgeStats(buildKnowledgeStats(questions, questionMap, questionAggs));
        return vo;
    }

    private void fillOverall(LearningStatisticsVO vo, List<ExamAnswer> answers,
                             BigDecimal passScore, BigDecimal excellentLine) {
        List<BigDecimal> scores = answers.stream()
                .map(ExamAnswer::getTeacherFinalScore)
                .filter(Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .toList();

        vo.setTotalCount(answers.size());
        vo.setMarkedCount(scores.size());
        if (scores.isEmpty()) {
            vo.setAvgScore(scale(BigDecimal.ZERO));
            vo.setMaxScore(scale(BigDecimal.ZERO));
            vo.setMinScore(scale(BigDecimal.ZERO));
            vo.setPassCount(0);
            vo.setPassRate(0d);
            vo.setExcellentCount(0);
            vo.setExcellentRate(0d);
            return;
        }

        BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        int passCount = (int) scores.stream().filter(score -> score.compareTo(passScore) >= 0).count();
        int excellentCount = (int) scores.stream().filter(score -> score.compareTo(excellentLine) >= 0).count();

        vo.setAvgScore(scale(sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP)));
        vo.setMaxScore(scale(scores.get(scores.size() - 1)));
        vo.setMinScore(scale(scores.get(0)));
        vo.setPassCount(passCount);
        vo.setPassRate(round4((double) passCount / scores.size()));
        vo.setExcellentCount(excellentCount);
        vo.setExcellentRate(round4((double) excellentCount / scores.size()));
    }

    /** 按 student_class 分组统计；未填写班级的归入"未分班"，用 TreeMap 保证输出顺序稳定 */
    private List<LearningStatisticsVO.ClassMetric> buildClassMetrics(List<ExamAnswer> answers,
                                                                    BigDecimal passScore,
                                                                    BigDecimal excellentLine) {
        Map<String, List<ExamAnswer>> grouped = answers.stream().collect(Collectors.groupingBy(
                answer -> StringUtils.hasText(answer.getStudentClass())
                        ? answer.getStudentClass().trim()
                        : "未分班",
                TreeMap::new,
                Collectors.toList()));

        List<LearningStatisticsVO.ClassMetric> metrics = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<ExamAnswer>> entry : grouped.entrySet()) {
            List<BigDecimal> scores = entry.getValue().stream()
                    .map(ExamAnswer::getTeacherFinalScore)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.naturalOrder())
                    .toList();

            LearningStatisticsVO.ClassMetric metric = new LearningStatisticsVO.ClassMetric();
            metric.setClassName(entry.getKey());
            metric.setStudentCount(entry.getValue().size());
            metric.setMarkedCount(scores.size());
            if (scores.isEmpty()) {
                metric.setPassCount(0);
                metric.setPassRate(0d);
                metric.setExcellentCount(0);
                metric.setExcellentRate(0d);
                metrics.add(metric);
                continue;
            }

            BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            int passCount = (int) scores.stream().filter(score -> score.compareTo(passScore) >= 0).count();
            int excellentCount = (int) scores.stream().filter(score -> score.compareTo(excellentLine) >= 0).count();
            metric.setAvgScore(scale(sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP)));
            metric.setMaxScore(scale(scores.get(scores.size() - 1)));
            metric.setMinScore(scale(scores.get(0)));
            metric.setPassCount(passCount);
            metric.setPassRate(round4((double) passCount / scores.size()));
            metric.setExcellentCount(excellentCount);
            metric.setExcellentRate(round4((double) excellentCount / scores.size()));
            metrics.add(metric);
        }
        return metrics;
    }

    /**
     * 聚合逐题得分。
     *
     * answer_content 在 AI 阅卷完成后会被写成 [{questionId, questionNo, score, comment}]；
     * 线上未阅卷的答卷是 [{questionId, answer}]，没有 score，自然被跳过。
     */
    private Map<Long, QuestionAgg> aggregateQuestions(List<ExamAnswer> answers,
                                                      Map<Long, ExamQuestion> questionMap) {
        Map<Long, QuestionAgg> aggs = new HashMap<>();
        for (ExamAnswer answer : answers) {
            for (JSONObject item : parseItemScores(answer.getAnswerContent())) {
                Long questionId;
                Double rawScore;
                try {
                    questionId = item.getLong("questionId");
                    rawScore = item.getDouble("score");
                } catch (Exception e) {
                    continue;
                }
                ExamQuestion question = questionMap.get(questionId);
                if (question == null || rawScore == null) {
                    continue;
                }
                BigDecimal score = BigDecimal.valueOf(rawScore).setScale(2, RoundingMode.HALF_UP);
                BigDecimal fullScore = question.getScore() == null ? BigDecimal.ZERO : question.getScore();

                QuestionAgg agg = aggs.computeIfAbsent(questionId, key -> new QuestionAgg());
                agg.answerCount++;
                agg.sum = agg.sum.add(score);
                if (fullScore.signum() > 0
                        && score.compareTo(fullScore.multiply(DEFAULT_PASS_RATE)) < 0) {
                    agg.wrongCount++;
                }
            }
        }
        return aggs;
    }

    /** 高频错题：保留得分率低于 60% 的题，按低分人数降序、得分率升序 */
    private List<LearningStatisticsVO.WeakQuestion> buildWeakQuestions(List<ExamQuestion> questions,
                                                                       Map<Long, QuestionAgg> aggs) {
        List<LearningStatisticsVO.WeakQuestion> weakQuestions = new ArrayList<>();
        for (ExamQuestion question : questions) {
            QuestionAgg agg = aggs.get(question.getId());
            if (agg == null || agg.answerCount == 0) {
                continue;
            }
            BigDecimal fullScore = question.getScore() == null ? BigDecimal.ZERO : question.getScore();
            if (fullScore.signum() <= 0) {
                continue;
            }
            BigDecimal avgScore = scale(agg.sum.divide(BigDecimal.valueOf(agg.answerCount), 2, RoundingMode.HALF_UP));
            double scoreRate = round4(avgScore.doubleValue() / fullScore.doubleValue());
            if (scoreRate >= WEAK_QUESTION_RATE) {
                continue;
            }

            LearningStatisticsVO.WeakQuestion weak = new LearningStatisticsVO.WeakQuestion();
            weak.setQuestionId(question.getId());
            weak.setQuestionNo(question.getQuestionNo());
            weak.setQuestionType(questionTypeName(question));
            weak.setFullScore(scale(fullScore));
            weak.setAnswerCount(agg.answerCount);
            weak.setAvgScore(avgScore);
            weak.setScoreRate(scoreRate);
            weak.setWrongCount(agg.wrongCount);
            weak.setWrongRate(round4((double) agg.wrongCount / agg.answerCount));
            weakQuestions.add(weak);
        }

        weakQuestions.sort(Comparator.comparing(LearningStatisticsVO.WeakQuestion::getWrongCount).reversed()
                .thenComparing(LearningStatisticsVO.WeakQuestion::getScoreRate));
        return weakQuestions;
    }

    /**
     * 知识点得分率：题目 -> 知识点桥表 -> 按知识点累计满分与得分。
     *
     * 一道题绑定多个知识点时，每个知识点都会获得该题的完整得分与满分，
     * 这样"知识点掌握度"是按知识点独立评估的，不做平均分摊（分摊反而失真）。
     */
    private List<LearningStatisticsVO.KnowledgeStat> buildKnowledgeStats(List<ExamQuestion> questions,
                                                                         Map<Long, ExamQuestion> questionMap,
                                                                         Map<Long, QuestionAgg> aggs) {
        List<Long> questionIds = questions.stream()
                .map(ExamQuestion::getId)
                .filter(Objects::nonNull)
                .toList();
        if (questionIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<QuestionKnowledgeRel> relations = questionKnowledgeRelMapper.selectList(
                Wrappers.<QuestionKnowledgeRel>lambdaQuery()
                        .in(QuestionKnowledgeRel::getQuestionId, questionIds));
        if (relations.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> knowledgeIds = relations.stream()
                .map(QuestionKnowledgeRel::getKnowledgeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (knowledgeIds.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> knowledgeNames = questionKnowledgeMapper.selectBatchIds(knowledgeIds).stream()
                .collect(Collectors.toMap(QuestionKnowledge::getId, QuestionKnowledge::getKnowledgeName, (a, b) -> a));

        Map<Long, KnowledgeAgg> knowledgeAggs = new LinkedHashMap<>();
        for (QuestionKnowledgeRel relation : relations) {
            if (relation.getKnowledgeId() == null || relation.getQuestionId() == null) {
                continue;
            }
            KnowledgeAgg agg = knowledgeAggs.computeIfAbsent(relation.getKnowledgeId(), key -> new KnowledgeAgg());
            if (!agg.questionIds.add(relation.getQuestionId())) {
                // 同一题重复绑定同一知识点，只算一次
                continue;
            }
            QuestionAgg questionAgg = aggs.get(relation.getQuestionId());
            ExamQuestion question = questionMap.get(relation.getQuestionId());
            if (questionAgg == null || questionAgg.answerCount == 0 || question == null) {
                continue;
            }
            BigDecimal fullScore = question.getScore() == null ? BigDecimal.ZERO : question.getScore();
            agg.answerCount += questionAgg.answerCount;
            agg.fullScore = agg.fullScore.add(fullScore.multiply(BigDecimal.valueOf(questionAgg.answerCount)));
            agg.earnedScore = agg.earnedScore.add(questionAgg.sum);
        }

        List<LearningStatisticsVO.KnowledgeStat> stats = new ArrayList<>();
        for (Map.Entry<Long, KnowledgeAgg> entry : knowledgeAggs.entrySet()) {
            KnowledgeAgg agg = entry.getValue();
            if (agg.answerCount == 0 || agg.fullScore.signum() <= 0) {
                continue;
            }
            LearningStatisticsVO.KnowledgeStat stat = new LearningStatisticsVO.KnowledgeStat();
            stat.setKnowledgeId(entry.getKey());
            stat.setKnowledgeName(knowledgeNames.getOrDefault(entry.getKey(), "未命名知识点"));
            stat.setQuestionCount(agg.questionIds.size());
            stat.setAnswerCount(agg.answerCount);
            stat.setFullScore(scale(agg.fullScore));
            stat.setAvgScore(scale(agg.earnedScore.divide(BigDecimal.valueOf(agg.answerCount), 2, RoundingMode.HALF_UP)));
            stat.setScoreRate(agg.earnedScore.divide(agg.fullScore, 4, RoundingMode.HALF_UP).doubleValue());
            stats.add(stat);
        }

        stats.sort(Comparator.comparing(LearningStatisticsVO.KnowledgeStat::getScoreRate));
        return stats;
    }

    // ==================== 生成讲评 ====================

    /**
     * 生成 AI 教学讲评建议（只生成正文，不生成 PPT）。
     *
     * 先插入"生成中"报告再调用大模型：
     * - 大模型耗时较长，先落库可以让前端立即拿到 reportId；
     * - 失败时保留 status=2 与 errorMsg，便于教师看到原因并重试；
     * - 不加 @Transactional，避免失败记录被一起回滚。
     *
     * @param dto 创建请求（paperId + 可选标题）
     * @return 讲评报告（含 Markdown 正文）
     */
    public LearningAnalysisReportVO generateAnalysis(LearningAnalysisCreateDTO dto) {
        Long creatorId = requireCurrentUserId();
        ExamPaper paper = requirePaper(dto.getPaperId());
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户

        LearningAnalysisReport report = new LearningAnalysisReport();
        report.setPaperId(paper.getId());
        report.setCourseId(paper.getCourseId());
        report.setReportType(REPORT_TYPE_PAPER);
        report.setReportTitle(resolveReportTitle(dto.getReportTitle(), paper));
        report.setStatus(STATUS_GENERATING);
        report.setCreatorId(creatorId);
        learningAnalysisReportMapper.insert(report);

        try {
            LearningStatisticsVO statistics = statistics(paper.getId());
            // BYOK：用触发本次讲评的用户自己的 Key（无登录上下文时回退到最近一次保存的 Key）
            String analysisText = deepSeekLlmUtil.chatAs(
                    CurrentUserUtil.usernameOrNull(), SYSTEM_PROMPT, buildAnalysisPrompt(paper, statistics));

            report.setAnalysisText(analysisText);
            report.setStatus(STATUS_SUCCESS);
            report.setErrorMsg(null);
            learningAnalysisReportMapper.updateById(report);

            LearningAnalysisReportVO vo = toDetailVO(report);
            vo.setPaperTitle(paper.getPaperTitle());
            vo.setStatistics(statistics);
            return vo;
        } catch (Exception e) {
            String reason = truncate(e.getMessage(), 900);
            report.setStatus(STATUS_FAILED);
            report.setErrorMsg(reason);
            learningAnalysisReportMapper.updateById(report);
            log.error("学情讲评生成失败: reportId={}, paperId={}, error={}",
                    report.getId(), paper.getId(), reason, e);
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("学情讲评生成失败：" + reason);
        }
    }

    /**
     * 基于已生成的讲评正文生成 PPT 课件，并返回短时效下载地址。
     *
     * 与"生成讲评"拆成两个接口的原因：
     * - 大模型调用与 PPT 生成耗时不同，拆开后前端可以先展示文字，再按需生成课件；
     * - PPT 生成失败不影响已生成的讲评正文，重新调用本接口即可重试；
     * - 每次重新生成都会上传新对象，旧对象保留在 MinIO 中（按日期归档，便于回溯）。
     */
    public PptDownloadVO generatePpt(Long reportId) {
        LearningAnalysisReport report = requireReport(reportId);
        // 数据权限（占位）：校验报告所属课程的任课教师是否为当前用户

        if (!StringUtils.hasText(report.getAnalysisText())) {
            throw new BusinessException("该报告还没有讲评正文，请先生成 AI 教学讲评建议");
        }
        if (report.getPaperId() == null) {
            throw new BusinessException("报告未关联试卷，无法生成 PPT");
        }

        ExamPaper paper = requirePaper(report.getPaperId());
        LearningStatisticsVO statistics = statistics(paper.getId());
        try {
            String objectKey = pptUtil.generateAndUpload(statistics, report.getAnalysisText(), report.getReportTitle());
            report.setPptFileUrl(objectKey);
            report.setStatus(STATUS_SUCCESS);
            report.setErrorMsg(null);
            learningAnalysisReportMapper.updateById(report);

            PptDownloadVO vo = new PptDownloadVO();
            vo.setReportId(report.getId());
            vo.setReportTitle(report.getReportTitle());
            vo.setFileName(safeFileName(report.getReportTitle()) + "-讲评课件.pptx");
            vo.setPptFileUrl(objectKey);
            vo.setDownloadUrl(minioUtil.getPresignedUrl(objectKey));
            return vo;
        } catch (Exception e) {
            log.error("生成讲评PPT失败: reportId={}, paperId={}", reportId, paper.getId(), e);
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("生成讲评PPT失败：" + e.getMessage());
        }
    }

    /**
     * 查询报告详情，并把 PPT objectKey 换成短时效签名地址。
     */
    public LearningAnalysisReportVO getReport(Long reportId) {
        LearningAnalysisReport report = learningAnalysisReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException("学情分析报告不存在: " + reportId);
        }

        LearningAnalysisReportVO vo = toDetailVO(report);
        if (report.getPaperId() != null) {
            ExamPaper paper = examPaperMapper.selectById(report.getPaperId());
            if (paper != null) {
                vo.setPaperTitle(paper.getPaperTitle());
                vo.setStatistics(statistics(paper.getId()));
            }
        }
        return vo;
    }

    // ==================== 学生个人学情卡片 ====================

    /**
     * 查询学生个人学情卡片。
     *
     * 处理要点：
     * - 同一学生同一试卷可能多次作答，取 id 最大（最新）的一条；
     * - 排名只在已出分答卷之间计算，未出分返回 null；
     * - 知识点只统计该学生有逐题得分明细的题目，避免教师只录总分时把未作答题目算成 0 分。
     *
     * 数据权限（占位）：教师只能查看自己班级学生；学生只能查看本人。
     */
    public StudentLearningCardVO studentCard(Long paperId, Long studentId) {
        ExamPaper paper = requirePaper(paperId);
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户，
        // 学生角色还需校验 studentId 是否等于当前登录用户

        List<ExamAnswer> paperAnswers = examAnswerMapper.selectList(Wrappers.<ExamAnswer>lambdaQuery()
                .eq(ExamAnswer::getPaperId, paperId)
                .orderByDesc(ExamAnswer::getId));
        ExamAnswer answer = paperAnswers.stream()
                .filter(item -> Objects.equals(item.getStudentId(), studentId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("该学生暂无本试卷的答卷"));

        BigDecimal totalScore = paper.getTotalScore() == null
                ? new BigDecimal("100.00")
                : scale(paper.getTotalScore());
        BigDecimal passScore = paper.getPassScore() == null
                ? scale(totalScore.multiply(DEFAULT_PASS_RATE))
                : scale(paper.getPassScore());
        BigDecimal finalScore = answer.getTeacherFinalScore();

        LearningStatisticsVO statistics = statistics(paperId);
        String className = normalizeClassName(answer.getStudentClass());
        LearningStatisticsVO.ClassMetric classMetric = statistics.getClassMetrics() == null
                ? null
                : statistics.getClassMetrics().stream()
                .filter(metric -> className.equals(metric.getClassName()))
                .findFirst()
                .orElse(null);

        // 同一学生多次作答只保留最新一条，避免排名被重复答卷影响
        Map<String, ExamAnswer> latestByStudent = new LinkedHashMap<>();
        for (ExamAnswer item : paperAnswers) {
            latestByStudent.putIfAbsent(studentKey(item), item);
        }
        List<ExamAnswer> scoredAnswers = latestByStudent.values().stream()
                .filter(item -> item.getTeacherFinalScore() != null)
                .toList();
        List<ExamAnswer> classScoredAnswers = scoredAnswers.stream()
                .filter(item -> className.equals(normalizeClassName(item.getStudentClass())))
                .toList();

        StudentLearningCardVO vo = new StudentLearningCardVO();
        vo.setPaperId(paper.getId());
        vo.setPaperTitle(paper.getPaperTitle());
        vo.setTotalScore(totalScore);
        vo.setPassScore(passScore);
        vo.setAnswerId(answer.getId());
        vo.setStudentId(answer.getStudentId());
        vo.setStudentNo(answer.getStudentNo());
        vo.setStudentName(answer.getStudentName());
        vo.setStudentClass(answer.getStudentClass());
        vo.setAnswerSource(answer.getAnswerSource());
        AnswerSourceEnum source = AnswerSourceEnum.of(answer.getAnswerSource());
        vo.setAnswerSourceDesc(source == null ? "" : source.getDesc());
        vo.setMarkingStatus(answer.getMarkingStatus());
        vo.setMarkingStatusDesc(AnswerStatusEnum.descOf(answer.getMarkingStatus()));
        vo.setAiReferenceScore(answer.getAiReferenceScore());
        vo.setTeacherFinalScore(finalScore);
        vo.setPassed(finalScore != null && finalScore.compareTo(passScore) >= 0);
        vo.setMarkTime(answer.getMarkTime());

        if (classMetric != null) {
            vo.setClassAvgScore(classMetric.getAvgScore());
            vo.setClassPassRate(classMetric.getPassRate());
        }
        if (finalScore != null) {
            if (classMetric != null && classMetric.getAvgScore() != null) {
                vo.setScoreDiffToClassAvg(scale(finalScore.subtract(classMetric.getAvgScore())));
            }
            vo.setPaperRank(rankOf(scoredAnswers, finalScore));
            vo.setPaperSize(scoredAnswers.size());
            vo.setClassRank(rankOf(classScoredAnswers, finalScore));
            vo.setClassSize(classScoredAnswers.size());
        }

        List<ExamQuestion> questions = examQuestionMapper.selectList(Wrappers.<ExamQuestion>lambdaQuery()
                .eq(ExamQuestion::getPaperId, paperId)
                .orderByAsc(ExamQuestion::getSortOrder));
        Map<Long, ExamQuestion> questionMap = questions.stream()
                .filter(question -> question.getId() != null)
                .collect(Collectors.toMap(ExamQuestion::getId, question -> question, (a, b) -> a));
        Map<Long, StudentItemScore> itemScores = parseStudentItemScores(answer.getAnswerContent());

        List<StudentLearningCardVO.QuestionItem> weakQuestions = buildStudentWeakQuestions(questions, itemScores);
        List<StudentLearningCardVO.KnowledgeItem> knowledgeStats =
                buildStudentKnowledgeStats(questions, questionMap, itemScores);
        vo.setWeakQuestions(weakQuestions);
        vo.setKnowledgeStats(knowledgeStats);
        vo.setSuggestion(buildStudentSuggestion(answer, itemScores, weakQuestions, knowledgeStats));
        return vo;
    }

    /** 分页查询报告列表 */
    public PageResult<LearningAnalysisReportListVO> pageReports(LearningAnalysisQueryDTO query) {
        LambdaQueryWrapper<LearningAnalysisReport> wrapper = Wrappers.lambdaQuery();
        if (query.getPaperId() != null) {
            wrapper.eq(LearningAnalysisReport::getPaperId, query.getPaperId());
        }
        if (StringUtils.hasText(query.getReportType())) {
            wrapper.eq(LearningAnalysisReport::getReportType, query.getReportType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(LearningAnalysisReport::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(LearningAnalysisReport::getCreateTime);

        Page<LearningAnalysisReport> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<LearningAnalysisReport> result = learningAnalysisReportMapper.selectPage(page, wrapper);

        Set<Long> paperIds = result.getRecords().stream()
                .map(LearningAnalysisReport::getPaperId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> paperTitles = paperIds.isEmpty()
                ? Map.of()
                : examPaperMapper.selectBatchIds(paperIds).stream()
                .collect(Collectors.toMap(ExamPaper::getId, ExamPaper::getPaperTitle, (a, b) -> a));

        List<LearningAnalysisReportListVO> records = result.getRecords().stream()
                .map(report -> toListVO(report, paperTitles.get(report.getPaperId())))
                .toList();
        return PageResult.of(result, records);
    }

    // ==================== Prompt ====================

    /**
     * 组装大模型 Prompt。
     *
     * 设计原则：
     * - 只给"结构化事实"，不给结论，让大模型基于数据推导；
     * - 数字统一保留两位小数/一位百分比，避免模型抄错；
     * - 明确章节结构，PPT 与前端正文都按这个结构展示。
     */
    private String buildAnalysisPrompt(ExamPaper paper, LearningStatisticsVO statistics) {
        StringBuilder prompt = new StringBuilder(2048);
        prompt.append("请根据以下考试数据撰写学情分析报告。\n\n");

        prompt.append("【试卷信息】\n");
        prompt.append("试卷：").append(paper.getPaperTitle()).append("\n");
        prompt.append("总分：").append(scoreText(statistics.getTotalScore()))
                .append("，及格线：").append(scoreText(statistics.getPassScore())).append("\n\n");

        prompt.append("【整体指标】\n");
        prompt.append("答卷总数：").append(nullToZero(statistics.getTotalCount()))
                .append("，已出分：").append(nullToZero(statistics.getMarkedCount())).append("\n");
        prompt.append("平均分：").append(scoreText(statistics.getAvgScore()))
                .append("，最高分：").append(scoreText(statistics.getMaxScore()))
                .append("，最低分：").append(scoreText(statistics.getMinScore())).append("\n");
        prompt.append("及格人数：").append(nullToZero(statistics.getPassCount()))
                .append("（").append(percentText(statistics.getPassRate())).append("）")
                .append("，优秀人数：").append(nullToZero(statistics.getExcellentCount()))
                .append("（").append(percentText(statistics.getExcellentRate())).append("）\n\n");

        prompt.append("【班级指标】\n");
        if (statistics.getClassMetrics() == null || statistics.getClassMetrics().isEmpty()) {
            prompt.append("无班级信息。\n");
        } else {
            for (LearningStatisticsVO.ClassMetric metric : statistics.getClassMetrics()) {
                prompt.append("- ").append(metric.getClassName())
                        .append("：答卷 ").append(nullToZero(metric.getStudentCount()))
                        .append(" 份，已出分 ").append(nullToZero(metric.getMarkedCount()))
                        .append(" 份，平均分 ").append(scoreText(metric.getAvgScore()))
                        .append("，及格率 ").append(percentText(metric.getPassRate()))
                        .append("，优秀率 ").append(percentText(metric.getExcellentRate()))
                        .append("\n");
            }
        }
        prompt.append("\n");

        prompt.append("【高频错题（得分率低于 60%）】\n");
        if (statistics.getWeakQuestions() == null || statistics.getWeakQuestions().isEmpty()) {
            prompt.append("本次考试没有得分率低于 60% 的题目。\n");
        } else {
            for (LearningStatisticsVO.WeakQuestion weak : statistics.getWeakQuestions()) {
                prompt.append("- 第 ").append(weak.getQuestionNo()).append(" 题（")
                        .append(weak.getQuestionType()).append("，满分 ").append(scoreText(weak.getFullScore()))
                        .append("）：平均得分 ").append(scoreText(weak.getAvgScore()))
                        .append("，得分率 ").append(percentText(weak.getScoreRate()))
                        .append("，低分人数 ").append(nullToZero(weak.getWrongCount()))
                        .append("/").append(nullToZero(weak.getAnswerCount()))
                        .append("（").append(percentText(weak.getWrongRate())).append("）\n");
            }
        }
        prompt.append("\n");

        prompt.append("【知识点得分率（由低到高）】\n");
        if (statistics.getKnowledgeStats() == null || statistics.getKnowledgeStats().isEmpty()) {
            prompt.append("本次考试未绑定知识点。\n");
        } else {
            int limit = Math.min(statistics.getKnowledgeStats().size(), MAX_PROMPT_ITEMS);
            for (int i = 0; i < limit; i++) {
                LearningStatisticsVO.KnowledgeStat knowledge = statistics.getKnowledgeStats().get(i);
                prompt.append("- ").append(knowledge.getKnowledgeName())
                        .append("：关联 ").append(nullToZero(knowledge.getQuestionCount()))
                        .append(" 道题，作答 ").append(nullToZero(knowledge.getAnswerCount()))
                        .append(" 人次，平均得分 ").append(scoreText(knowledge.getAvgScore()))
                        .append("，得分率 ").append(percentText(knowledge.getScoreRate()))
                        .append("\n");
            }
        }
        prompt.append("\n");

        prompt.append("【输出要求】\n");
        prompt.append("1. 五个章节缺一不可，使用 Markdown 标题；\n");
        prompt.append("2. 高频错题讲评要逐题说明考查点、典型错误表现、课堂讲评要点；\n");
        prompt.append("3. 教学改进建议要区分“课堂重讲”“练习强化”“个别辅导”，并给出可执行的作业或练习方向；\n");
        prompt.append("4. 不要重复罗列所有数字，重点是数据背后的教学含义；\n");
        prompt.append("5. 全文控制在 1200~2000 字。");
        return prompt.toString();
    }

    // ==================== 转换与工具 ====================

    private LearningAnalysisReportVO toDetailVO(LearningAnalysisReport report) {
        LearningAnalysisReportVO vo = new LearningAnalysisReportVO();
        vo.setId(report.getId());
        vo.setPaperId(report.getPaperId());
        vo.setCourseId(report.getCourseId());
        vo.setReportType(report.getReportType());
        vo.setReportTitle(report.getReportTitle());
        vo.setAnalysisText(report.getAnalysisText());
        vo.setPptFileUrl(report.getPptFileUrl());
        vo.setStatus(report.getStatus());
        vo.setStatusDesc(statusDesc(report.getStatus()));
        vo.setErrorMsg(report.getErrorMsg());
        vo.setCreatorId(report.getCreatorId());
        vo.setCreateTime(report.getCreateTime());
        vo.setUpdateTime(report.getUpdateTime());

        if (StringUtils.hasText(report.getPptFileUrl())) {
            try {
                vo.setPptAccessUrl(minioUtil.getPresignedUrl(report.getPptFileUrl()));
            } catch (Exception e) {
                // 签名失败不影响正文展示，前端可以稍后重试详情接口
                log.warn("获取PPT签名地址失败: objectKey={}, error={}", report.getPptFileUrl(), e.getMessage());
            }
        }
        return vo;
    }

    private LearningAnalysisReportListVO toListVO(LearningAnalysisReport report, String paperTitle) {
        LearningAnalysisReportListVO vo = new LearningAnalysisReportListVO();
        vo.setId(report.getId());
        vo.setPaperId(report.getPaperId());
        vo.setPaperTitle(paperTitle);
        vo.setCourseId(report.getCourseId());
        vo.setReportType(report.getReportType());
        vo.setReportTitle(report.getReportTitle());
        vo.setStatus(report.getStatus());
        vo.setStatusDesc(statusDesc(report.getStatus()));
        vo.setErrorMsg(report.getErrorMsg());
        vo.setHasPpt(StringUtils.hasText(report.getPptFileUrl()));
        vo.setCreatorId(report.getCreatorId());
        vo.setCreateTime(report.getCreateTime());
        return vo;
    }

    private String statusDesc(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case STATUS_GENERATING -> "生成中";
            case STATUS_SUCCESS -> "生成成功";
            case STATUS_FAILED -> "生成失败";
            default -> "未知状态";
        };
    }

    private String resolveReportTitle(String reportTitle, ExamPaper paper) {
        if (StringUtils.hasText(reportTitle)) {
            return reportTitle.trim();
        }
        String paperTitle = StringUtils.hasText(paper.getPaperTitle()) ? paper.getPaperTitle() : "试卷" + paper.getId();
        return paperTitle + "-学情分析";
    }

    private ExamPaper requirePaper(Long paperId) {
        ExamPaper paper = examPaperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException("试卷不存在: " + paperId);
        }
        return paper;
    }

    private LearningAnalysisReport requireReport(Long reportId) {
        LearningAnalysisReport report = learningAnalysisReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException("学情分析报告不存在: " + reportId);
        }
        return report;
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

    private String normalizeClassName(String className) {
        return StringUtils.hasText(className) ? className.trim() : "未分班";
    }

    /** 唯一标识一个学生：优先 studentId，其次学号，最后退化为答卷ID */
    private String studentKey(ExamAnswer answer) {
        if (answer.getStudentId() != null) {
            return "id:" + answer.getStudentId();
        }
        if (StringUtils.hasText(answer.getStudentNo())) {
            return "no:" + answer.getStudentNo();
        }
        return "answer:" + answer.getId();
    }

    /** 排名 = 1 + 分数严格高于当前分的人数（并列同名次） */
    private int rankOf(List<ExamAnswer> answers, BigDecimal score) {
        int rank = 1;
        for (ExamAnswer item : answers) {
            if (item.getTeacherFinalScore() != null && item.getTeacherFinalScore().compareTo(score) > 0) {
                rank++;
            }
        }
        return rank;
    }

    /** 解析单个学生答卷的逐题得分（questionId -> 得分 + 评语） */
    private Map<Long, StudentItemScore> parseStudentItemScores(String answerContent) {
        Map<Long, StudentItemScore> itemScores = new HashMap<>();
        for (JSONObject item : parseItemScores(answerContent)) {
            try {
                Long questionId = item.getLong("questionId");
                Double rawScore = item.getDouble("score");
                if (questionId == null || rawScore == null) {
                    continue;
                }
                StudentItemScore itemScore = new StudentItemScore();
                itemScore.score = BigDecimal.valueOf(rawScore).setScale(2, RoundingMode.HALF_UP);
                itemScore.comment = item.getStr("comment");
                itemScores.put(questionId, itemScore);
            } catch (Exception e) {
                log.warn("解析学生逐题得分失败，已跳过: {}", e.getMessage());
            }
        }
        return itemScores;
    }

    /** 学生薄弱题：得分率低于 60%，按得分率升序，最多 10 道 */
    private List<StudentLearningCardVO.QuestionItem> buildStudentWeakQuestions(
            List<ExamQuestion> questions, Map<Long, StudentItemScore> itemScores) {
        List<StudentLearningCardVO.QuestionItem> weakQuestions = new ArrayList<>();
        for (ExamQuestion question : questions) {
            StudentItemScore itemScore = itemScores.get(question.getId());
            if (itemScore == null) {
                continue;
            }
            BigDecimal fullScore = question.getScore() == null ? BigDecimal.ZERO : question.getScore();
            if (fullScore.signum() <= 0) {
                continue;
            }
            double scoreRate = round4(itemScore.score.doubleValue() / fullScore.doubleValue());
            if (scoreRate >= WEAK_QUESTION_RATE) {
                continue;
            }

            StudentLearningCardVO.QuestionItem item = new StudentLearningCardVO.QuestionItem();
            item.setQuestionId(question.getId());
            item.setQuestionNo(question.getQuestionNo());
            item.setQuestionType(questionTypeName(question));
            item.setFullScore(scale(fullScore));
            item.setScore(scale(itemScore.score));
            item.setScoreRate(scoreRate);
            item.setComment(itemScore.comment);
            weakQuestions.add(item);
        }

        weakQuestions.sort(Comparator.comparing(StudentLearningCardVO.QuestionItem::getScoreRate));
        return weakQuestions.stream().limit(MAX_PROMPT_ITEMS).toList();
    }

    /**
     * 学生知识点掌握情况。
     *
     * 只统计该学生有逐题得分明细的题目：
     * 若教师只录了总分而没有逐题明细，宁可不展示知识点，也不把缺失题目误算成 0 分。
     */
    private List<StudentLearningCardVO.KnowledgeItem> buildStudentKnowledgeStats(
            List<ExamQuestion> questions,
            Map<Long, ExamQuestion> questionMap,
            Map<Long, StudentItemScore> itemScores) {
        List<Long> questionIds = questions.stream()
                .map(ExamQuestion::getId)
                .filter(Objects::nonNull)
                .toList();
        if (questionIds.isEmpty() || itemScores.isEmpty()) {
            return new ArrayList<>();
        }

        List<QuestionKnowledgeRel> relations = questionKnowledgeRelMapper.selectList(
                Wrappers.<QuestionKnowledgeRel>lambdaQuery()
                        .in(QuestionKnowledgeRel::getQuestionId, questionIds));
        if (relations.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> knowledgeIds = relations.stream()
                .map(QuestionKnowledgeRel::getKnowledgeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (knowledgeIds.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> knowledgeNames = questionKnowledgeMapper.selectBatchIds(knowledgeIds).stream()
                .collect(Collectors.toMap(QuestionKnowledge::getId, QuestionKnowledge::getKnowledgeName, (a, b) -> a));

        Map<Long, StudentKnowledgeAgg> aggs = new LinkedHashMap<>();
        for (QuestionKnowledgeRel relation : relations) {
            if (relation.getKnowledgeId() == null || relation.getQuestionId() == null) {
                continue;
            }
            StudentItemScore itemScore = itemScores.get(relation.getQuestionId());
            ExamQuestion question = questionMap.get(relation.getQuestionId());
            if (itemScore == null || question == null || question.getScore() == null) {
                continue;
            }
            StudentKnowledgeAgg agg = aggs.computeIfAbsent(relation.getKnowledgeId(), key -> new StudentKnowledgeAgg());
            if (!agg.questionIds.add(relation.getQuestionId())) {
                continue;
            }
            agg.fullScore = agg.fullScore.add(question.getScore());
            agg.earnedScore = agg.earnedScore.add(itemScore.score);
        }

        List<StudentLearningCardVO.KnowledgeItem> stats = new ArrayList<>();
        for (Map.Entry<Long, StudentKnowledgeAgg> entry : aggs.entrySet()) {
            StudentKnowledgeAgg agg = entry.getValue();
            if (agg.fullScore.signum() <= 0) {
                continue;
            }
            double scoreRate = agg.earnedScore.divide(agg.fullScore, 4, RoundingMode.HALF_UP).doubleValue();
            StudentLearningCardVO.KnowledgeItem item = new StudentLearningCardVO.KnowledgeItem();
            item.setKnowledgeId(entry.getKey());
            item.setKnowledgeName(knowledgeNames.getOrDefault(entry.getKey(), "未命名知识点"));
            item.setQuestionCount(agg.questionIds.size());
            item.setFullScore(scale(agg.fullScore));
            item.setEarnedScore(scale(agg.earnedScore));
            item.setScoreRate(scoreRate);
            item.setWeak(scoreRate < WEAK_QUESTION_RATE);
            stats.add(item);
        }

        stats.sort(Comparator.comparing(StudentLearningCardVO.KnowledgeItem::getScoreRate));
        return stats;
    }

    /** 规则化学习建议：不调用大模型，保证接口稳定、可解释 */
    private String buildStudentSuggestion(ExamAnswer answer,
                                          Map<Long, StudentItemScore> itemScores,
                                          List<StudentLearningCardVO.QuestionItem> weakQuestions,
                                          List<StudentLearningCardVO.KnowledgeItem> knowledgeStats) {
        if (answer.getTeacherFinalScore() == null) {
            return "本试卷尚未出分，出分后即可查看个人学情分析。";
        }
        if (itemScores.isEmpty()) {
            return "本试卷暂无逐题得分明细，暂时无法生成知识点级建议，请联系教师完成逐题批改。";
        }

        List<StudentLearningCardVO.KnowledgeItem> weakKnowledges = knowledgeStats.stream()
                .filter(item -> Boolean.TRUE.equals(item.getWeak()))
                .limit(3)
                .toList();
        if (!weakKnowledges.isEmpty()) {
            StringBuilder suggestion = new StringBuilder("本次考试需重点巩固：");
            for (int i = 0; i < weakKnowledges.size(); i++) {
                StudentLearningCardVO.KnowledgeItem item = weakKnowledges.get(i);
                if (i > 0) {
                    suggestion.append("、");
                }
                suggestion.append(item.getKnowledgeName())
                        .append("（得分率 ").append(percentText(item.getScoreRate())).append("）");
            }
            suggestion.append("。建议先回顾课堂例题与错题，再完成对应知识点的针对性练习。");
            return suggestion.toString();
        }

        if (!weakQuestions.isEmpty()) {
            return "本次考试有 " + weakQuestions.size()
                    + " 道题得分率偏低，建议结合题目评语逐题复盘解题步骤，避免同类失分。";
        }
        return "各知识点掌握较为均衡，建议保持当前学习节奏，并适当挑战综合性题目。";
    }

    /** 去掉文件名中的非法字符，供 Content-Disposition / 前端保存使用 */
    private String safeFileName(String title) {
        String value = StringUtils.hasText(title) ? title.trim() : "学情分析";
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /** 解析 answer_content 中的逐题明细；非 JSON 数组（旧数据/空值）直接忽略 */
    private List<JSONObject> parseItemScores(String answerContent) {
        List<JSONObject> items = new ArrayList<>();
        if (!StringUtils.hasText(answerContent)) {
            return items;
        }
        String trimmed = answerContent.trim();
        if (!trimmed.startsWith("[")) {
            return items;
        }
        try {
            JSONArray array = JSONUtil.parseArray(trimmed);
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getJSONObject(i);
                if (item != null) {
                    items.add(item);
                }
            }
        } catch (Exception e) {
            log.warn("解析答卷逐题明细失败，已跳过: {}", e.getMessage());
        }
        return items;
    }

    /** 题型数值转展示名称（与 exam_question.question_type 约定一致） */
    private String questionTypeName(ExamQuestion question) {
        if (StringUtils.hasText(question.getSectionName())) {
            return question.getSectionName();
        }
        if (question.getQuestionType() == null) {
            return "未知题型";
        }
        return switch (question.getQuestionType()) {
            case 1 -> "单选题";
            case 2 -> "多选题";
            case 3 -> "判断题";
            case 4 -> "填空题";
            case 5 -> "简答题";
            case 6 -> "论述题";
            case 7 -> "计算题";
            case 8 -> "案例题";
            default -> "其他题型";
        };
    }

    private String scoreText(BigDecimal value) {
        return value == null ? "—" : scale(value).toPlainString();
    }

    private String percentText(Double rate) {
        return rate == null ? "—" : String.format(Locale.ROOT, "%.1f%%", rate * 100);
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private double round4(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    /** 逐题聚合中间对象 */
    private static class QuestionAgg {
        private int answerCount;
        private BigDecimal sum = BigDecimal.ZERO;
        private int wrongCount;
    }

    /** 知识点聚合中间对象 */
    private static class KnowledgeAgg {
        private final Set<Long> questionIds = new HashSet<>();
        private int answerCount;
        private BigDecimal fullScore = BigDecimal.ZERO;
        private BigDecimal earnedScore = BigDecimal.ZERO;
    }

    /** 学生单题得分明细 */
    private static class StudentItemScore {
        private BigDecimal score;
        private String comment;
    }

    /** 学生知识点聚合中间对象 */
    private static class StudentKnowledgeAgg {
        private final Set<Long> questionIds = new HashSet<>();
        private BigDecimal fullScore = BigDecimal.ZERO;
        private BigDecimal earnedScore = BigDecimal.ZERO;
    }
}
