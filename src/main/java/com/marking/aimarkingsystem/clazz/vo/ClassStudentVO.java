package com.marking.aimarkingsystem.clazz.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 班级学生 / 可选学生项。 */
@Data
@Schema(description = "班级学生")
public class ClassStudentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 班级学生关系ID，可选学生列表为空 */
    private Long id;

    private Long studentId;

    /** 登录账号（学号） */
    private String username;

    /** 真实姓名 */
    private String realName;

    private Integer status;

    /** 加入班级时间 */
    private LocalDateTime joinTime;
}
