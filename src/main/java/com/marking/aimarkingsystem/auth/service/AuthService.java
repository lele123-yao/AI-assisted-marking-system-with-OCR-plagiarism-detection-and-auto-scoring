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
import com.marking.aimarkingsystem.user.dto.RegisterDTO;
import com.marking.aimarkingsystem.user.entity.User;
import com.marking.aimarkingsystem.user.entity.UserRole;
import com.marking.aimarkingsystem.user.enums.IdentityEnum;
import com.marking.aimarkingsystem.user.mapper.UserMapper;
import com.marking.aimarkingsystem.user.mapper.UserRoleMapper;
import com.marking.aimarkingsystem.user.vo.UserVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        // 4. 查询角色，签发 token 并组装登录响应
        List<Role> roles = loadRoles(user.getId());
        LoginVO loginVO = issueToken(user, roles);
        log.info("用户登录成功: username={}, roles={}", user.getUsername(),
                roles.stream().map(Role::getRoleCode).toList());

        // 5. 更新最后登录时间（只更新该字段，避免覆盖其他数据）
        User loginRecord = new User();
        loginRecord.setId(user.getId());
        loginRecord.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(loginRecord);

        return loginVO;
    }

    /**
     * 师生自助注册，注册成功后直接签发 token（等同于注册即登录）。
     *
     * 业务流程：
     * 1. 用户名查重（包含逻辑删除账号，避免与唯一索引冲突）；
     * 2. 根据身份找到基础角色（TEACHER / STUDENT），角色必须已在 SQL 中初始化；
     * 3. 密码 BCrypt 加密后写入 sys_user，状态默认启用；
     * 4. 写入 sys_user_role 绑定基础角色；
     * 5. 复用登录的 token 签发逻辑，返回与登录完全一致的 LoginVO。
     *
     * 事务保证"用户 + 角色关联"要么都成功，要么都回滚。
     * 管理员角色不在可选身份中，防止自助注册提权。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        String username = dto.getUsername().trim();

        // 1. 用户名唯一性预校验
        if (userMapper.countByUsername(username) > 0) {
            throw new BusinessException("用户名已存在");
        }

        // 2. 密码重复校验：同一个密码只能被一个账号使用
        ensurePasswordUnused(dto.getPassword());

        // 3. 解析身份并查询对应的基础角色
        // 自助注册只允许学生；教师账号必须由管理员在「系统管理 → 用户管理」创建，避免越权提权
        IdentityEnum identity = IdentityEnum.fromCode(dto.getIdentity());
        if (identity != IdentityEnum.STUDENT) {
            throw new BusinessException("教师账号暂不支持自助注册，请联系管理员创建");
        }
        Role baseRole = roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getRoleCode, identity.getRoleCode())
                .eq(Role::getStatus, STATUS_ENABLED));
        if (baseRole == null) {
            throw new BusinessException("基础角色[" + identity.getRoleCode() + "]未初始化，请联系管理员");
        }

        // 4. 写入用户，密码 BCrypt 加密，状态默认启用
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        // 表中的 nickname 即真实姓名
        user.setNickname(dto.getRealName().trim());
        user.setStatus(STATUS_ENABLED);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发注册同名账号时，由数据库唯一索引兜底，仍转换为业务异常
            throw new BusinessException("用户名已存在");
        }

        // 5. 绑定基础角色
        UserRole relation = new UserRole();
        relation.setUserId(user.getId());
        relation.setRoleId(baseRole.getId());
        userRoleMapper.insert(relation);

        // 6. 签发 token，返回与登录接口一致的结构
        LoginVO loginVO = issueToken(user, List.of(baseRole));
        log.info("用户注册成功: username={}, identity={}", username, identity);
        return loginVO;
    }

    /**
     * 密码重复校验：同一个密码只允许一个账号使用。
     *
     * BCrypt 每次加密都会生成随机盐，同一明文得到的密文也不同，
     * 因此无法用 "WHERE password = ?" 判断，只能逐条用 matches 比对。
     * 注册与新增用户都是低频操作，这里按账号量线性校验；
     * 若后续账号规模变大，可增加一列密码指纹（如 SHA-256）做索引查询。
     */
    private void ensurePasswordUnused(String rawPassword) {
        List<User> users = userMapper.selectList(
                Wrappers.<User>lambdaQuery().select(User::getId, User::getPassword));
        boolean used = users.stream()
                .anyMatch(user -> user.getPassword() != null
                        && passwordEncoder.matches(rawPassword, user.getPassword()));
        if (used) {
            throw new BusinessException("该密码已被其他账号使用，请更换密码");
        }
    }

    /**
     * 签发 JWT 并写入 Redis（登录与注册共用）。
     *
     * 同一用户名重复签发会覆盖 Redis 中的旧 token，
     * 因此注册后直接把新 token 给前端，也遵循"一个账号仅一个有效会话"的规则。
     */
    private LoginVO issueToken(User user, List<Role> roles) {
        List<String> authorities = roles.stream()
                .map(role -> ROLE_PREFIX + role.getRoleCode())
                .toList();

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("authorities", authorities);

        String token = jwtUtil.generateToken(user.getUsername(), claims);
        redisUtil.set(RedisKeyConstant.LOGIN_TOKEN + user.getUsername(), token,
                jwtUtil.getExpiration(), TimeUnit.MILLISECONDS);

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
