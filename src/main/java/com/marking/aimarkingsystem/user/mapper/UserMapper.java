package com.marking.aimarkingsystem.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper。
 *
 * BaseMapper 已提供单表 CRUD（selectById、selectOne、insert、updateById、
 * removeById 逻辑删除等），单表场景无需编写 XML。
 * 后续如需"按用户名查询"等自定义方法，可在此添加接口方法配合 Wrapper 或 XML。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
