package com.marking.aimarkingsystem.prompt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 提示词模板新增 / 编辑 DTO（编辑时 id 通过路径参数传递，body 不含 id）。
 *
 * 说明：
 * - userPrompt 必填：它是评分正文，占位符 {{question}} {{standardAnswer}} {{scoringRule}}
 *   {{studentAnswer}} {{score}} 由 AI 阅卷模块在调用大模型前替换；
 * - version 不在 DTO 中：新增固定为 1，修改时由 Service 层递增，保证历史版本可回溯；
 * - temperature 不传时由 Service 默认 0.20。
 */
@Data
@Schema(description = "提示词模板保存请求（新增/编辑共用）")
public class PromptTemplateSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "模板编码（唯一）", example = "SHORT_ANSWER_DEFAULT")
    @NotBlank(message = "模板编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{2,64}$", message = "模板编码只能由字母、数字、下划线组成，长度 2-64 位")
    private String templateCode;

    @Schema(description = "模板名称", example = "简答题通用评分")
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 128, message = "模板名称最长 128 位")
    private String templateName;

    @Schema(description = "适用题型：1单选 2多选 3判断 4填空 5简答 6论述 7计算 8案例；0 表示通用", example = "5")
    @NotNull(message = "适用题型不能为空")
    @Min(value = 0, message = "题型取值 0-8（0 表示通用）")
    @Max(value = 8, message = "题型取值 0-8（0 表示通用）")
    private Integer questionType;

    @Schema(description = "限定课程ID，不传表示全校通用", example = "1")
    @Min(value = 1, message = "课程ID不合法")
    private Long courseId;

    @Schema(description = "系统提示词：设定AI角色与评分原则",
            example = "你是一名严谨的高校课程阅卷教师。请严格依据标准答案与评分细则评分，只输出JSON。")
    @Size(max = 10000, message = "系统提示词最长 10000 字符")
    private String systemPrompt;

    @Schema(description = "用户提示词模板，支持占位符",
            example = "题目：{{question}}\n标准答案：{{standardAnswer}}\n学生作答：{{studentAnswer}}\n本题满分：{{score}}")
    @NotBlank(message = "用户提示词不能为空")
    @Size(max = 10000, message = "用户提示词最长 10000 字符")
    private String userPrompt;

    @Schema(description = "指定大模型名称，不传使用系统默认模型", example = "deepseek-chat")
    @Size(max = 64, message = "模型名称最长 64 位")
    private String modelName;

    @Schema(description = "采样温度，评分场景建议 0~0.3；不传默认 0.20", example = "0.20")
    @DecimalMin(value = "0.00", message = "温度不能小于 0")
    @DecimalMax(value = "2.00", message = "温度不能超过 2.00")
    private BigDecimal temperature;

    @Schema(description = "状态：1 启用，0 停用；不传默认 1", example = "1")
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;

    @Schema(description = "备注", example = "简答题默认模板")
    @Size(max = 512, message = "备注最长 512 位")
    private String remark;
}
