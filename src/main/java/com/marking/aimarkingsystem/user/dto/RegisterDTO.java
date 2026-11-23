package com.marking.aimarkingsystem.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 自助注册请求 DTO。
 *
 * 说明：
 * - 只暴露注册必需的四个字段，roleIds、status 等敏感字段不允许前端传入，
 *   角色由后端根据 identity 自动绑定，防止用户自助注册管理员；
 * - identity 用 String 接收并在 @Pattern 中限制取值，非法值会由全局异常处理器
 *   统一返回 400，提示比 Jackson 枚举转换失败更友好；
 * - realName 最终写入 sys_user.nickname（表中的昵称字段）。
 */
@Data
@Schema(description = "自助注册请求")
public class RegisterDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户名", example = "student01")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度需为 4-20 位")
    private String username;

    @Schema(description = "密码", example = "123456")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需为 6-20 位")
    private String password;

    @Schema(description = "真实姓名", example = "张三")
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 32, message = "真实姓名最长 32 位")
    private String realName;

    @Schema(description = "身份：自助注册只允许 STUDENT", example = "STUDENT")
    @NotBlank(message = "身份不能为空")
    @Pattern(regexp = "STUDENT", message = "自助注册只能选择学生身份，教师账号请联系管理员创建")
    private String identity;
}
