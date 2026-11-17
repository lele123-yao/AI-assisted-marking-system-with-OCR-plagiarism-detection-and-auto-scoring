package com.marking.aimarkingsystem.user.entity;

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
 * 用户-角色关联实体，对应表 sys_user_role。
 *
 * 说明：
 * - MyBatis-Plus 不支持多表级联，查询用户角色需要 Service 层先查本表再查 role 表；
 * - user_id + role_id 在数据库层有唯一索引，重复绑定会被数据库拒绝。
 */
@Data
@TableName("sys_user_role")
public class UserRole implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID，对应 sys_user.id */
    private Long userId;

    /** 角色ID，对应 sys_role.id */
    private Long roleId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
