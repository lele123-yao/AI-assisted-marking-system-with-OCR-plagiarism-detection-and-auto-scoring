package com.marking.aimarkingsystem.clazz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.clazz.entity.ClassInfo;
import org.apache.ibatis.annotations.Mapper;

/** 班级表 Mapper，单表 CRUD 由 BaseMapper 提供。 */
@Mapper
public interface ClassInfoMapper extends BaseMapper<ClassInfo> {
}
