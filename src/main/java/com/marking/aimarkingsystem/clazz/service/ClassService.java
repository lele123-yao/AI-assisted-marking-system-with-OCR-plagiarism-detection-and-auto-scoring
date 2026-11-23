package com.marking.aimarkingsystem.clazz.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marking.aimarkingsystem.clazz.dto.ClassSaveDTO;
import com.marking.aimarkingsystem.clazz.entity.ClassInfo;
import com.marking.aimarkingsystem.clazz.entity.ClassStudent;
import com.marking.aimarkingsystem.clazz.mapper.ClassInfoMapper;
import com.marking.aimarkingsystem.clazz.mapper.ClassStudentMapper;
import com.marking.aimarkingsystem.clazz.support.AccountSupport;
import com.marking.aimarkingsystem.clazz.vo.ClassStudentVO;
import com.marking.aimarkingsystem.clazz.vo.ClassVO;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.PageResult;
import com.marking.aimarkingsystem.common.result.ResultCode;
import com.marking.aimarkingsystem.role.entity.Role;
import com.marking.aimarkingsystem.role.mapper.RoleMapper;
import com.marking.aimarkingsystem.user.entity.User;
import com.marking.aimarkingsystem.user.entity.UserRole;
import com.marking.aimarkingsystem.user.mapper.UserMapper;
import com.marking.aimarkingsystem.user.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 班级服务：班级 CRUD 与班级学生管理。
 *
 * 数据权限：
 * - ADMIN 可管理全部班级；
 * - TEACHER 只能查看/维护 teacherId = 自己的班级；
 * - 只能把拥有 STUDENT 角色、且当前不在任何班的学生加入班级。
 */
@Service
public class ClassService {

    private static final int STATUS_NORMAL = 1;

    private static final String ROLE_STUDENT = "STUDENT";

    /** 可选学生列表最大返回条数，避免一次拉全量用户 */
    private static final int STUDENT_OPTION_LIMIT = 200;

    private final ClassInfoMapper classInfoMapper;

    private final ClassStudentMapper classStudentMapper;

    private final UserMapper userMapper;

    private final UserRoleMapper userRoleMapper;

    private final RoleMapper roleMapper;

    private final AccountSupport accountSupport;

    public ClassService(ClassInfoMapper classInfoMapper,
                        ClassStudentMapper classStudentMapper,
                        UserMapper userMapper,
                        UserRoleMapper userRoleMapper,
                        RoleMapper roleMapper,
                        AccountSupport accountSupport) {
        this.classInfoMapper = classInfoMapper;
        this.classStudentMapper = classStudentMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.accountSupport = accountSupport;
    }

    /** 分页查询班级：教师只看自己的班级。 */
    public PageResult<ClassVO> pageClasses(Integer pageNum, Integer pageSize,
                                           String keyword, Integer status) {
        Long teacherId = accountSupport.isTeacher() && !accountSupport.isAdmin()
                ? accountSupport.requireCurrentUserId() : null;

        Page<ClassInfo> page = new Page<>(pageNum, pageSize);
        Page<ClassInfo> classPage = classInfoMapper.selectPage(page,
                Wrappers.<ClassInfo>lambdaQuery()
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(ClassInfo::getClassName, keyword)
                                .or()
                                .like(ClassInfo::getClassCode, keyword))
                        .eq(teacherId != null, ClassInfo::getTeacherId, teacherId)
                        .eq(status != null, ClassInfo::getStatus, status)
                        .orderByDesc(ClassInfo::getCreateTime));

        List<ClassVO> records = classPage.getRecords().stream()
                .map(this::toClassVO)
                .toList();
        fillTeacherNames(records);
        fillStudentCounts(records);
        return PageResult.of(classPage, records);
    }

    /** 新增班级。 */
    @Transactional(rollbackFor = Exception.class)
    public Long addClass(ClassSaveDTO dto) {
        assertClassCodeUnique(dto.getClassCode(), null);

        ClassInfo clazz = new ClassInfo();
        clazz.setClassCode(dto.getClassCode().trim());
        clazz.setClassName(dto.getClassName().trim());
        clazz.setGrade(dto.getGrade());
        clazz.setTeacherId(resolveTeacherId(dto.getTeacherId()));
        clazz.setDescription(dto.getDescription());
        clazz.setStatus(dto.getStatus() == null ? STATUS_NORMAL : dto.getStatus());
        classInfoMapper.insert(clazz);
        return clazz.getId();
    }

    /** 编辑班级。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateClass(Long id, ClassSaveDTO dto) {
        ClassInfo exists = requireOwnedClass(id);
        assertClassCodeUnique(dto.getClassCode(), id);

        ClassInfo update = new ClassInfo();
        update.setId(id);
        update.setClassCode(dto.getClassCode().trim());
        update.setClassName(dto.getClassName().trim());
        update.setGrade(dto.getGrade());
        update.setTeacherId(resolveTeacherId(dto.getTeacherId() == null
                ? exists.getTeacherId() : dto.getTeacherId()));
        update.setDescription(dto.getDescription());
        update.setStatus(dto.getStatus() == null ? exists.getStatus() : dto.getStatus());
        classInfoMapper.updateById(update);
    }

    /** 删除班级：同时逻辑删除班级学生关系。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteClass(Long id) {
        requireOwnedClass(id);
        classStudentMapper.delete(Wrappers.<ClassStudent>lambdaQuery()
                .eq(ClassStudent::getClassId, id));
        classInfoMapper.deleteById(id);
    }

    /** 班级下拉选项。 */
    public List<ClassVO> listClassOptions() {
        Long teacherId = accountSupport.isTeacher() && !accountSupport.isAdmin()
                ? accountSupport.requireCurrentUserId() : null;
        List<ClassInfo> list = classInfoMapper.selectList(
                Wrappers.<ClassInfo>lambdaQuery()
                        .eq(ClassInfo::getStatus, STATUS_NORMAL)
                        .eq(teacherId != null, ClassInfo::getTeacherId, teacherId)
                        .orderByAsc(ClassInfo::getClassName));
        return list.stream().map(this::toClassVO).toList();
    }

    /** 班级内学生列表。 */
    public List<ClassStudentVO> listStudents(Long classId) {
        requireOwnedClass(classId);
        List<ClassStudent> relations = classStudentMapper.selectList(
                Wrappers.<ClassStudent>lambdaQuery()
                        .eq(ClassStudent::getClassId, classId)
                        .orderByDesc(ClassStudent::getCreateTime));
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, User> userMap = userMapper.selectBatchIds(
                        relations.stream().map(ClassStudent::getStudentId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        return relations.stream().map(relation -> {
            ClassStudentVO vo = new ClassStudentVO();
            vo.setId(relation.getId());
            vo.setStudentId(relation.getStudentId());
            vo.setStatus(relation.getStatus());
            vo.setJoinTime(relation.getCreateTime());
            User user = userMap.get(relation.getStudentId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setRealName(displayName(user));
            }
            return vo;
        }).toList();
    }

    /** 可加入班级的学生：拥有 STUDENT 角色且当前不在任何班。 */
    public List<ClassStudentVO> listAvailableStudents(String keyword) {
        List<Long> studentIds = listStudentRoleUserIds();
        if (studentIds.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> occupied = classStudentMapper.selectList(
                        Wrappers.<ClassStudent>lambdaQuery()
                                .in(ClassStudent::getStudentId, studentIds))
                .stream()
                .map(ClassStudent::getStudentId)
                .collect(Collectors.toSet());

        List<Long> available = studentIds.stream()
                .filter(id -> !occupied.contains(id))
                .toList();
        return toStudentVOs(available, keyword);
    }

    /** 全部学生账号（供「额外指定学生」搜索，含已在班学生）。 */
    public List<ClassStudentVO> listAllStudents(String keyword) {
        return toStudentVOs(listStudentRoleUserIds(), keyword);
    }

    /** 批量加入班级。 */
    @Transactional(rollbackFor = Exception.class)
    public int addStudents(Long classId, List<Long> studentIds) {
        requireOwnedClass(classId);
        Role studentRole = requireStudentRole();

        List<Long> distinctIds = studentIds.stream().distinct().toList();
        for (Long studentId : distinctIds) {
            User user = userMapper.selectById(studentId);
            if (user == null) {
                throw new BusinessException("学生不存在（ID=" + studentId + "）");
            }
            Long roleCount = userRoleMapper.selectCount(
                    Wrappers.<UserRole>lambdaQuery()
                            .eq(UserRole::getUserId, studentId)
                            .eq(UserRole::getRoleId, studentRole.getId()));
            if (roleCount == null || roleCount == 0) {
                throw new BusinessException("用户「" + displayName(user) + "」不是学生身份，无法加入班级");
            }
        }

        Map<Long, ClassStudent> existing = classStudentMapper.selectList(
                        Wrappers.<ClassStudent>lambdaQuery()
                                .in(ClassStudent::getStudentId, distinctIds))
                .stream()
                .collect(Collectors.toMap(ClassStudent::getStudentId, item -> item));

        int added = 0;
        for (Long studentId : distinctIds) {
            ClassStudent relation = existing.get(studentId);
            if (relation != null) {
                if (Objects.equals(relation.getClassId(), classId)) {
                    continue;
                }
                ClassInfo other = classInfoMapper.selectById(relation.getClassId());
                String otherName = other == null ? relation.getClassId().toString() : other.getClassName();
                throw new BusinessException("学生「" + displayName(userMapper.selectById(studentId))
                        + "」已在班级「" + otherName + "」中，请先移出");
            }
            ClassStudent insert = new ClassStudent();
            insert.setClassId(classId);
            insert.setStudentId(studentId);
            insert.setStatus(STATUS_NORMAL);
            classStudentMapper.insert(insert);
            added++;
        }
        return added;
    }

    /** 将学生移出班级（逻辑删除关系）。 */
    @Transactional(rollbackFor = Exception.class)
    public void removeStudent(Long classId, Long studentId) {
        requireOwnedClass(classId);
        ClassStudent relation = classStudentMapper.selectOne(
                Wrappers.<ClassStudent>lambdaQuery()
                        .eq(ClassStudent::getClassId, classId)
                        .eq(ClassStudent::getStudentId, studentId));
        if (relation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该学生不在此班级");
        }
        classStudentMapper.deleteById(relation.getId());
    }

    /** 校验班级存在且当前用户有维护权限。 */
    public ClassInfo requireOwnedClass(Long classId) {
        ClassInfo clazz = classInfoMapper.selectById(classId);
        if (clazz == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班级不存在");
        }
        if (!accountSupport.isAdmin()
                && !Objects.equals(clazz.getTeacherId(), accountSupport.requireCurrentUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作自己负责的班级");
        }
        return clazz;
    }

    /** 教师强制绑定自己；管理员未指定时默认为自己。 */
    private Long resolveTeacherId(Long requested) {
        if (accountSupport.isTeacher() && !accountSupport.isAdmin()) {
            return accountSupport.requireCurrentUserId();
        }
        Long teacherId = requested != null ? requested : accountSupport.requireCurrentUserId();
        if (userMapper.selectById(teacherId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任课教师不存在");
        }
        return teacherId;
    }

    private void assertClassCodeUnique(String classCode, Long excludeId) {
        Long count = classInfoMapper.selectCount(
                Wrappers.<ClassInfo>lambdaQuery()
                        .eq(ClassInfo::getClassCode, classCode)
                        .ne(excludeId != null, ClassInfo::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("班级编码已存在");
        }
    }

    private Role requireStudentRole() {
        Role role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getRoleCode, ROLE_STUDENT)
                .eq(Role::getStatus, STATUS_NORMAL));
        if (role == null) {
            throw new BusinessException("学生角色未初始化，请联系管理员");
        }
        return role;
    }

    private List<Long> listStudentRoleUserIds() {
        Role role = requireStudentRole();
        List<UserRole> relations = userRoleMapper.selectList(
                Wrappers.<UserRole>lambdaQuery().eq(UserRole::getRoleId, role.getId()));
        return relations.stream()
                .map(UserRole::getUserId)
                .distinct()
                .toList();
    }

    private List<ClassStudentVO> toStudentVOs(List<Long> userIds, String keyword) {
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        List<ClassStudentVO> result = new ArrayList<>();
        for (User user : users) {
            if (!Objects.equals(user.getStatus(), STATUS_NORMAL)) {
                continue;
            }
            if (StringUtils.hasText(keyword)
                    && !contains(user.getUsername(), keyword)
                    && !contains(user.getNickname(), keyword)) {
                continue;
            }
            ClassStudentVO vo = new ClassStudentVO();
            vo.setStudentId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setRealName(displayName(user));
            vo.setStatus(user.getStatus());
            result.add(vo);
            if (result.size() >= STUDENT_OPTION_LIMIT) {
                break;
            }
        }
        return result;
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword.toLowerCase());
    }

    private void fillTeacherNames(List<ClassVO> classes) {
        List<Long> teacherIds = classes.stream()
                .map(ClassVO::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (teacherIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = new HashMap<>();
        userMapper.selectBatchIds(teacherIds)
                .forEach(user -> nameMap.put(user.getId(), displayName(user)));
        classes.forEach(vo -> vo.setTeacherName(nameMap.get(vo.getTeacherId())));
    }

    private void fillStudentCounts(List<ClassVO> classes) {
        if (classes.isEmpty()) {
            return;
        }
        List<Long> classIds = classes.stream().map(ClassVO::getId).toList();
        Map<Long, Long> counts = classStudentMapper.selectList(
                        Wrappers.<ClassStudent>lambdaQuery()
                                .in(ClassStudent::getClassId, classIds))
                .stream()
                .collect(Collectors.groupingBy(ClassStudent::getClassId, Collectors.counting()));
        classes.forEach(vo -> vo.setStudentCount(counts.getOrDefault(vo.getId(), 0L)));
    }

    private String displayName(User user) {
        if (user == null) {
            return "";
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private ClassVO toClassVO(ClassInfo clazz) {
        ClassVO vo = new ClassVO();
        vo.setId(clazz.getId());
        vo.setClassCode(clazz.getClassCode());
        vo.setClassName(clazz.getClassName());
        vo.setGrade(clazz.getGrade());
        vo.setTeacherId(clazz.getTeacherId());
        vo.setDescription(clazz.getDescription());
        vo.setStatus(clazz.getStatus());
        vo.setCreateTime(clazz.getCreateTime());
        vo.setUpdateTime(clazz.getUpdateTime());
        return vo;
    }
}
