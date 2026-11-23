package com.marking.aimarkingsystem.clazz.entity;

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
 * 班级实体，对应表 class_info。
 *
 * 约定：一个班一位任课教师（teacherId 关联 sys_user.id），
 * classCode 业务唯一，由 Service 层校验（逻辑删除场景下不加数据库唯一索引）。
 */
@Data
@TableName("class_info")
public class ClassInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 班级编码，业务唯一，如 CS-2023-1 */
    private String classCode;

    /** 班级名称 */
    private String className;

    /** 年级，如 2023 */
    private String grade;

    /** 任课教师ID，关联 sys_user.id */
    private Long teacherId;

    /** 班级简介 */
    private String description;

    /** 状态：1 正常，0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
