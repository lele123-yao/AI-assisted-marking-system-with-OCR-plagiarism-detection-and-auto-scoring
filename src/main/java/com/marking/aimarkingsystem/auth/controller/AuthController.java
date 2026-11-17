package com.marking.aimarkingsystem.auth.controller;

import com.marking.aimarkingsystem.auth.service.AuthService;
import com.marking.aimarkingsystem.auth.vo.LoginVO;
import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.user.dto.LoginDTO;
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
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return Result.success("登录成功", authService.login(loginDTO));
    }
}
