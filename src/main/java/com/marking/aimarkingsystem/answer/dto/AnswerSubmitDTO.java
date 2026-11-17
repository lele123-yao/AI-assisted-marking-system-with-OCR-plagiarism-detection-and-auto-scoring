package com.marking.aimarkingsystem.answer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 线上答题提交 DTO。
 *
 * 说明：
 * - studentId 不从 DTO 接收，由 Service 取当前登录用户，防止替他人交卷；
 * - answerContent 约定为按题号组织的 JSON 字符串，如
 *   {"1":"栈是后进先出...","2":"先序遍历..."}，
 *   用字符串而非结构化对象，是因为题目数量与题型可变，转成 DTO 需要动态字段；
 * - 提交时机（是否在考试时间窗内）属于 Service 业务校验，DTO 不做限制。
 */
@Data
@Schema(description = "线上答题提交请求")
public class AnswerSubmitDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "试卷ID", example = "1")
    @NotNull(message = "试卷ID不能为空")
    @Min(value = 1, message = "试卷ID不合法")
    private Long paperId;

    @Schema(description = "作答内容，按题号组织的JSON字符串",
            example = "{\"1\":\"栈是后进先出...\",\"2\":\"先序遍历...\"}")
    @NotBlank(message = "作答内容不能为空")
    @Size(max = 200000, message = "作答内容过长")
    private String answerContent;
}
