package com.marking.aimarkingsystem.statistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 成绩统计 VO（按试卷维度）。
 *
 * 统计口径：
 * - 只统计已有教师最终分的答卷（teacherFinalScore 非空），待录分/阅卷失败的不计入均分与及格率，
 *   但会在 totalCount / unmarkedCount 中体现，保证"总人数"对得上；
 * - 及格率、优秀率、分数段占比均为 0~1 的小数，由前端按需乘 100 显示百分比；
 * - 分数段与逐题统计由 Service 聚合后填充，本 VO 只描述出参结构。
 *
 * 不新建数据表：所有指标均由 exam_answer 实时聚合得出（单课程百人量级无需预计算表）。
 */
@Data
@Schema(description = "试卷成绩统计")
public class GradeStatisticsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "试卷ID", example = "3")
    private Long paperId;

    @Schema(description = "试卷标题", example = "数据结构与算法 期末考试")
    private String paperTitle;

    @Schema(description = "试卷总分", example = "100.00")
    private BigDecimal totalScore;

    @Schema(description = "及格分", example = "60.00")
    private BigDecimal passScore;

    @Schema(description = "答卷总数（含未录分）", example = "60")
    private Integer totalCount;

    @Schema(description = "已出分答卷数（参与统计）", example = "58")
    private Integer markedCount;

    @Schema(description = "未出分答卷数（待录分/识别中/失败）", example = "2")
    private Integer unmarkedCount;

    @Schema(description = "平均分，保留两位小数", example = "76.35")
    private BigDecimal avgScore;

    @Schema(description = "最高分", example = "98.00")
    private BigDecimal maxScore;

    @Schema(description = "最低分", example = "41.50")
    private BigDecimal minScore;

    @Schema(description = "中位数", example = "78.00")
    private BigDecimal medianScore;

    @Schema(description = "及格人数", example = "51")
    private Integer passCount;

    @Schema(description = "及格率（0~1）", example = "0.8793")
    private Double passRate;

    @Schema(description = "优秀人数（默认 ≥ 90% 试卷总分）", example = "12")
    private Integer excellentCount;

    @Schema(description = "优秀率（0~1）", example = "0.2069")
    private Double excellentRate;

    @Schema(description = "分数段分布，按分数从低到高排列")
    private List<ScoreBucket> scoreDistribution;

    @Schema(description = "逐题统计，按题号顺序排列")
    private List<QuestionStat> questionStats;

    /** 分数段 */
    @Data
    @Schema(description = "分数段分布项")
    public static class ScoreBucket implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "分数段文案（得分率百分比区间）", example = "60-69")
        private String label;

        @Schema(description = "分数段下限（得分率百分比，含）", example = "60.00")
        private BigDecimal minScore;

        @Schema(description = "分数段上限（得分率百分比，不含；最后一段含100）", example = "70.00")
        private BigDecimal maxScore;

        @Schema(description = "该段人数", example = "14")
        private Integer count;

        @Schema(description = "该段占比（0~1）", example = "0.2414")
        private Double ratio;
    }

    /** 逐题统计 */
    @Data
    @Schema(description = "逐题得分统计项")
    public static class QuestionStat implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "题目ID", example = "11")
        private Long questionId;

        @Schema(description = "题号", example = "1")
        private String questionNo;

        @Schema(description = "题型", example = "简答题")
        private String questionType;

        @Schema(description = "该题满分", example = "20.00")
        private BigDecimal fullScore;

        @Schema(description = "有效作答人数", example = "57")
        private Integer answerCount;

        @Schema(description = "平均得分", example = "14.62")
        private BigDecimal avgScore;

        @Schema(description = "最高得分", example = "20.00")
        private BigDecimal maxScore;

        @Schema(description = "最低得分", example = "3.00")
        private BigDecimal minScore;

        @Schema(description = "得分率（平均分/满分，0~1）", example = "0.7310")
        private Double scoreRate;

        @Schema(description = "满分人数", example = "5")
        private Integer fullMarkCount;
    }
}
