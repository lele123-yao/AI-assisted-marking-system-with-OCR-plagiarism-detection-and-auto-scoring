package com.marking.aimarkingsystem.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体，对应表 sys_user。
 *
 * 约定：
 * - 密码字段只在认证流程内部使用，@JsonIgnore 保证任何接口误把 User 返回给前端时不泄露密文；
 * - createTime/updateTime 由 MyMetaObjectHandler 自动填充，Service 层不需要手动赋值；
 * - delFlag 配合全局配置 logic-delete-field，调用 removeById 会自动变成 UPDATE 逻辑删除。
 */
@Data
@TableName("sys_user")
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增，对应 yml 中 id-type: auto */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号，唯一 */
    private String username;

    /** BCrypt 密文，永不对外返回 */
    @JsonIgnore
    private String password;

    /** 昵称/真实姓名 */
    private String nickname;

    /** 邮箱（预留：找回密码、通知） */
    private String email;

    /** 手机号（预留） */
    private String phone;

    /** 头像地址（预留） */
    private String avatar;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入与更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 未删除，1 已删除 */
    @TableLogic
    private Integer delFlag;
}
