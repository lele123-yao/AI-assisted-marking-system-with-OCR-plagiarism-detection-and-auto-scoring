package com.marking.aimarkingsystem.paper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.paper.entity.ExamQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 试卷题目 Mapper。
 *
 * 题目的知识点绑定（question_knowledge_rel 多对多）由 Service 层查关联表组装，
 * 不在此写 JOIN 复杂 SQL，与模块1 sys_user_role 的处理方式保持一致。
 */
@Mapper
public interface ExamQuestionMapper extends BaseMapper<ExamQuestion> {
}
