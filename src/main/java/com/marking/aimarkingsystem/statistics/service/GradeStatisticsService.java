package com.marking.aimarkingsystem.statistics.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.answer.entity.ExamAnswer;
import com.marking.aimarkingsystem.answer.enums.AnswerSourceEnum;
import com.marking.aimarkingsystem.answer.enums.AnswerStatusEnum;
import com.marking.aimarkingsystem.answer.mapper.ExamAnswerMapper;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.paper.entity.ExamPaper;
import com.marking.aimarkingsystem.paper.mapper.ExamPaperMapper;
import com.marking.aimarkingsystem.statistics.excel.GradeExportRow;
import com.marking.aimarkingsystem.statistics.vo.GradeStatisticsVO;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 成绩统计与导出服务。
 *
 * 统计口径：
 * - 只有 teacherFinalScore 非空的答卷参与均分/最值/及格率/分数段，未出分的计入总数但不进分母；
 * - 及格线取试卷 pass_score，为空时按总分的 60% 兜底；
 * - 优秀线按总分的 90% 计算；
 * - 分数段按得分率划分：0-59 / 60-69 / 70-79 / 80-89 / 90-100（单位：百分比）。
 *
 * 不新建数据表：所有指标由 exam_answer 实时聚合；单课程百人量级一次查询即可完成。
 * 数据权限（占位）：教师只能统计自己所授课程下试卷的成绩。
 */
@Service
public class GradeStatisticsService {

    private static final Logger log = LoggerFactory.getLogger(GradeStatisticsService.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 分数段上限（得分率百分比），最后一段为 100 以上 */
    private static final double[] BUCKET_UPPER_BOUNDS = {60d, 70d, 80d, 90d, 100d};
    private static final String[] BUCKET_LABELS = {"0-59", "60-69", "70-79", "80-89", "90-100"};

    private final ExamAnswerMapper examAnswerMapper;
    private final ExamPaperMapper examPaperMapper;

    public GradeStatisticsService(ExamAnswerMapper examAnswerMapper,
                                  ExamPaperMapper examPaperMapper) {
        this.examAnswerMapper = examAnswerMapper;
        this.examPaperMapper = examPaperMapper;
    }

    /**
     * 试卷成绩统计：最高分、最低分、平均分、中位数、及格率、优秀率与分数段分布。
     */
    public GradeStatisticsVO statistics(Long paperId) {
        ExamPaper paper = requirePaper(paperId);
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户
        List<ExamAnswer> answers = loadAnswers(paperId);

        List<BigDecimal> scores = answers.stream()
                .map(ExamAnswer::getTeacherFinalScore)
                .filter(score -> score != null)
                .sorted(Comparator.naturalOrder())
                .toList();

        BigDecimal totalScore = paper.getTotalScore() == null
                ? new BigDecimal("100.00")
                : scale(paper.getTotalScore());
        BigDecimal passScore = paper.getPassScore() == null
                ? scale(totalScore.multiply(new BigDecimal("0.6")))
                : scale(paper.getPassScore());
        BigDecimal excellentLine = scale(totalScore.multiply(new BigDecimal("0.9")));

        GradeStatisticsVO vo = new GradeStatisticsVO();
        vo.setPaperId(paper.getId());
        vo.setPaperTitle(paper.getPaperTitle());
        vo.setTotalScore(totalScore);
        vo.setPassScore(passScore);
        vo.setTotalCount(answers.size());
        vo.setMarkedCount(scores.size());
        vo.setUnmarkedCount(answers.size() - scores.size());
        vo.setScoreDistribution(buildDistribution(scores, totalScore));

        if (scores.isEmpty()) {
            vo.setPassCount(0);
            vo.setPassRate(0d);
            vo.setExcellentCount(0);
            vo.setExcellentRate(0d);
            return vo;
        }

        BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        int passCount = (int) scores.stream().filter(score -> score.compareTo(passScore) >= 0).count();
        int excellentCount = (int) scores.stream().filter(score -> score.compareTo(excellentLine) >= 0).count();

        vo.setAvgScore(scale(sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP)));
        vo.setMaxScore(scale(scores.get(scores.size() - 1)));
        vo.setMinScore(scale(scores.get(0)));
        vo.setMedianScore(median(scores));
        vo.setPassCount(passCount);
        vo.setPassRate(round4((double) passCount / scores.size()));
        vo.setExcellentCount(excellentCount);
        vo.setExcellentRate(round4((double) excellentCount / scores.size()));
        return vo;
    }

    /**
     * 导出原始成绩 Excel：线上与纸质答卷统一列，未出分留空。
     *
     * 直接写 HttpServletResponse 流，不再包一层 Result（文件流无法与 JSON 混用）。
     */
    public void exportExcel(Long paperId, HttpServletResponse response) {
        ExamPaper paper = requirePaper(paperId);
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户
        List<ExamAnswer> answers = loadAnswers(paperId);
        List<GradeExportRow> rows = buildRows(answers, paper);

        try {
            String fileName = URLEncoder.encode(paper.getPaperTitle() + "-成绩单",
                            StandardCharsets.UTF_8)
                    .replace("+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
            EasyExcel.write(response.getOutputStream(), GradeExportRow.class)
                    .sheet("成绩单")
                    .doWrite(rows);
            log.info("成绩单导出完成: paperId={}, rows={}", paperId, rows.size());
        } catch (IOException e) {
            throw new BusinessException("成绩单导出失败: " + e.getMessage());
        }
    }

    // ==================== 内部实现 ====================

    private List<GradeExportRow> buildRows(List<ExamAnswer> answers, ExamPaper paper) {
        BigDecimal totalScore = paper.getTotalScore() == null
                ? new BigDecimal("100.00")
                : scale(paper.getTotalScore());
        BigDecimal passScore = paper.getPassScore() == null
                ? scale(totalScore.multiply(new BigDecimal("0.6")))
                : scale(paper.getPassScore());

        List<GradeExportRow> rows = new ArrayList<>(answers.size());
        int index = 1;
        for (ExamAnswer answer : answers) {
            GradeExportRow row = new GradeExportRow();
            row.setIndex(index++);
            row.setStudentNo(answer.getStudentNo());
            row.setStudentName(answer.getStudentName());
            row.setStudentClass(answer.getStudentClass());
            row.setAnswerSourceDesc(AnswerSourceEnum.isPaperScan(answer.getAnswerSource()) ? "纸质扫描" : "线上作答");
            row.setAiReferenceScore(nullToEmpty(answer.getAiReferenceScore()));
            row.setTeacherFinalScore(nullToEmpty(answer.getTeacherFinalScore()));
            row.setPassFlag(passFlag(answer.getTeacherFinalScore(), passScore));
            row.setReviewStatusDesc(reviewStatusDesc(answer));
            row.setMarkTime(answer.getMarkTime() == null ? "" : answer.getMarkTime().format(TIME_FORMATTER));
            rows.add(row);
        }
        return rows;
    }

    private String passFlag(BigDecimal finalScore, BigDecimal passScore) {
        if (finalScore == null) {
            return "未出分";
        }
        return finalScore.compareTo(passScore) >= 0 ? "是" : "否";
    }

    /** 复核状态由归档状态 + 是否改分推导（当前表结构无独立复核状态列） */
    private String reviewStatusDesc(ExamAnswer answer) {
        if (AnswerStatusEnum.isArchived(answer.getMarkingStatus())) {
            boolean modified = answer.getAiReferenceScore() != null
                    && answer.getTeacherFinalScore() != null
                    && scale(answer.getAiReferenceScore()).compareTo(scale(answer.getTeacherFinalScore())) != 0;
            return modified ? "已复核改分" : "复核通过";
        }
        if (AnswerStatusEnum.PENDING_TEACHER_CONFIRM.getCode().equals(answer.getMarkingStatus())) {
            return "待复核";
        }
        return AnswerStatusEnum.descOf(answer.getMarkingStatus());
    }

    private List<GradeStatisticsVO.ScoreBucket> buildDistribution(List<BigDecimal> scores, BigDecimal totalScore) {
        int[] counts = new int[BUCKET_UPPER_BOUNDS.length];
        for (BigDecimal score : scores) {
            double rate = score.doubleValue() * 100d / totalScore.doubleValue();
            for (int i = 0; i < BUCKET_UPPER_BOUNDS.length; i++) {
                boolean last = i == BUCKET_UPPER_BOUNDS.length - 1;
                if (rate < BUCKET_UPPER_BOUNDS[i] || (last && rate >= BUCKET_UPPER_BOUNDS[i])) {
                    counts[i]++;
                    break;
                }
            }
        }

        int total = scores.size();
        List<GradeStatisticsVO.ScoreBucket> buckets = new ArrayList<>(counts.length);
        for (int i = 0; i < counts.length; i++) {
            GradeStatisticsVO.ScoreBucket bucket = new GradeStatisticsVO.ScoreBucket();
            bucket.setLabel(BUCKET_LABELS[i]);
            bucket.setMinScore(BigDecimal.valueOf(i == 0 ? 0 : BUCKET_UPPER_BOUNDS[i - 1]).setScale(2, RoundingMode.HALF_UP));
            bucket.setMaxScore(BigDecimal.valueOf(BUCKET_UPPER_BOUNDS[i]).setScale(2, RoundingMode.HALF_UP));
            bucket.setCount(counts[i]);
            bucket.setRatio(total == 0 ? 0d : round4((double) counts[i] / total));
            buckets.add(bucket);
        }
        return buckets;
    }

    private BigDecimal median(List<BigDecimal> sortedScores) {
        int size = sortedScores.size();
        int middle = size / 2;
        if (size % 2 == 1) {
            return scale(sortedScores.get(middle));
        }
        BigDecimal sum = sortedScores.get(middle - 1).add(sortedScores.get(middle));
        return sum.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private List<ExamAnswer> loadAnswers(Long paperId) {
        return examAnswerMapper.selectList(Wrappers.<ExamAnswer>lambdaQuery()
                .eq(ExamAnswer::getPaperId, paperId)
                .orderByAsc(ExamAnswer::getStudentNo));
    }

    private ExamPaper requirePaper(Long paperId) {
        ExamPaper paper = examPaperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException("试卷不存在: " + paperId);
        }
        return paper;
    }

    private String nullToEmpty(BigDecimal value) {
        return value == null ? "" : scale(value).toPlainString();
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private double round4(double value) {
        return Math.round(value * 10000d) / 10000d;
    }
}
