package com.marking.aimarkingsystem.auth.controller;

import com.marking.aimarkingsystem.auth.service.AuthService;
import com.marking.aimarkingsystem.auth.vo.LoginVO;
import com.marking.aimarkingsystem.common.annotation.OperationLog;
import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.user.dto.LoginDTO;
import com.marking.aimarkingsystem.user.dto.RegisterDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（本阶段只有登录）。
 *
 * 该路径 /auth/login 已在 SecurityConfig 白名单中放行，无需携带 token。
 */
@Tag(name = "认证管理", description = "登录相关接口")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 账号密码登录。
     *
     * 登录成功后返回 JWT；同一账号再次登录会使之前签发的 token 立即失效。
     */
    @Operation(summary = "账号密码登录",
            description = "校验 BCrypt 密码后签发 JWT，同一账号后登录会挤掉先登录的 token，有效期 30 分钟")
    @OperationLog(module = "认证管理", action = "账号密码登录", recordResult = false)
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return Result.success("登录成功", authService.login(loginDTO));
    }

    /**
     * 师生自助注册。
     *
     * 匿名接口，无需 token；注册成功后自动绑定基础角色并直接返回 token，
     * 响应结构与登录接口完全一致，前端拿到后可直接进入系统。
     * 管理员角色不允许自助注册。
     */
    @Operation(summary = "自助注册",
            description = "学生 / 教师注册，自动绑定 TEACHER 或 STUDENT 基础角色，注册成功直接返回 token")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO registerDTO) {
        return Result.success("注册成功", authService.register(registerDTO));
    }
}
