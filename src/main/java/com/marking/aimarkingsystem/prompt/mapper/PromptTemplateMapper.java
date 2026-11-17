package com.marking.aimarkingsystem.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.prompt.entity.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提示词模板 Mapper。
 *
 * 模板按"课程+题型 > 通用+题型"的匹配选择逻辑在 Service / AI 阅卷模块实现，
 * 本 Mapper 只做单表 CRUD。
 */
@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {
}
