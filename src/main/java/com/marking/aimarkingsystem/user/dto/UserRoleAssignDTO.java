package com.marking.aimarkingsystem.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 用户角色分配 DTO。
 *
 * 传空数组表示清空该用户的全部角色；传入角色 ID 会整体覆盖原有角色。
 */
@Data
@Schema(description = "用户角色分配请求")
public class UserRoleAssignDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "角色ID集合，传空数组表示清空角色", example = "[1, 2]")
    @NotNull(message = "角色ID集合不能为 null")
    private List<Long> roleIds;
}
