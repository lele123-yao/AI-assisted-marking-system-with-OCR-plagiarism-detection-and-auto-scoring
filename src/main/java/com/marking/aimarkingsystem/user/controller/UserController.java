package com.marking.aimarkingsystem.user.controller;

import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.result.PageResult;
import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.user.dto.PasswordResetDTO;
import com.marking.aimarkingsystem.user.dto.UserAddDTO;
import com.marking.aimarkingsystem.user.dto.UserRoleAssignDTO;
import com.marking.aimarkingsystem.user.service.UserService;
import com.marking.aimarkingsystem.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口。
 *
 * 权限说明：当前阶段 token 中的权限只有角色（ROLE_ADMIN），
 * 因此这里用 @RequiresPermission("ROLE_ADMIN") 做管理员校验；
 * 后续实现 sys_role_permission 后，可改为 system:user:add 这类权限码，无需改动切面。
 *
 * 数据权限（教师只能管理自己课程下的学生）保持占位，本阶段不实现。
 */
@Tag(name = "用户管理", description = "用户新增、启停、密码重置、分页查询与角色分配")
@RestController
@RequestMapping("/system/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "新增用户",
            description = "密码使用 BCrypt 加密后入库；可通过 roleIds 同时绑定角色，需要管理员权限")
    @RequiresPermission("ROLE_ADMIN")
    @PostMapping
    public Result<Long> addUser(@Valid @RequestBody UserAddDTO dto) {
        return Result.success("新增成功", userService.addUser(dto));
    }

    @Operation(summary = "分页查询用户",
            description = "支持用户名模糊匹配、按账号状态过滤；返回结果中已填充每个用户的角色")
    @RequiresPermission("ROLE_ADMIN")
    @GetMapping("/page")
    public Result<PageResult<UserVO>> pageUsers(
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页条数，最大 100") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @Parameter(description = "用户名，模糊匹配") @RequestParam(required = false) String username,
            @Parameter(description = "状态：1 启用，0 禁用") @RequestParam(required = false) @Min(0) @Max(1) Integer status) {
        return Result.success(userService.pageUsers(pageNum, pageSize, username, status));
    }

    @Operation(summary = "启用/禁用用户",
            description = "禁用会同时清除该用户在 Redis 中的登录会话，已签发的 token 立即失效")
    @RequiresPermission("ROLE_ADMIN")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @Parameter(description = "状态：1 启用，0 禁用") @RequestParam @Min(0) @Max(1) Integer status) {
        userService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "重置密码",
            description = "重置后清除该用户登录会话，用户需使用新密码重新登录")
    @RequiresPermission("ROLE_ADMIN")
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @Valid @RequestBody PasswordResetDTO dto) {
        userService.resetPassword(id, dto.getNewPassword());
        return Result.success();
    }

    @Operation(summary = "给用户分配角色",
            description = "整体覆盖原有角色：传空数组表示清空该用户的全部角色")
    @RequiresPermission("ROLE_ADMIN")
    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @Valid @RequestBody UserRoleAssignDTO dto) {
        userService.assignRoles(id, dto.getRoleIds());
        return Result.success();
    }
}
