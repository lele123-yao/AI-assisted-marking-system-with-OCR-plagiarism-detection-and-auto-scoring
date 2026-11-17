package com.marking.aimarkingsystem.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.course.entity.Course;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程 Mapper。
 *
 * 继承 BaseMapper 即覆盖单表 CRUD；课程名/教师名等跨表展示信息
 * 由 Service 层分步查询后组装，不在此写多表 SQL。
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {
}
