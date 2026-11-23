package com.marking.aimarkingsystem.answer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 教师手动录入最终分数 DTO（纸质试卷线下批改）。
 *
 * 结构：
 * - items：逐题得分明细，教师按题录入，Service 会校验"每题得分不超过该题满分"、
 *   "各题得分之和 = teacherFinalScore"，避免出现总分与明细对不上的数据；
 * - teacherFinalScore：教师最终得分，必须由逐题得分汇总得出，不能凭空填写。
 *
 * 业务约束（在 Service 中校验，不能只靠注解）：
 * - 各题得分之和必须等于 teacherFinalScore；
 * - 教师可以直接采纳 AI 参考分，最终成绩以教师提交的为准；
 * - AI 参考分字段永远由系统写入，本 DTO 不接收，防止前端覆盖。
 *
 * 说明：answerId 通过路径参数传入，不放在请求体里，避免路径与 body 两个 id 不一致。
 */
@Data
@Schema(description = "教师手动录入最终分数请求")
public class TeacherScoreDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "逐题得分明细，至少一条")
    @NotEmpty(message = "请至少录入一道题的得分")
    @Valid
    private List<QuestionScore> items;

    @Schema(description = "教师最终得分（必须等于各题得分之和，0.00 ~ 999.99）", example = "33.50")
    @NotNull(message = "最终得分不能为空")
    @DecimalMin(value = "0.00", message = "最终得分不能为负数")
    @DecimalMax(value = "999.99", message = "最终得分超出允许范围")
    @Digits(integer = 3, fraction = 2, message = "最终得分最多两位小数")
    private BigDecimal teacherFinalScore;

    /**
     * 单题得分明细。
     *
     * questionId 用题目主键而不是题号字符串：题号（如"一.1"）可修改，
     * 用主键才能稳定关联 exam_question，后续学情分析按知识点统计时也以它为外键。
     */
    @Data
    @Schema(description = "单题得分")
    public static class QuestionScore implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "题目ID（必须属于该答卷的试卷）", example = "11")
        @NotNull(message = "题目ID不能为空")
        @Min(value = 1, message = "题目ID不合法")
        private Long questionId;

        @Schema(description = "该题得分，上限为该题满分", example = "8.50")
        @NotNull(message = "题目得分不能为空")
        @DecimalMin(value = "0.00", message = "题目得分不能为负数")
        @Digits(integer = 3, fraction = 2, message = "题目得分最多两位小数")
        private BigDecimal score;

        @Schema(description = "教师对该题的评语，可为空", example = "要点齐全，表述清楚")
        @jakarta.validation.constraints.Size(max = 1000, message = "评语最长 1000 字")
        private String comment;
    }
}
