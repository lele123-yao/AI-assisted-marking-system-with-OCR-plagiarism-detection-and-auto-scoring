package com.marking.aimarkingsystem.clazz.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 学生端「我的试卷」。 */
@Data
@Schema(description = "我的试卷")
public class StudentPaperVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long paperId;

    private String paperNo;

    private String paperTitle;

    private String courseName;

    /** 作答模式：1 线上，2 纸质 */
    private Integer answerMode;

    private BigDecimal totalScore;

    /** 考试时长（分钟） */
    private Integer duration;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 下发截止时间 */
    private LocalDateTime deadline;

    private LocalDateTime assignTime;

    /** 来源：CLASS 整班下发，STUDENT 指定学生 */
    private String source;

    /** 试卷状态：1 待考试，2 阅卷中 */
    private Integer paperStatus;

    /** 是否已提交 */
    private Boolean answered;

    /** 已提交时的答卷ID */
    private Long answerId;
}
