package com.marking.aimarkingsystem.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.notify.entity.NotifyMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内通知 Mapper。
 *
 * 只继承 BaseMapper：分页、未读数、标记已读全部用 MyBatis-Plus 的 Wrapper 完成，
 * 无需自定义 XML。
 */
@Mapper
public interface NotifyMessageMapper extends BaseMapper<NotifyMessage> {
}
