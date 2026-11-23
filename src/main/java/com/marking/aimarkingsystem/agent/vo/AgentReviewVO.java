package com.marking.aimarkingsystem.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 阅卷 Agent 复核结果。
 *
 * draft 是主 Agent 初次给出的评分，final 是经过 Checker 自检、必要时修正后的定稿；
 * 两者都返回，教师能看到 Agent 是否在自检中修正过，而不是只有一个黑盒分数。
 *
 * checkerPassed / checkerIssues / degraded 三个字段是"可解释性"的落点：
 * - checkerPassed=true 说明初评就符合规则；
 * - checkerPassed=false + degraded=true 说明修正失败，最终采用原始初评；
 * - checkerIssues 是 Checker 给出的全部问题，前端可以直接展示。
 */
@Data
@Schema(description = "阅卷 Agent 复核结果")
public class AgentReviewVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "本次运行编号，可用于查询完整推理轨迹")
    private String runNo;

    @Schema(description = "题目ID")
    private Long questionId;

    @Schema(description = "题号")
    private String questionNo;

    @Schema(description = "本题满分")
    private BigDecimal fullScore;

    @Schema(description = "初评分数")
    private BigDecimal draftScore;

    @Schema(description = "初评依据")
    private String draftComment;

    @Schema(description = "自检后的最终分数")
    private BigDecimal finalScore;

    @Schema(description = "自检后的最终依据")
    private String finalComment;

    @Schema(description = "Checker 自检是否通过")
    private Boolean checkerPassed;

    @Schema(description = "Checker 发现的问题")
    private List<String> checkerIssues;

    @Schema(description = "是否发生过修正")
    private Boolean corrected;

    @Schema(description = "修正轮数：0 表示初评直接通过")
    private Integer correctionRounds;

    @Schema(description = "是否降级（修正失败时保留原始初评）")
    private Boolean degraded;

    @Schema(description = "工具调用轨迹")
    private List<AgentStepVO> steps;
}
