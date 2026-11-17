package com.marking.aimarkingsystem.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marking.aimarkingsystem.permission.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限表 Mapper。
 *
 * 树形菜单的组装在 Service 层用 parentId 递归完成，Mapper 保持纯净单表操作；
 * 后续如需联表查询"某角色的全部权限"，在此添加自定义方法并编写 XML。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
