package com.marking.aimarkingsystem.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.knowledge.entity.QuestionKnowledge;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识点标签 Mapper。
 *
 * 树形结构的父子组装由 Service 层完成（先全量查出再内存组树），
 * 不在此写递归 SQL。
 */
@Mapper
public interface QuestionKnowledgeMapper extends BaseMapper<QuestionKnowledge> {
}
