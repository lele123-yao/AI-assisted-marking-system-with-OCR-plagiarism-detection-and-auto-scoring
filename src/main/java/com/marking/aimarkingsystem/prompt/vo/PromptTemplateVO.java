package com.marking.aimarkingsystem.prompt.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提示词模板视图对象 VO。
 *
 * 说明：
 * - systemPrompt / userPrompt 完整返回，便于模板管理页面编辑与预览；
 * - courseName 由 Service 关联课程表填充，展示模板适用范围（全校通用 / 某课程）。
 */
@Data
@Schema(description = "提示词模板信息")
public class PromptTemplateVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "模板ID", example = "1")
    private Long id;

    @Schema(description = "模板编码", example = "SHORT_ANSWER_DEFAULT")
    private String templateCode;

    @Schema(description = "模板名称", example = "简答题通用评分")
    private String templateName;

    @Schema(description = "适用题型：1单选 2多选 3判断 4填空 5简答 6论述 7计算 8案例；0 通用", example = "5")
    private Integer questionType;

    @Schema(description = "限定课程ID，null 表示全校通用", example = "1")
    private Long courseId;

    @Schema(description = "限定课程名称（Service 填充，null 表示全校通用）", example = "数据结构与算法")
    private String courseName;

    @Schema(description = "系统提示词")
    private String systemPrompt;

    @Schema(description = "用户提示词模板，支持占位符")
    private String userPrompt;

    @Schema(description = "指定大模型名称，null 使用系统默认", example = "deepseek-chat")
    private String modelName;

    @Schema(description = "采样温度", example = "0.20")
    private BigDecimal temperature;

    @Schema(description = "模板版本号", example = "1")
    private Integer version;

    @Schema(description = "状态：1 启用，0 停用", example = "1")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
