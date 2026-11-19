package com.marking.aimarkingsystem.learning.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 生成学情分析报告请求。
 *
 * 说明：
 * - 只接收 paperId 与可选标题，统计数据、Prompt、PPT 全部由 Service 生成，
 *   避免前端传入指标导致"报告内容与真实数据不一致"；
 * - creatorId 由登录上下文获取，不放在请求体里，防止伪造创建人；
 * - 报告类型当前固定为 PAPER，等课程级分析落地后再扩展 reportType。
 */
@Data
@Schema(description = "生成学情分析报告请求")
public class LearningAnalysisCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "试卷ID", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "试卷ID不能为空")
    private Long paperId;

    @Schema(description = "报告标题，为空时自动使用《试卷标题-学情分析》", example = "数据结构与算法期末考试学情分析")
    @Size(max = 128, message = "报告标题最长 128 个字符")
    private String reportTitle;
}
