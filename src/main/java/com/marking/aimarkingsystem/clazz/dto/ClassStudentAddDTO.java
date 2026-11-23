package com.marking.aimarkingsystem.clazz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 向班级添加学生请求（支持批量）。 */
@Data
@Schema(description = "添加班级学生请求")
public class ClassStudentAddDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "学生用户ID列表")
    @NotEmpty(message = "请至少选择一名学生")
    private List<@NotNull(message = "学生ID不能为空") Long> studentIds;
}
