package com.marking.aimarkingsystem.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 课程新增 / 编辑 DTO（编辑时 id 通过路径参数传递，body 不含 id）。
 *
 * 说明：
 * - teacherId 由前端从当前登录教师或教师下拉框选择，Service 层会校验该用户存在；
 * - status 可空：新增时 Service 默认置 1（正常），不强制前端传。
 */
@Data
@Schema(description = "课程保存请求（新增/编辑共用）")
public class CourseSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "课程编码（唯一）", example = "CS101")
    @NotBlank(message = "课程编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,32}$", message = "课程编码只能由字母、数字、下划线、中划线组成，长度 2-32 位")
    private String courseCode;

    @Schema(description = "课程名称", example = "数据结构与算法")
    @NotBlank(message = "课程名称不能为空")
    @Size(max = 128, message = "课程名称最长 128 位")
    private String courseName;

    @Schema(description = "任课教师ID（sys_user.id）", example = "1")
    @NotNull(message = "任课教师不能为空")
    @Min(value = 1, message = "教师ID不合法")
    private Long teacherId;

    @Schema(description = "开课学期，如 2026-2027-1", example = "2026-2027-1")
    @Size(max = 32, message = "学期最长 32 位")
    private String term;

    @Schema(description = "学分", example = "4.0")
    @DecimalMin(value = "0.0", message = "学分不能小于 0")
    @DecimalMax(value = "99.9", message = "学分不能超过 99.9")
    private BigDecimal credit;

    @Schema(description = "课程简介", example = "线性表、树、图与常用排序算法")
    @Size(max = 512, message = "课程简介最长 512 位")
    private String description;

    @Schema(description = "状态：1 正常，0 停用；不传新增时默认 1", example = "1")
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
