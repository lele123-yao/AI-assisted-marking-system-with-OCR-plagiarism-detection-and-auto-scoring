package com.marking.aimarkingsystem.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.learning.entity.LearningAnalysisReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学情分析报告 Mapper。
 *
 * 只继承 BaseMapper，报告 CRUD 全部由 MyBatis-Plus 提供；
 * 统计所需的答卷、题目、知识点数据分别由各自 Mapper 查询后在 Service 聚合，
 * 不在 SQL 中做多表 JOIN，保持各模块边界清晰。
 */
@Mapper
public interface LearningAnalysisReportMapper extends BaseMapper<LearningAnalysisReport> {
}
