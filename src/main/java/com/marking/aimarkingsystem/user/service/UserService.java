package com.marking.aimarkingsystem.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marking.aimarkingsystem.common.constant.RedisKeyConstant;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.PageResult;
import com.marking.aimarkingsystem.common.result.ResultCode;
import com.marking.aimarkingsystem.common.util.RedisUtil;
import com.marking.aimarkingsystem.role.entity.Role;
import com.marking.aimarkingsystem.role.mapper.RoleMapper;
import com.marking.aimarkingsystem.role.vo.RoleVO;
import com.marking.aimarkingsystem.user.dto.UserAddDTO;
import com.marking.aimarkingsystem.user.entity.User;
import com.marking.aimarkingsystem.user.entity.UserRole;
import com.marking.aimarkingsystem.user.mapper.UserMapper;
import com.marking.aimarkingsystem.user.mapper.UserRoleMapper;
import com.marking.aimarkingsystem.user.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户管理服务（不含登录，登录逻辑在 AuthService）。
 *
 * 数据权限说明：本阶段不做"教师只能查看自己学生"之类的行级过滤，
 * 相关扩展点保留在 PermissionAspect 与 MybatisPlusConfig 的注释中。
 */
@Service
public class UserService {

    private static final int STATUS_ENABLED = 1;

    private static final int STATUS_DISABLED = 0;

    private final UserMapper userMapper;

    private final UserRoleMapper userRoleMapper;

    private final RoleMapper roleMapper;

    private final PasswordEncoder passwordEncoder;

    private final RedisUtil redisUtil;

    public UserService(UserMapper userMapper,
                       UserRoleMapper userRoleMapper,
                       RoleMapper roleMapper,
                       PasswordEncoder passwordEncoder,
                       RedisUtil redisUtil) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.redisUtil = redisUtil;
    }

    /**
     * 新增用户。
     * 密码使用 BCrypt 加密后入库；roleIds 不为空时同时写入用户角色关联表。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long addUser(UserAddDTO dto) {
        Long exists = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException("用户名已存在");
        }

        // 密码重复校验：同一个密码只允许一个账号使用
        ensurePasswordUnused(dto.getPassword());

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setStatus(STATUS_ENABLED);
        userMapper.insert(user);

        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            bindRoles(user.getId(), dto.getRoleIds());
        }
        return user.getId();
    }

    /**
     * 密码重复校验：同一个密码只允许一个账号使用。
     *
     * BCrypt 带随机盐，同一明文每次加密结果都不同，无法用等值条件查询，
     * 因此逐条用 matches 比对。新增用户是低频操作，线性校验可以接受；
     * 账号规模变大后可增加密码指纹列（如 SHA-256）做索引查询。
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
     * 启用 / 禁用用户。
     * 禁用时同步删除 Redis 中的登录会话，使该用户已签发的 token 立即失效。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Integer status) {
        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new BusinessException("状态值只能为 0（禁用）或 1（启用）");
        }

        User user = requireUser(userId);
        User update = new User();
        update.setId(userId);
        update.setStatus(status);
        userMapper.updateById(update);

        if (status == STATUS_DISABLED) {
            redisUtil.delete(RedisKeyConstant.LOGIN_TOKEN + user.getUsername());
        }
    }

    /**
     * 重置密码。
     * 重置后清除 Redis 会话，强制用户使用新密码重新登录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String newPassword) {
        User user = requireUser(userId);

        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);

        redisUtil.delete(RedisKeyConstant.LOGIN_TOKEN + user.getUsername());
    }

    /**
     * 分页查询用户，支持按用户名模糊匹配、按状态过滤。
     */
    public PageResult<UserVO> pageUsers(Integer pageNum, Integer pageSize, String username, Integer status) {
        Page<User> page = new Page<>(pageNum, pageSize);
        Page<User> userPage = userMapper.selectPage(page,
                Wrappers.<User>lambdaQuery()
                        .like(StringUtils.hasText(username), User::getUsername, username)
                        .eq(status != null, User::getStatus, status)
                        .orderByDesc(User::getCreateTime));

        List<UserVO> records = userPage.getRecords().stream()
                .map(this::toUserVO)
                .toList();
        fillRoles(records);
        return PageResult.of(userPage, records);
    }

    /**
     * 给用户分配角色：整体覆盖，传空集合表示清空角色。
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        requireUser(userId);

        // 关联表使用物理删除，避免逻辑删除残留记录与唯一索引冲突
        userRoleMapper.physicalDeleteByUserId(userId);

        if (roleIds != null && !roleIds.isEmpty()) {
            bindRoles(userId, roleIds);
        }
    }

    /** 校验角色是否都存在，然后逐条写入关联表 */
    private void bindRoles(Long userId, List<Long> roleIds) {
        List<Long> distinctIds = roleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return;
        }

        List<Role> roles = roleMapper.selectBatchIds(distinctIds);
        if (roles.size() != distinctIds.size()) {
            throw new BusinessException("存在无效的角色ID");
        }

        for (Long roleId : distinctIds) {
            UserRole relation = new UserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
    }

    /** 批量填充分页结果中的角色信息，避免逐条查询 */
    private void fillRoles(List<UserVO> users) {
        if (users.isEmpty()) {
            return;
        }

        List<Long> userIds = users.stream().map(UserVO::getId).toList();
        List<UserRole> relations = userRoleMapper.selectList(
                Wrappers.<UserRole>lambdaQuery().in(UserRole::getUserId, userIds));
        if (relations.isEmpty()) {
            users.forEach(vo -> vo.setRoles(Collections.emptyList()));
            return;
        }

        List<Long> roleIds = relations.stream()
                .map(UserRole::getRoleId)
                .distinct()
                .toList();
        Map<Long, RoleVO> roleMap = roleMapper.selectBatchIds(roleIds).stream()
                .map(this::toRoleVO)
                .collect(Collectors.toMap(RoleVO::getId, Function.identity()));

        Map<Long, List<Long>> userRoleMap = relations.stream()
                .collect(Collectors.groupingBy(UserRole::getUserId,
                        Collectors.mapping(UserRole::getRoleId, Collectors.toList())));

        users.forEach(vo -> vo.setRoles(
                userRoleMap.getOrDefault(vo.getId(), Collections.emptyList()).stream()
                        .map(roleMap::get)
                        .filter(Objects::nonNull)
                        .toList()));
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private UserVO toUserVO(User user) {
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
        return userVO;
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
