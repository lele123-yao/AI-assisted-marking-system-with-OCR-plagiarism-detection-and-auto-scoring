package com.marking.aimarkingsystem.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.agent.entity.AgentKnowledgeVector;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库向量 Mapper。向量内容用 LONGTEXT 存取，计算在应用层完成。
 */
@Mapper
public interface AgentKnowledgeVectorMapper extends BaseMapper<AgentKnowledgeVector> {
}
