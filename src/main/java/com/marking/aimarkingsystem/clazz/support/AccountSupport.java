package com.marking.aimarkingsystem.clazz.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.ResultCode;
import com.marking.aimarkingsystem.common.util.CurrentUserUtil;
import com.marking.aimarkingsystem.user.entity.User;
import com.marking.aimarkingsystem.user.mapper.UserMapper;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 当前登录账号上下文：把"用户名 + 角色"两类判断集中到一处。
 *
 * 角色直接读 JWT 写入 SecurityContext 的 authorities（ROLE_ADMIN 等），不查库；
 * 用户 id 需要查 sys_user，只在真正需要时调用。
 */
@Component
public class AccountSupport {

    private final UserMapper userMapper;

    public AccountSupport(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 当前登录用户，未登录或账号不存在抛 401。 */
    public User requireCurrentUser() {
        String username = CurrentUserUtil.requireUsername();
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录账号不存在，请重新登录");
        }
        return user;
    }

    public Long requireCurrentUserId() {
        return requireCurrentUser().getId();
    }

    public boolean isAdmin() {
        return hasAuthority("ROLE_ADMIN");
    }

    public boolean isTeacher() {
        return hasAuthority("ROLE_TEACHER");
    }

    public boolean isStudent() {
        return hasAuthority("ROLE_STUDENT");
    }

    private boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
