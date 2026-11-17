package com.marking.aimarkingsystem.role.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色视图对象 VO。
 *
 * 说明：
 * - 角色表本身无敏感字段，VO 与实体字段接近，但仍独立一份：
 *   后续如需在 VO 中追加"该角色下的用户数量"等统计字段，不影响实体；
 * - 登录响应、用户详情、角色管理列表均复用此 VO。
 */
@Data
public class RoleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 角色编码，如 ADMIN / TEACHER / STUDENT */
    private String roleCode;

    /** 角色名称，如 系统管理员 */
    private String roleName;

    private String description;

    private Integer sort;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    private LocalDateTime createTime;
}
