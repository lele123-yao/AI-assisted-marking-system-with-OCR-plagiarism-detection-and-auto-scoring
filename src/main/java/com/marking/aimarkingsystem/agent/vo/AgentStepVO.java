package com.marking.aimarkingsystem.agent.vo;

import com.marking.aimarkingsystem.agent.core.AgentStep;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Agent 单步轨迹，前端折叠展示"思考 -> 调了什么工具 -> 看到了什么"。
 */
@Data
@Schema(description = "Agent 工具调用轨迹")
public class AgentStepVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "第几轮")
    private Integer step;

    @Schema(description = "模型思考")
    private String thought;

    @Schema(description = "工具名")
    private String tool;

    @Schema(description = "调用参数")
    private String input;

    @Schema(description = "工具返回结果")
    private String observation;

    public static AgentStepVO from(AgentStep step) {
        AgentStepVO vo = new AgentStepVO();
        vo.setStep(step.step());
        vo.setThought(step.thought());
        vo.setTool(step.tool());
        vo.setInput(step.input());
        vo.setObservation(step.observation());
        return vo;
    }
}
