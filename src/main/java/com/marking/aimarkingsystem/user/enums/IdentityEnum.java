package com.marking.aimarkingsystem.user.enums;

import com.marking.aimarkingsystem.common.exception.BusinessException;

/**
 * 自助注册身份枚举。
 *
 * 说明：
 * - 前端只传 TEACHER / STUDENT 两个值；
 * - 每个身份对应 sys_role 中的基础角色编码（role_code），
 *   注册成功后由 AuthService 自动绑定该角色，用户即可获得基础权限；
 * - 管理员角色不允许自助注册，只能由后台创建，避免越权提权。
 */
public enum IdentityEnum {

    /** 教师：绑定基础角色 TEACHER */
    TEACHER("TEACHER"),

    /** 学生：绑定基础角色 STUDENT */
    STUDENT("STUDENT");

    /** 对应的基础角色编码，与 sys_role.role_code 保持一致 */
    private final String roleCode;

    IdentityEnum(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleCode() {
        return roleCode;
    }

    /**
     * 将请求中的身份字符串转换为枚举。
     * DTO 上已有 @Pattern 做第一道校验，这里再兜底一次，
     * 保证 Service 被其他入口调用时也不会出现非法身份。
     */
    public static IdentityEnum fromCode(String code) {
        for (IdentityEnum identity : values()) {
            if (identity.name().equals(code)) {
                return identity;
            }
        }
        throw new BusinessException("身份只能为 TEACHER 或 STUDENT");
    }
}
