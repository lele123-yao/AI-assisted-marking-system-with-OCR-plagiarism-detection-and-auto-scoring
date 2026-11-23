package com.marking.aimarkingsystem.clazz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 试卷下发请求：整班、指定学生可同时使用。 */
@Data
@Schema(description = "试卷下发请求")
public class PaperAssignDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "试卷ID")
    @NotNull(message = "请选择试卷")
    @Min(value = 1, message = "试卷ID不合法")
    private Long paperId;

    @Schema(description = "整班下发的班级ID，可空")
    @Min(value = 1, message = "班级ID不合法")
    private Long classId;

    @Schema(description = "额外指定下发的学生ID列表，可空")
    private List<Long> studentIds;

    @Schema(description = "截止时间，可空")
    private LocalDateTime deadline;
}
