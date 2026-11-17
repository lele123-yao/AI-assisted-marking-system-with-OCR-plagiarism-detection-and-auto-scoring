package com.marking.aimarkingsystem.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.auth.vo.LoginVO;
import com.marking.aimarkingsystem.common.constant.RedisKeyConstant;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.ResultCode;
import com.marking.aimarkingsystem.common.util.JwtUtil;
import com.marking.aimarkingsystem.common.util.RedisUtil;
import com.marking.aimarkingsystem.role.entity.Role;
import com.marking.aimarkingsystem.role.mapper.RoleMapper;
import com.marking.aimarkingsystem.role.vo.RoleVO;
import com.marking.aimarkingsystem.user.dto.LoginDTO;
import com.marking.aimarkingsystem.user.entity.User;
import com.marking.aimarkingsystem.user.entity.UserRole;
import com.marking.aimarkingsystem.user.mapper.UserMapper;
import com.marking.aimarkingsystem.user.mapper.UserRoleMapper;
import com.marking.aimarkingsystem.user.vo.UserVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 登录服务（仅登录，不含用户/角色 CRUD）。
 *
 * 职责：
 * 1. 校验账号是否存在、是否被禁用、密码是否正确；
 * 2. 查询用户角色，作为 JWT 中的权限声明；
 * 3. 签发 JWT；
 * 4. 将 token 写入 Redis（login:token:{username}），覆盖旧值 → 后登录挤掉先登录；
 * 5. 更新最后登录时间并返回登录信息。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 角色权限前缀，Spring Security 的 hasRole 约定为 ROLE_ 前缀 */
    private static final String ROLE_PREFIX = "ROLE_";

    /** 账号启用状态 */
    private static final int STATUS_ENABLED = 1;

    private final UserMapper userMapper;

    private final UserRoleMapper userRoleMapper;

    private final RoleMapper roleMapper;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil;

    private final RedisUtil redisUtil;

    public AuthService(UserMapper userMapper,
                       UserRoleMapper userRoleMapper,
                       RoleMapper roleMapper,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       RedisUtil redisUtil) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
    }

    /**
     * 账号密码登录。
     *
     * 用户名不存在与密码错误统一提示"用户名或密码错误"，
     * 避免通过错误信息枚举出系统里存在哪些账号。
     */
    public LoginVO login(LoginDTO loginDTO) {
        // 1. 查询用户（逻辑删除条件由 MyBatis-Plus 自动追加）
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, loginDTO.getUsername()));
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 2. BCrypt 校验：matches(明文, 数据库密文)
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 3. 账号状态校验
        if (!Objects.equals(user.getStatus(), STATUS_ENABLED)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        // 4. 查询角色，作为 JWT 的权限声明
        List<Role> roles = loadRoles(user.getId());
        List<String> authorities = roles.stream()
                .map(role -> ROLE_PREFIX + role.getRoleCode())
                .toList();

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("authorities", authorities);

        // 5. 签发 token
        String token = jwtUtil.generateToken(user.getUsername(), claims);

        // 6. 写入 Redis：同一 username 直接覆盖旧 token，旧 token 立即失效
        redisUtil.set(RedisKeyConstant.LOGIN_TOKEN + user.getUsername(), token,
                jwtUtil.getExpiration(), TimeUnit.MILLISECONDS);
        log.info("用户登录成功: username={}, roles={}", user.getUsername(), authorities);

        // 7. 更新最后登录时间（只更新该字段，避免覆盖其他数据）
        User loginRecord = new User();
        loginRecord.setId(user.getId());
        loginRecord.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(loginRecord);

        return buildLoginVO(user, roles, token);
    }

    /** 查询用户有效角色（先查关联表，再批量查角色表，避免多表联查） */
    private List<Role> loadRoles(Long userId) {
        List<UserRole> relations = userRoleMapper.selectList(
                Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, userId));
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> roleIds = relations.stream()
                .map(UserRole::getRoleId)
                .distinct()
                .toList();

        return roleMapper.selectBatchIds(roleIds).stream()
                .filter(role -> Objects.equals(role.getStatus(), STATUS_ENABLED))
                .toList();
    }

    private LoginVO buildLoginVO(User user, List<Role> roles, String token) {
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUsername(user.getUsername());
        userVO.setNickname(user.getNickname());
        userVO.setEmail(user.getEmail());
        userVO.setPhone(user.getPhone());
        userVO.setAvatar(user.getAvatar());
        userVO.setStatus(user.getStatus());
        userVO.setLastLoginTime(user.getLastLoginTime());
        userVO.setCreateTime(user.getCreateTime());
        userVO.setRoles(roles.stream().map(this::toRoleVO).toList());

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setTokenType("Bearer");
        loginVO.setExpiresIn(jwtUtil.getExpiration() / 1000);
        loginVO.setUser(userVO);
        return loginVO;
    }

    private RoleVO toRoleVO(Role role) {
        RoleVO roleVO = new RoleVO();
        roleVO.setId(role.getId());
        roleVO.setRoleCode(role.getRoleCode());
        roleVO.setRoleName(role.getRoleName());
        roleVO.setDescription(role.getDescription());
        roleVO.setSort(role.getSort());
        roleVO.setStatus(role.getStatus());
        roleVO.setCreateTime(role.getCreateTime());
        return roleVO;
    }
}
