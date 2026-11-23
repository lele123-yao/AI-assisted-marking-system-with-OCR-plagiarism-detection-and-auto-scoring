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
 * 班级学生关系实体，对应表 class_student。
 *
 * 一名学生同时只能在一个班，由 ClassService 在加入时校验。
 */
@Data
@TableName("class_student")
public class ClassStudent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 班级ID，关联 class_info.id */
    private Long classId;

    /** 学生ID，关联 sys_user.id */
    private Long studentId;

    /** 状态：1 在班，0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：移出班级即置为 1 */
    @TableLogic
    private Integer delFlag;
}
