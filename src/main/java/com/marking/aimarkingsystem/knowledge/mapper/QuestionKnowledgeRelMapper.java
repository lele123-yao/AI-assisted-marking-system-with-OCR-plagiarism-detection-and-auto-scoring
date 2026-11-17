package com.marking.aimarkingsystem.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.knowledge.entity.QuestionKnowledgeRel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目-知识点关联 Mapper（多对多桥表）。
 *
 * 关联表不承载业务数据：重新绑定知识点时，Service 层先整批删除旧关联再插入新关联；
 * 若后续为该表补充唯一索引，需参考 UserRoleMapper 改为物理删除注解 SQL。
 */
@Mapper
public interface QuestionKnowledgeRelMapper extends BaseMapper<QuestionKnowledgeRel> {
}
