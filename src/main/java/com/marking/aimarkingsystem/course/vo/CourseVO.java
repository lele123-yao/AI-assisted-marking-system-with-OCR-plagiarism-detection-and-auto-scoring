package com.marking.aimarkingsystem.course.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程视图对象 VO。
 *
 * 说明：
 * - VO 是"返回给前端的视图"，与实体解耦：后续若课程需要展示教师姓名，
 *   直接在此加 teacherName 字段，由 Service 关联 sys_user 填充，不影响实体；
 * - 本阶段预留 teacherName 字段，列表页可直接展示，Service 未填充时为 null。
 */
@Data
@Schema(description = "课程信息")
public class CourseVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "课程ID", example = "1")
    private Long id;

    @Schema(description = "课程编码", example = "CS101")
    private String courseCode;

    @Schema(description = "课程名称", example = "数据结构与算法")
    private String courseName;

    @Schema(description = "任课教师ID", example = "1")
    private Long teacherId;

    @Schema(description = "任课教师姓名（Service 关联用户表填充，可能为 null）", example = "张老师")
    private String teacherName;

    @Schema(description = "开课学期", example = "2026-2027-1")
    private String term;

    @Schema(description = "学分", example = "4.0")
    private BigDecimal credit;

    @Schema(description = "课程简介")
    private String description;

    @Schema(description = "状态：1 正常，0 停用", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
