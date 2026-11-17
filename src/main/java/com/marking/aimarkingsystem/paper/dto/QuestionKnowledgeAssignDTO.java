package com.marking.aimarkingsystem.paper.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 题目知识点绑定请求 DTO。
 *
 * 语义：整体覆盖——传空数组表示清空该题目的全部知识点，
 * 传入 ID 集合则替换原有绑定。与"分配用户角色"接口的设计保持一致。
 */
@Data
@Schema(description = "题目知识点绑定请求")
public class QuestionKnowledgeAssignDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "知识点ID集合，传空数组表示清空绑定", example = "[1, 3]")
    @NotNull(message = "知识点ID集合不能为 null")
    @Size(max = 20, message = "一道题最多绑定 20 个知识点")
    private List<Long> knowledgeIds;
}
