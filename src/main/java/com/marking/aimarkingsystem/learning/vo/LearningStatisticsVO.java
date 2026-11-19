package com.marking.aimarkingsystem.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 学情统计快照（模块8）。
 *
 * 这是"统计 -> 大模型 -> PPT"链路中间的数据结构：
 * - 由 exam_answer / exam_question / question_knowledge_rel 实时聚合得到；
 * - 既作为生成 Prompt 的数据源，也直接回传给前端用于报告详情页展示；
 * - 不在数据库持久化，避免统计数据与答卷表不一致（每次重新聚合即可）。
 *
 * 统计口径：
 * - 总分/及格线/优秀线沿用成绩统计模块：及格线取 pass_score，为空按总分 60%，优秀线取 90%；
 * - 班级指标按 student_class 分组，未填写班级的答卷归入"未分班"；
 * - 逐题得分取答卷 answerContent 中 AI 写入的逐题明细（questionId + score），
 *   纸质答卷教师手动录分未回写明细时，该题不计入逐题统计；
 * - 得分率均为 0~1 的小数，前端按需乘 100 显示。
 */
@Data
@Schema(description = "学情统计快照")
public class LearningStatisticsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "试卷ID", example = "3")
    private Long paperId;

    @Schema(description = "试卷标题", example = "数据结构与算法 期末考试")
    private String paperTitle;

    @Schema(description = "课程ID", example = "1")
    private Long courseId;

    @Schema(description = "试卷总分", example = "100.00")
    private BigDecimal totalScore;

    @Schema(description = "及格分", example = "60.00")
    private BigDecimal passScore;

    @Schema(description = "答卷总数（含未出分）", example = "60")
    private Integer totalCount;

    @Schema(description = "已出分答卷数（参与统计）", example = "58")
    private Integer markedCount;

    @Schema(description = "平均分", example = "76.35")
    private BigDecimal avgScore;

    @Schema(description = "最高分", example = "98.00")
    private BigDecimal maxScore;

    @Schema(description = "最低分", example = "41.50")
    private BigDecimal minScore;

    @Schema(description = "及格人数", example = "51")
    private Integer passCount;

    @Schema(description = "及格率（0~1）", example = "0.8793")
    private Double passRate;

    @Schema(description = "优秀人数（≥ 总分 90%）", example = "12")
    private Integer excellentCount;

    @Schema(description = "优秀率（0~1）", example = "0.2069")
    private Double excellentRate;

    @Schema(description = "班级维度指标")
    private List<ClassMetric> classMetrics;

    @Schema(description = "高频错题（得分率低于 60%，按低分人数降序）")
    private List<WeakQuestion> weakQuestions;

    @Schema(description = "知识点得分率（按得分率升序）")
    private List<KnowledgeStat> knowledgeStats;

    /** 班级维度指标 */
    @Data
    @Schema(description = "班级学情指标")
    public static class ClassMetric implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "班级名称，未填写时为“未分班”", example = "计算机2026级1班")
        private String className;

        @Schema(description = "班级答卷总数", example = "30")
        private Integer studentCount;

        @Schema(description = "已出分人数", example = "29")
        private Integer markedCount;

        @Schema(description = "平均分", example = "78.20")
        private BigDecimal avgScore;

        @Schema(description = "最高分", example = "95.00")
        private BigDecimal maxScore;

        @Schema(description = "最低分", example = "52.00")
        private BigDecimal minScore;

        @Schema(description = "及格人数", example = "26")
        private Integer passCount;

        @Schema(description = "及格率（0~1）", example = "0.8966")
        private Double passRate;

        @Schema(description = "优秀人数", example = "7")
        private Integer excellentCount;

        @Schema(description = "优秀率（0~1）", example = "0.2414")
        private Double excellentRate;
    }

    /** 高频错题 */
    @Data
    @Schema(description = "薄弱题目统计")
    public static class WeakQuestion implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "题目ID", example = "11")
        private Long questionId;

        @Schema(description = "题号", example = "3")
        private String questionNo;

        @Schema(description = "题型名称", example = "简答题")
        private String questionType;

        @Schema(description = "该题满分", example = "10.00")
        private BigDecimal fullScore;

        @Schema(description = "有效作答人数", example = "58")
        private Integer answerCount;

        @Schema(description = "平均得分", example = "4.32")
        private BigDecimal avgScore;

        @Schema(description = "得分率（0~1）", example = "0.4320")
        private Double scoreRate;

        @Schema(description = "低分人数（得分低于该题满分 60%）", example = "37")
        private Integer wrongCount;

        @Schema(description = "低分率（0~1）", example = "0.6379")
        private Double wrongRate;
    }

    /** 知识点得分率 */
    @Data
    @Schema(description = "知识点得分统计")
    public static class KnowledgeStat implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "知识点ID", example = "21")
        private Long knowledgeId;

        @Schema(description = "知识点名称", example = "二叉树遍历")
        private String knowledgeName;

        @Schema(description = "关联题目数", example = "3")
        private Integer questionCount;

        @Schema(description = "涉及作答人次（题目作答人数之和）", example = "170")
        private Integer answerCount;

        @Schema(description = "累计满分（各题满分 × 作答人数之和）", example = "300.00")
        private BigDecimal fullScore;

        @Schema(description = "平均得分（累计得分 / 作答人次）", example = "6.80")
        private BigDecimal avgScore;

        @Schema(description = "得分率（累计得分 / 累计满分，0~1）", example = "0.6800")
        private Double scoreRate;
    }
}
