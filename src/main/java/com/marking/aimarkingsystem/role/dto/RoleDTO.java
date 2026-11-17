package com.marking.aimarkingsystem.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色新增 / 修改 DTO。
 *
 * roleCode 是程序判断权限用的稳定标识，不允许包含中文和空格；
 * roleName 只用于展示，可以随意修改。
 */
@Data
@Schema(description = "角色请求")
public class RoleDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "角色编码（唯一）", example = "TEACHER")
    @NotBlank(message = "角色编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{2,32}$", message = "角色编码只能由字母、数字、下划线组成，长度 2-32 位")
    private String roleCode;

    @Schema(description = "角色名称", example = "任课教师")
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称最长 64 位")
    private String roleName;

    @Schema(description = "角色描述", example = "负责所授课程的试卷发布与成绩查看")
    @Size(max = 256, message = "角色描述最长 256 位")
    private String description;

    @Schema(description = "排序号，升序展示", example = "2")
    @Min(value = 0, message = "排序号不能小于 0")
    private Integer sort;

    @Schema(description = "状态：1 启用，0 禁用", example = "1")
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
