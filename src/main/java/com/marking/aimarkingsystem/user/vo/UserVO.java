package com.marking.aimarkingsystem.user.vo;

import com.marking.aimarkingsystem.role.vo.RoleVO;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图对象 VO。
 *
 * 说明：
 * - VO 是"返回给前端的视图"，刻意不包含 password，从类型层面杜绝密文外泄，
 *   不依赖开发者记得手动置空；
 * - roles 为预留字段：登录成功/查询用户详情时填充用户绑定的角色，
 *   单表查询场景下保持 null 即可。
 */
@Data
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private String avatar;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    /** 用户绑定的角色列表（关联查询时填充，可为空） */
    private List<RoleVO> roles;
}
