package com.marking.aimarkingsystem.clazz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.clazz.entity.PaperAssignment;
import org.apache.ibatis.annotations.Mapper;

/** 试卷下发 Mapper，单表 CRUD 由 BaseMapper 提供。 */
@Mapper
public interface PaperAssignmentMapper extends BaseMapper<PaperAssignment> {
}
