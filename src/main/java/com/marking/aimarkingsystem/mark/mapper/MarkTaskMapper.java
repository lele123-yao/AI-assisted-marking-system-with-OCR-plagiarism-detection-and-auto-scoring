package com.marking.aimarkingsystem.mark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.mark.entity.MarkTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异步阅卷/OCR 任务 Mapper。
 *
 * 只继承 BaseMapper，单表 CRUD 全部由 MyBatis-Plus 提供。
 * 任务状态流转（待消费→处理中→成功/失败）、重试计数、死信转移等
 * 全部由后续阶段的 Service 层拼装 Wrapper 实现，本 Mapper 零自定义 SQL。
 */
@Mapper
public interface MarkTaskMapper extends BaseMapper<MarkTask> {
}
