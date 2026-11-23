package com.marking.aimarkingsystem.clazz.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 学生端「我的成绩」。 */
@Data
@Schema(description = "我的成绩")
public class StudentScoreVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long answerId;

    private Long paperId;

    private String paperNo;

    private String paperTitle;

    /** 试卷总分 */
    private BigDecimal paperTotalScore;

    /** AI 参考分 */
    private BigDecimal aiReferenceScore;

    /** 教师最终分（成绩以此为准） */
    private BigDecimal finalScore;

    /** 阅卷状态，取值见 AnswerStatusEnum */
    private Integer markingStatus;

    private LocalDateTime submitTime;

    private LocalDateTime markTime;
}
