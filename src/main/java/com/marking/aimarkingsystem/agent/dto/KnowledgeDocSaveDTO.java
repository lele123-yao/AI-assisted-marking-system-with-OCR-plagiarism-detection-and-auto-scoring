package com.marking.aimarkingsystem.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增知识库文档入参。
 * courseId 为空表示全局文档。
 */
@Data
@Schema(description = "新增知识库文档")
public class KnowledgeDocSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "所属课程ID，为空表示全局", example = "1")
    private Long courseId;

    @NotBlank(message = "标题不能为空")
    @Schema(description = "文档标题", example = "主观题评分规范")
    private String title;

    @NotBlank(message = "正文不能为空")
    @Schema(description = "文档正文")
    private String content;

    @Schema(description = "标签，逗号分隔", example = "评分规范,主观题")
    private String tags;

    @Schema(description = "题型，为空表示通用", example = "5")
    private Integer questionType;
}
