package com.marking.aimarkingsystem.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户表 Mapper。
 *
 * BaseMapper 已提供单表 CRUD（selectById、selectOne、insert、updateById、
 * removeById 逻辑删除等），单表场景无需编写 XML。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 按用户名统计记录数（包含已逻辑删除的记录）。
     *
     * 为什么不用 MyBatis-Plus 的 selectCount：
     * - selectCount 会自动追加 del_flag = 0，若同名账号已被逻辑删除，预校验会通过，
     *   但数据库唯一索引仍然存在，最终插入时才会报错；
     * - 这里手写 SQL 不加 del_flag 条件，提前给出"用户名已存在"的业务提示。
     */
    @Select("SELECT COUNT(*) FROM sys_user WHERE username = #{username}")
    long countByUsername(@Param("username") String username);
}
