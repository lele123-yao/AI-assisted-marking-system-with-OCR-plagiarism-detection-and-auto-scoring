package com.marking.aimarkingsystem.clazz.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 班级列表/下拉项。 */
@Data
@Schema(description = "班级信息")
public class ClassVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String classCode;

    private String className;

    private String grade;

    private Long teacherId;

    /** 任课教师姓名（昵称优先，回退用户名） */
    private String teacherName;

    private String description;

    private Integer status;

    /** 班级学生数 */
    private Long studentCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
