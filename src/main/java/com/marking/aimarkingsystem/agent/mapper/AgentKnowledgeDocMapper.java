package com.marking.aimarkingsystem.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.agent.entity.AgentKnowledgeDoc;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档 Mapper：单表 CRUD 由 MyBatis-Plus 提供。
 * 检索打分在 Service 内存中完成，不写自定义 SQL。
 */
@Mapper
public interface AgentKnowledgeDocMapper extends BaseMapper<AgentKnowledgeDoc> {
}
