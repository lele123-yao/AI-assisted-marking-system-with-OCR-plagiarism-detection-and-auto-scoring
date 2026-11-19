package com.marking.aimarkingsystem.learning.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 学情分析报告分页查询条件。
 *
 * 数据权限（占位）：查询时限制在当前教师所授课程范围内，
 * 具体校验在 Controller 取到登录用户后由 Service 追加条件。
 */
@Data
@Schema(description = "学情分析报告分页查询条件")
public class LearningAnalysisQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Long pageNum = 1L;

    @Schema(description = "每页条数，最大 100", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Long pageSize = 10L;

    @Schema(description = "试卷ID，可空", example = "3")
    @Min(value = 1, message = "试卷ID不合法")
    private Long paperId;

    @Schema(description = "报告类型：PAPER 单试卷，COURSE 课程阶段", example = "PAPER")
    @Pattern(regexp = "PAPER|COURSE", message = "报告类型只能是 PAPER 或 COURSE")
    private String reportType;

    @Schema(description = "生成状态：0 生成中，1 成功，2 失败", example = "1")
    @Min(value = 0, message = "状态不合法")
    @Max(value = 2, message = "状态不合法")
    private Integer status;
}
