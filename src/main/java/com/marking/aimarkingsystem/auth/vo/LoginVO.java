package com.marking.aimarkingsystem.auth.vo;

import com.marking.aimarkingsystem.user.vo.UserVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录响应 VO。
 *
 * 前端拿到 token 后，后续请求统一放在请求头：
 * Authorization: Bearer {token}
 */
@Data
@Schema(description = "登录响应")
public class LoginVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "JWT 令牌", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.xxxx")
    private String token;

    @Schema(description = "令牌类型，固定为 Bearer", example = "Bearer")
    private String tokenType;

    @Schema(description = "有效期（秒）", example = "1800")
    private Long expiresIn;

    @Schema(description = "登录用户信息（含角色）")
    private UserVO user;
}
