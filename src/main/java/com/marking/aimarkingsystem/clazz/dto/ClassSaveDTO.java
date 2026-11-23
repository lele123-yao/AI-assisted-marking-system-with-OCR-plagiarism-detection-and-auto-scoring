package com.marking.aimarkingsystem.clazz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 班级新增/编辑请求。 */
@Data
@Schema(description = "班级保存请求")
public class ClassSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "班级编码", example = "CS-2023-1")
    @NotBlank(message = "班级编码不能为空")
    @Size(max = 32, message = "班级编码最长 32 位")
    private String classCode;

    @Schema(description = "班级名称", example = "计算机 2023 级 1 班")
    @NotBlank(message = "班级名称不能为空")
    @Size(max = 64, message = "班级名称最长 64 位")
    private String className;

    @Schema(description = "年级", example = "2023")
    @Size(max = 32, message = "年级最长 32 位")
    private String grade;

    /** 教师创建/编辑时由后端强制为当前用户；管理员可指定 */
    @Schema(description = "任课教师ID", example = "2")
    @Min(value = 1, message = "任课教师ID不合法")
    private Long teacherId;

    @Schema(description = "班级简介")
    @Size(max = 512, message = "班级简介最长 512 位")
    private String description;

    @Schema(description = "状态：1 正常，0 停用")
    @Min(value = 0, message = "状态不合法")
    @Max(value = 1, message = "状态不合法")
    private Integer status;
}
