package com.marking.aimarkingsystem.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 教师助手回答 + 工具调用轨迹。
 */
@Data
@Schema(description = "教师助手回答")
public class AgentChatVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "本次运行编号，可用于查询完整推理轨迹")
    private String runNo;

    @Schema(description = "助手回答")
    private String reply;

    @Schema(description = "工具调用轨迹")
    private List<AgentStepVO> steps;
}
