package com.marking.aimarkingsystem.role.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色实体，对应表 sys_role。
 *
 * 说明：
 * - 代码中做权限判断统一用 roleCode（稳定），roleName（中文名）只用于展示，允许修改；
 * - sort 用于后台角色管理页面的展示排序。
 */
@Data
@TableName("sys_role")
public class Role implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码，唯一，如 ADMIN / TEACHER / STUDENT */
    private String roleCode;

    /** 角色名称，如 系统管理员 */
    private String roleName;

    /** 角色描述 */
    private String description;

    /** 排序号 */
    private Integer sort;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
