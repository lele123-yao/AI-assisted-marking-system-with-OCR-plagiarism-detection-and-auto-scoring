package com.marking.aimarkingsystem.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 新增用户请求 DTO（管理员创建账号时使用）。
 *
 * 说明：
 * - roleIds 为预留字段：管理员创建用户时可直接绑定角色，后续 UserService
 *   会同时写 sys_user 和 sys_user_role 两张表；
 * - phone 的正则允许空字符串，避免前端传 "" 被误判为格式错误（null 天然通过校验）。
 */
@Data
public class UserAddDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度需为 4-20 位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需为 6-20 位")
    private String password;

    @Size(max = 32, message = "昵称最长 32 位")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 绑定的角色ID集合，可为空表示暂不分配角色 */
    private List<Long> roleIds;
}
