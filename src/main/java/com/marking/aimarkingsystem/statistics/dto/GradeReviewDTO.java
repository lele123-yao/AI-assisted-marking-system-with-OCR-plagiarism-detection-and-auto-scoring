package com.marking.aimarkingsystem.statistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 成绩复核提交 DTO。
 *
 * 复核是对"AI 参考分 + 教师最终分"的二次确认，三种处理方式：
 * - reviewAction = 1 复核通过：维持现有最终分，答卷进入归档；
 * - reviewAction = 2 改分：必须提交 finalScore（可同时提交逐题得分明细）；
 * - reviewAction = 3 驳回重批：成绩退回待录入状态，由教师重新录分。
 *
 * 校验分工：
 * - 单字段格式（非空、范围、精度）由 JSR303 在本 DTO 完成；
 * - 跨字段规则（改分必须给 finalScore、逐题得分合计必须等于 finalScore、
 *   改分后不能与 AI 参考分完全相同）由 Service 校验，注解无法表达。
 */
@Data
@Schema(description = "成绩复核提交参数")
public class GradeReviewDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "答卷ID", example = "101")
    @NotNull(message = "答卷ID不能为空")
    @Min(value = 1, message = "答卷ID不合法")
    private Long answerId;

    @Schema(description = "复核处理方式：1 复核通过，2 改分，3 驳回重批", example = "2")
    @NotNull(message = "复核处理方式不能为空")
    @Min(value = 1, message = "复核处理方式不合法")
    @Max(value = 3, message = "复核处理方式不合法")
    private Integer reviewAction;

    @Schema(description = "改分后的最终得分，reviewAction=2 时必填", example = "33.50")
    @DecimalMin(value = "0.00", message = "最终得分不能为负数")
    @DecimalMax(value = "999.99", message = "最终得分超出允许范围")
    @Digits(integer = 3, fraction = 2, message = "最终得分最多两位小数")
    private BigDecimal finalScore;

    @Schema(description = "逐题得分明细，reviewAction=2 时可选；传了则合计必须等于 finalScore")
    @Valid
    private List<ItemScore> items;

    @Schema(description = "复核意见，驳回时建议填写原因", example = "第2题评分偏高，调整为12分")
    @Size(max = 500, message = "复核意见最长 500 字")
    private String reviewRemark;

    /** 逐题得分明细项 */
    @Data
    @Schema(description = "复核逐题得分")
    public static class ItemScore implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "题目ID", example = "11")
        @NotNull(message = "题目ID不能为空")
        @Min(value = 1, message = "题目ID不合法")
        private Long questionId;

        @Schema(description = "该题得分", example = "12.00")
        @NotNull(message = "题目得分不能为空")
        @DecimalMin(value = "0.00", message = "题目得分不能为负数")
        @DecimalMax(value = "999.99", message = "题目得分超出允许范围")
        @Digits(integer = 3, fraction = 2, message = "题目得分最多两位小数")
        private BigDecimal score;
    }
}
