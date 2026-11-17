package com.marking.aimarkingsystem.paper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.paper.entity.ExamPaper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 试卷 Mapper。
 *
 * 试卷的汇总回写（totalScore/questionCount）、状态流转校验均在 Service 层完成；
 * 本 Mapper 只做单表 CRUD，保持薄。
 */
@Mapper
public interface ExamPaperMapper extends BaseMapper<ExamPaper> {
}
