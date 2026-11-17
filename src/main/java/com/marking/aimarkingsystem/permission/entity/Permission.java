package com.marking.aimarkingsystem.permission.entity;

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
 * 权限实体，对应表 sys_permission。
 *
 * 说明：
 * - parentId 构成树形结构（0 为顶级），后续接口按此递归组装菜单树；
 * - permType 区分 1 菜单 / 2 按钮 / 3 API 接口，前端控制显隐、后端做 URL 鉴权都靠它；
 * - path + method 组合定位 API 类权限，阶段后续 Security 配置会用到。
 */
@Data
@TableName("sys_permission")
public class Permission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父级权限ID，0 表示顶级 */
    private Long parentId;

    /** 权限编码，唯一，如 system:user:add */
    private String permCode;

    /** 权限名称 */
    private String permName;

    /** 权限类型：1 菜单，2 按钮，3 API接口 */
    private Integer permType;

    /** 前端路由路径或后端 API 路径 */
    private String path;

    /** HTTP 方法（API 类权限使用） */
    private String method;

    /** 菜单图标 */
    private String icon;

    /** 排序号 */
    private Integer sort;

    /** 前端是否可见：1 可见，0 隐藏 */
    private Integer visible;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
