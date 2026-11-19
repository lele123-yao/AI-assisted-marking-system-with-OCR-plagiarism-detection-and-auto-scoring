package com.marking.aimarkingsystem.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生个人学情卡片 VO（模块8）。
 *
 * 数据来源：
 * - 学生身份与分数：exam_answer（同一试卷多次作答时取最新一条）；
 * - 班级均值/及格率：复用 LearningAnalysisService.statistics 的班级指标；
 * - 薄弱题目与知识点：从该答卷 answerContent 的逐题得分明细反推。
 *
 * 统计口径：
 * - 排名只在"已出分"答卷之间计算；
 * - 知识点只统计该学生有逐题得分明细的题目，避免教师只录总分时把未作答题目误判为 0 分；
 * - suggestion 是规则化文案（非大模型），保证接口稳定、可解释。
 */
@Data
@Schema(description = "学生个人学情卡片")
public class StudentLearningCardVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ==================== 试卷信息 ====================

    @Schema(description = "试卷ID", example = "3")
    private Long paperId;

    @Schema(description = "试卷标题", example = "数据结构与算法 期末考试")
    private String paperTitle;

    @Schema(description = "试卷总分", example = "100.00")
    private BigDecimal totalScore;

    @Schema(description = "及格分", example = "60.00")
    private BigDecimal passScore;

    // ==================== 学生与答卷信息 ====================

    @Schema(description = "答卷ID", example = "101")
    private Long answerId;

    @Schema(description = "学生ID", example = "12")
    private Long studentId;

    @Schema(description = "学号", example = "20260001")
    private String studentNo;

    @Schema(description = "学生姓名", example = "张三")
    private String studentName;

    @Schema(description = "学生班级", example = "计算机2026级1班")
    private String studentClass;

    @Schema(description = "答卷来源：ONLINE 线上作答，PAPER_SCAN 纸质扫描", example = "ONLINE")
    private String answerSource;

    @Schema(description = "答卷来源文案", example = "线上作答")
    private String answerSourceDesc;

    @Schema(description = "答卷状态", example = "5")
    private Integer markingStatus;

    @Schema(description = "答卷状态文案", example = "教师已确认归档")
    private String markingStatusDesc;

    @Schema(description = "AI参考分", example = "78.00")
    private BigDecimal aiReferenceScore;

    @Schema(description = "教师最终得分，未出分时为空", example = "80.00")
    private BigDecimal teacherFinalScore;

    @Schema(description = "是否及格", example = "true")
    private Boolean passed;

    @Schema(description = "阅卷/确认时间")
    private LocalDateTime markTime;

    // ==================== 对比与排名 ====================

    @Schema(description = "班级平均分", example = "76.35")
    private BigDecimal classAvgScore;

    @Schema(description = "班级及格率（0~1）", example = "0.8793")
    private Double classPassRate;

    @Schema(description = "与班级平均分的差值，正数为高于班级均值", example = "3.65")
    private BigDecimal scoreDiffToClassAvg;

    @Schema(description = "班级排名（已出分学生中）", example = "8")
    private Integer classRank;

    @Schema(description = "班级已出分人数", example = "29")
    private Integer classSize;

    @Schema(description = "试卷排名（已出分学生中）", example = "15")
    private Integer paperRank;

    @Schema(description = "试卷已出分人数", example = "58")
    private Integer paperSize;

    // ==================== 学情明细 ====================

    @Schema(description = "薄弱题目（得分率低于 60%，最多 10 道）")
    private List<QuestionItem> weakQuestions;

    @Schema(description = "该学生知识点掌握情况（按得分率升序）")
    private List<KnowledgeItem> knowledgeStats;

    @Schema(description = "规则化学习建议", example = "本次考试需重点巩固：二叉树遍历（得分率 40.0%）。建议先回顾课堂例题，再完成针对性练习。")
    private String suggestion;

    /** 学生单题作答情况 */
    @Data
    @Schema(description = "学生单题作答情况")
    public static class QuestionItem implements Serializable {

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

        @Schema(description = "该题得分", example = "4.00")
        private BigDecimal score;

        @Schema(description = "该题得分率（0~1）", example = "0.4000")
        private Double scoreRate;

        @Schema(description = "AI 对该题的评语", example = "遍历顺序正确，但未说明递归终止条件")
        private String comment;
    }

    /** 学生知识点掌握情况 */
    @Data
    @Schema(description = "学生知识点掌握情况")
    public static class KnowledgeItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "知识点ID", example = "21")
        private Long knowledgeId;

        @Schema(description = "知识点名称", example = "二叉树遍历")
        private String knowledgeName;

        @Schema(description = "该学生有得分明细的关联题数", example = "2")
        private Integer questionCount;

        @Schema(description = "累计满分", example = "20.00")
        private BigDecimal fullScore;

        @Schema(description = "累计得分", example = "8.00")
        private BigDecimal earnedScore;

        @Schema(description = "得分率（0~1）", example = "0.4000")
        private Double scoreRate;

        @Schema(description = "是否薄弱（得分率低于 60%）", example = "true")
        private Boolean weak;
    }
}
