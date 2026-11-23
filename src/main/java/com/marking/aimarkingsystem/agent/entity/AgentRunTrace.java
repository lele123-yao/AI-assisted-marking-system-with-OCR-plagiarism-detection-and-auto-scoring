package com.marking.aimarkingsystem.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 推理轨迹，对应表 agent_run_trace。
 *
 * 一次复核（runNo）会产生多行：
 * - 工具调用行：roundNo > 0，记录 thought/tool/input/observation；
 * - 收口行：roundNo = 0，记录 finalAnswer/success/durationMs。
 *
 * 为什么要落库而不是只打日志：
 * - 大模型结果不可解释是面试高频质疑，落库后可以按 runNo 还原完整推理链路；
 * - 出问题能定位是"模型选错工具""工具返回脏数据"还是"模型自己编了得分点"。
 */
@Data
@TableName("agent_run_trace")
public class AgentRunTrace implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 一次复核/对话的唯一编号 */
    private String runNo;

    /** MARKING_REVIEW / TEACHER_CHAT */
    private String agentType;

    private Long answerId;

    private Long questionId;

    /** 第几次尝试：1 初评，2/3 为 Checker 打回后的修正 */
    private Integer attemptNo;

    /** ReAct 轮次；0 表示收口行 */
    private Integer roundNo;

    private String thought;

    private String toolName;

    private String toolInput;

    private String observation;

    private String finalAnswer;

    private Integer success;

    private Long durationMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
