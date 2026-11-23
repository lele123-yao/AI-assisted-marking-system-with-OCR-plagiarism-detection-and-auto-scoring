package com.marking.aimarkingsystem.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 阅卷 Agent 复核入参：指定答卷中的某一道题。
 */
@Data
@Schema(description = "阅卷 Agent 复核请求")
public class AgentReviewRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "答卷ID不能为空")
    @Schema(description = "答卷ID", example = "101")
    private Long answerId;

    @NotNull(message = "题目ID不能为空")
    @Schema(description = "题目ID", example = "15")
    private Long questionId;
}
