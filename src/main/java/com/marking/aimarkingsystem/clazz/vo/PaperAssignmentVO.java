package com.marking.aimarkingsystem.clazz.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 试卷下发记录（教师端查看）。 */
@Data
@Schema(description = "试卷下发记录")
public class PaperAssignmentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long paperId;

    private Long classId;

    /** 班级名称（整班下发时有值） */
    private String className;

    private Long studentId;

    /** 学生姓名/账号（指定学生时有值） */
    private String studentName;

    /** 目标描述：班级名 或 学生姓名 */
    private String targetName;

    private LocalDateTime assignTime;

    private LocalDateTime deadline;

    private Integer status;
}
