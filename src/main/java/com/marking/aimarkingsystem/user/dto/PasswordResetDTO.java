package com.marking.aimarkingsystem.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 密码重置 DTO。
 *
 * 管理员重置密码后，该用户的 Redis 登录会话会被清除，必须重新登录。
 */
@Data
@Schema(description = "密码重置请求")
public class PasswordResetDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "新密码", example = "newPass123")
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需为 6-20 位")
    private String newPassword;
}
