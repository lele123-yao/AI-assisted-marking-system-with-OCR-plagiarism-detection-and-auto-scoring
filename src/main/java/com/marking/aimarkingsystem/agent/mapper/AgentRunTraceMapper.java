package com.marking.aimarkingsystem.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.agent.entity.AgentRunTrace;
import org.apache.ibatis.annotations.Mapper;

/**
 * 推理轨迹 Mapper。
 * 只做单表读写，查询按 runNo / answerId 走索引。
 */
@Mapper
public interface AgentRunTraceMapper extends BaseMapper<AgentRunTrace> {
}
