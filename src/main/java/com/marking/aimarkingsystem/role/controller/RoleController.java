package com.marking.aimarkingsystem.role.controller;

import com.marking.aimarkingsystem.common.annotation.OperationLog;
import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.role.dto.RoleDTO;
import com.marking.aimarkingsystem.role.service.RoleService;
import com.marking.aimarkingsystem.role.vo.RoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口。
 *
 * 权限说明同 UserController：当前用 ROLE_ADMIN 保护管理接口，
 * 后续可切换为 system:role:add 等权限码。
 */
@Tag(name = "角色管理", description = "角色的增删改查")
@RestController
@RequestMapping("/system/roles")
@Validated
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "新增角色", description = "角色编码全局唯一，编码用于程序判断，名称用于展示")
    @OperationLog(module = "角色管理", action = "新增角色")
    @RequiresPermission("ROLE_ADMIN")
    @PostMapping
    public Result<Long> createRole(@Valid @RequestBody RoleDTO dto) {
        return Result.success("新增成功", roleService.createRole(dto));
    }

    @Operation(summary = "修改角色", description = "修改角色编码时同样会校验唯一性")
    @OperationLog(module = "角色管理", action = "修改角色")
    @RequiresPermission("ROLE_ADMIN")
    @PutMapping("/{id}")
    public Result<Void> updateRole(
            @Parameter(description = "角色ID") @PathVariable Long id,
            @Valid @RequestBody RoleDTO dto) {
        roleService.updateRole(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除角色", description = "角色仍被用户绑定时拒绝删除，需先解除关联")
    @OperationLog(module = "角色管理", action = "删除角色")
    @RequiresPermission("ROLE_ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@Parameter(description = "角色ID") @PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.success();
    }

    @Operation(summary = "角色详情")
    @RequiresPermission("ROLE_ADMIN")
    @GetMapping("/{id}")
    public Result<RoleVO> getRole(@Parameter(description = "角色ID") @PathVariable Long id) {
        return Result.success(roleService.getRole(id));
    }

    @Operation(summary = "角色列表", description = "按 sort 升序返回；status 不传则返回全部角色")
    @RequiresPermission("ROLE_ADMIN")
    @GetMapping("/list")
    public Result<List<RoleVO>> listRoles(
            @Parameter(description = "状态：1 启用，0 禁用") @RequestParam(required = false) @Min(0) @Max(1) Integer status) {
        return Result.success(roleService.listRoles(status));
    }
}
