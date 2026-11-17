package com.marking.aimarkingsystem.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.role.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色表 Mapper。
 *
 * 单表 CRUD 由 BaseMapper 提供；后续"批量按 ID 查询角色名称"等
 * 高频场景直接使用 selectBatchIds 即可。
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
