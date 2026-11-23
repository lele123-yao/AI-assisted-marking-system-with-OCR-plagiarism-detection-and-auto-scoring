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
 * 试卷下发实体，对应表 paper_assignment。
 *
 * 两种下发方式：
 * - classId 非空、studentId 为空：整班下发，班内学生实时生效；
 * - classId 为空、studentId 非空：指定学生下发。
 */
@Data
@TableName("paper_assignment")
public class PaperAssignment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 试卷ID，关联 exam_paper.id */
    private Long paperId;

    /** 整班下发时的班级ID */
    private Long classId;

    /** 指定学生下发时的学生ID */
    private Long studentId;

    /** 下发操作人ID */
    private Long teacherId;

    /** 下发时间 */
    private LocalDateTime assignTime;

    /** 截止时间，可空 */
    private LocalDateTime deadline;

    /** 状态：1 有效，0 撤销 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
