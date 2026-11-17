package com.marking.aimarkingsystem.role.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.ResultCode;
import com.marking.aimarkingsystem.role.dto.RoleDTO;
import com.marking.aimarkingsystem.role.entity.Role;
import com.marking.aimarkingsystem.role.mapper.RoleMapper;
import com.marking.aimarkingsystem.role.vo.RoleVO;
import com.marking.aimarkingsystem.user.entity.UserRole;
import com.marking.aimarkingsystem.user.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色管理服务。
 *
 * 删除角色前会检查是否仍有用户绑定，避免产生"孤儿角色"。
 */
@Service
public class RoleService {

    private static final int STATUS_ENABLED = 1;

    private final RoleMapper roleMapper;

    private final UserRoleMapper userRoleMapper;

    public RoleService(RoleMapper roleMapper, UserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    /** 新增角色 */
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(RoleDTO dto) {
        assertRoleCodeUnique(dto.getRoleCode(), null);

        Role role = new Role();
        applyDto(role, dto);
        if (role.getStatus() == null) {
            role.setStatus(STATUS_ENABLED);
        }
        if (role.getSort() == null) {
            role.setSort(0);
        }

        roleMapper.insert(role);
        return role.getId();
    }

    /** 修改角色（roleCode 修改后同样保持全局唯一） */
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, RoleDTO dto) {
        requireRole(id);
        assertRoleCodeUnique(dto.getRoleCode(), id);

        Role update = new Role();
        update.setId(id);
        applyDto(update, dto);
        roleMapper.updateById(update);
    }

    /** 删除角色：仍被用户绑定时拒绝删除 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        requireRole(id);

        Long boundCount = userRoleMapper.selectCount(
                Wrappers.<UserRole>lambdaQuery().eq(UserRole::getRoleId, id));
        if (boundCount != null && boundCount > 0) {
            throw new BusinessException("该角色已分配给用户，请先解除关联再删除");
        }

        roleMapper.deleteById(id);
    }

    /** 角色详情 */
    public RoleVO getRole(Long id) {
        return toRoleVO(requireRole(id));
    }

    /** 角色列表，可按状态过滤，按 sort 升序 */
    public List<RoleVO> listRoles(Integer status) {
        return roleMapper.selectList(
                        Wrappers.<Role>lambdaQuery()
                                .eq(status != null, Role::getStatus, status)
                                .orderByAsc(Role::getSort)
                                .orderByAsc(Role::getId))
                .stream()
                .map(this::toRoleVO)
                .toList();
    }

    private void assertRoleCodeUnique(String roleCode, Long excludeId) {
        Long count = roleMapper.selectCount(
                Wrappers.<Role>lambdaQuery()
                        .eq(Role::getRoleCode, roleCode)
                        .ne(excludeId != null, Role::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("角色编码已存在");
        }
    }

    private Role requireRole(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private void applyDto(Role role, RoleDTO dto) {
        role.setRoleCode(dto.getRoleCode());
        role.setRoleName(dto.getRoleName());
        role.setDescription(dto.getDescription());
        role.setSort(dto.getSort());
        role.setStatus(dto.getStatus());
    }

    private RoleVO toRoleVO(Role role) {
        RoleVO roleVO = new RoleVO();
        roleVO.setId(role.getId());
        roleVO.setRoleCode(role.getRoleCode());
        roleVO.setRoleName(role.getRoleName());
        roleVO.setDescription(role.getDescription());
        roleVO.setSort(role.getSort());
        roleVO.setStatus(role.getStatus());
        roleVO.setCreateTime(role.getCreateTime());
        return roleVO;
    }
}
