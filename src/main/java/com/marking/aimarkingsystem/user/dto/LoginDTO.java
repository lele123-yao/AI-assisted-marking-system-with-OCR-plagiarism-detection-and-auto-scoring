package com.marking.aimarkingsystem.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录请求 DTO。
 *
 * 说明：
 * - DTO 只做"请求入参"的载体，与实体解耦：登录只需要账号密码，
 *   绝不允许前端直接传一个 User 实体进来（防止越权改 status、delFlag 等字段）；
 * - 校验注解配合 Controller 上的 @Valid 使用，校验失败由全局异常处理器统一返回 400。
 */
@Data
@Schema(description = "登录请求")
public class LoginDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户名", example = "admin")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度需为 4-20 位")
    private String username;

    @Schema(description = "密码", example = "admin123")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需为 6-20 位")
    private String password;
}
