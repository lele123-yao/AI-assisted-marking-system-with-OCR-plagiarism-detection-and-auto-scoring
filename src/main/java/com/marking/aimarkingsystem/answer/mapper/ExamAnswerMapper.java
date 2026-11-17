package com.marking.aimarkingsystem.answer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.answer.entity.ExamAnswer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 答卷 Mapper。
 *
 * 只继承 BaseMapper，单表 CRUD 全部由 MyBatis-Plus 提供。
 * 试卷标题、学生姓名等跨表展示字段由 Service 批量查询后组装，
 * 不在此写 JOIN 复杂 SQL；异步阅卷、抄袭检测等模块后续也通过本 Mapper 读写答卷主表。
 */
@Mapper
public interface ExamAnswerMapper extends BaseMapper<ExamAnswer> {
}
