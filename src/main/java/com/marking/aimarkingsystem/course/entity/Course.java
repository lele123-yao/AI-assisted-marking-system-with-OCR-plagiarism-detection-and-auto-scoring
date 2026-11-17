package com.marking.aimarkingsystem.course.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程实体，对应表 course。
 *
 * 约定：
 * - courseCode 是业务编码（如 CS101），唯一性由 Service 层校验（逻辑删除场景下不加数据库唯一索引）；
 * - teacherId 关联 sys_user.id，是后续"教师数据隔离"的过滤条件，本阶段只存值不做校验逻辑；
 * - 学生选课关系（学生列表）属于后续阶段，不在本实体扩展，避免提前写死业务。
 */
@Data
@TableName("course")
public class Course implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程编码，业务唯一，如 CS101 */
    private String courseCode;

    /** 课程名称 */
    private String courseName;

    /** 任课教师ID，关联 sys_user.id */
    private Long teacherId;

    /** 开课学期，如 2026-2027-1 */
    private String term;

    /** 学分 */
    private BigDecimal credit;

    /** 课程简介 */
    private String description;

    /** 状态：1 正常，0 停用 */
    private Integer status;

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
