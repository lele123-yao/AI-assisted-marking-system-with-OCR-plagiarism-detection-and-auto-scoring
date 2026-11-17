package com.marking.aimarkingsystem.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.user.entity.UserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户-角色关联表 Mapper。
 *
 * 说明：关联表使用物理删除（而非 @TableLogic 逻辑删除），
 * 因为 sys_user_role 上有 (user_id, role_id) 唯一索引，
 * 若只把 del_flag 置 1，再次给同一用户分配同一角色时会触发唯一键冲突。
 * 关联表本身不承载业务数据，物理删除是更合适的做法。
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /** 物理删除某用户的全部角色关联，用于"重新分配角色"前清空 */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") Long userId);
}
