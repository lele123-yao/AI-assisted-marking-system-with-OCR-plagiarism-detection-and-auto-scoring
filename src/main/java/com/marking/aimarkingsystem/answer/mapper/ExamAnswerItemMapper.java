package com.marking.aimarkingsystem.answer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.answer.entity.ExamAnswerItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 答卷逐题结果 Mapper。
 * 单表 CRUD 直接复用 BaseMapper，无需手写 XML。
 */
@Mapper
public interface ExamAnswerItemMapper extends BaseMapper<ExamAnswerItem> {
}
