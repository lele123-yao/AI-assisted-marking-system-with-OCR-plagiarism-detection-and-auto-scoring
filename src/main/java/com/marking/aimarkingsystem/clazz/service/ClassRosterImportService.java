package com.marking.aimarkingsystem.clazz.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.clazz.dto.ClassRosterRowDTO;
import com.marking.aimarkingsystem.clazz.entity.ClassInfo;
import com.marking.aimarkingsystem.clazz.entity.ClassStudent;
import com.marking.aimarkingsystem.clazz.excel.ClassRosterImportListener;
import com.marking.aimarkingsystem.clazz.mapper.ClassInfoMapper;
import com.marking.aimarkingsystem.clazz.mapper.ClassStudentMapper;
import com.marking.aimarkingsystem.clazz.vo.ClassRosterImportResultVO;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.role.entity.Role;
import com.marking.aimarkingsystem.role.mapper.RoleMapper;
import com.marking.aimarkingsystem.user.entity.User;
import com.marking.aimarkingsystem.user.entity.UserRole;
import com.marking.aimarkingsystem.user.mapper.UserMapper;
import com.marking.aimarkingsystem.user.mapper.UserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 班级名单 Excel 导入。
 *
 * 与「考生名单导入」不同：考生名单是给某一张试卷建纸质考生档案（可以没有系统账号），
 * 班级名单是把学生账号持久绑定到班级，直接影响学生端能否收到线上试题，
 * 因此这里要求每一行都对应一个真实的 STUDENT 账号（或在允许时自动建号）。
 *
 * 单行失败不会中断整批导入，最终统一返回成功/跳过/失败明细。
 */
@Service
public class ClassRosterImportService {

    private static final String ROLE_STUDENT = "STUDENT";

    private static final int STATUS_ENABLED = 1;

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private static final int MAX_USERNAME_LENGTH = 32;

    private static final int MAX_NAME_LENGTH = 32;

    private final ClassService classService;

    private final ClassStudentMapper classStudentMapper;

    private final ClassInfoMapper classInfoMapper;

    private final UserMapper userMapper;

    private final UserRoleMapper userRoleMapper;

    private final RoleMapper roleMapper;

    private final PasswordEncoder passwordEncoder;

    public ClassRosterImportService(ClassService classService,
                                    ClassStudentMapper classStudentMapper,
                                    ClassInfoMapper classInfoMapper,
                                    UserMapper userMapper,
                                    UserRoleMapper userRoleMapper,
                                    RoleMapper roleMapper,
                                    PasswordEncoder passwordEncoder) {
        this.classService = classService;
        this.classStudentMapper = classStudentMapper;
        this.classInfoMapper = classInfoMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 导入班级名单。
     *
     * @param classId       目标班级
     * @param autoCreateUser 学号不存在时是否自动创建学生账号
     * @param file          Excel 文件（表头：学号、姓名）
     */
    @Transactional(rollbackFor = Exception.class)
    public ClassRosterImportResultVO importRoster(Long classId, boolean autoCreateUser, MultipartFile file) {
        // 归属校验：教师只能给自己的班级导入
        ClassInfo clazz = classService.requireOwnedClass(classId);
        validateFile(file);
        Role studentRole = requireStudentRole();

        List<ClassRosterRowDTO> rows = readRows(file);
        if (rows.isEmpty()) {
            throw new BusinessException("Excel 中没有可导入的数据");
        }

        ClassRosterImportResultVO result = new ClassRosterImportResultVO();
        result.setTotalRows(rows.size());

        // 先批量预取，避免逐行查库
        Set<String> studentNos = new HashSet<>();
        rows.forEach(row -> {
            String no = safeTrim(row.getStudentNo());
            if (StringUtils.hasText(no)) {
                studentNos.add(no);
            }
        });
        Map<String, User> userMap = new HashMap<>();
        if (!studentNos.isEmpty()) {
            userMapper.selectList(Wrappers.<User>lambdaQuery()
                            .in(User::getUsername, studentNos))
                    .forEach(user -> userMap.put(user.getUsername(), user));
        }

        List<Long> foundUserIds = userMap.values().stream().map(User::getId).toList();
        Set<Long> studentUserIds = new HashSet<>();
        if (!foundUserIds.isEmpty()) {
            userRoleMapper.selectList(Wrappers.<UserRole>lambdaQuery()
                            .eq(UserRole::getRoleId, studentRole.getId())
                            .in(UserRole::getUserId, foundUserIds))
                    .forEach(relation -> studentUserIds.add(relation.getUserId()));
        }

        Map<Long, ClassStudent> relationMap = new HashMap<>();
        if (!foundUserIds.isEmpty()) {
            classStudentMapper.selectList(Wrappers.<ClassStudent>lambdaQuery()
                            .in(ClassStudent::getStudentId, foundUserIds))
                    .forEach(relation -> relationMap.put(relation.getStudentId(), relation));
        }
        Map<Long, String> classNames = loadClassNames(relationMap.values());

        for (int i = 0; i < rows.size(); i++) {
            ClassRosterRowDTO row = rows.get(i);
            int rowNum = i + 2;
            String studentNo = safeTrim(row.getStudentNo());
            String studentName = safeTrim(row.getStudentName());

            if (!StringUtils.hasText(studentNo)) {
                addFailure(result, rowNum, studentNo, studentName, "学号为空");
                continue;
            }
            if (studentNo.length() > MAX_USERNAME_LENGTH) {
                addFailure(result, rowNum, studentNo, studentName, "学号超过 " + MAX_USERNAME_LENGTH + " 位");
                continue;
            }

            try {
                User user = userMap.get(studentNo);
                if (user != null) {
                    bindExistingUser(result, clazz, user, studentUserIds, relationMap, classNames,
                            rowNum, studentName);
                } else if (!autoCreateUser) {
                    addFailure(result, rowNum, studentNo, studentName, "账号不存在，且未勾选自动创建");
                } else {
                    createStudentAndBind(result, clazz, studentRole, studentNo, studentName,
                            rowNum, userMap, studentUserIds, relationMap);
                }
            } catch (BusinessException e) {
                addFailure(result, rowNum, studentNo, studentName, e.getMessage());
            } catch (Exception e) {
                // 单行异常不影响其他行；保留原始提示便于老师排查
                addFailure(result, rowNum, studentNo, studentName, "导入失败：" + e.getMessage());
            }
        }
        return result;
    }

    private void bindExistingUser(ClassRosterImportResultVO result, ClassInfo clazz, User user,
                                  Set<Long> studentUserIds, Map<Long, ClassStudent> relationMap,
                                  Map<Long, String> classNames, int rowNum, String studentName) {
        if (!Objects.equals(user.getStatus(), STATUS_ENABLED)) {
            addFailure(result, rowNum, user.getUsername(), studentName, "账号已禁用");
            return;
        }
        if (!studentUserIds.contains(user.getId())) {
            addFailure(result, rowNum, user.getUsername(), studentName, "该账号不是学生身份");
            return;
        }
        ClassStudent relation = relationMap.get(user.getId());
        if (relation != null) {
            if (Objects.equals(relation.getClassId(), clazz.getId())) {
                result.setSkippedCount(result.getSkippedCount() + 1);
            } else {
                String other = classNames.getOrDefault(relation.getClassId(), "其他班级");
                addFailure(result, rowNum, user.getUsername(), studentName,
                        "已在班级「" + other + "」中，请先在原班级移出");
            }
            return;
        }
        ClassStudent insert = new ClassStudent();
        insert.setClassId(clazz.getId());
        insert.setStudentId(user.getId());
        insert.setStatus(STATUS_ENABLED);
        classStudentMapper.insert(insert);
        relationMap.put(user.getId(), insert);
        result.setSuccessCount(result.getSuccessCount() + 1);
    }

    private void createStudentAndBind(ClassRosterImportResultVO result, ClassInfo clazz, Role studentRole,
                                      String studentNo, String studentName, int rowNum,
                                      Map<String, User> userMap, Set<Long> studentUserIds,
                                      Map<Long, ClassStudent> relationMap) {
        if (!StringUtils.hasText(studentName)) {
            addFailure(result, rowNum, studentNo, studentName, "账号不存在且姓名为空，无法自动创建");
            return;
        }
        if (studentName.length() > MAX_NAME_LENGTH) {
            addFailure(result, rowNum, studentNo, studentName,
                    "姓名超过 " + MAX_NAME_LENGTH + " 位，无法自动创建");
            return;
        }

        User user = new User();
        user.setUsername(studentNo);
        user.setPassword(passwordEncoder.encode(initialPassword(studentNo)));
        user.setNickname(studentName);
        user.setStatus(STATUS_ENABLED);
        userMapper.insert(user);

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(studentRole.getId());
        userRoleMapper.insert(userRole);

        ClassStudent relation = new ClassStudent();
        relation.setClassId(clazz.getId());
        relation.setStudentId(user.getId());
        relation.setStatus(STATUS_ENABLED);
        classStudentMapper.insert(relation);

        userMap.put(studentNo, user);
        studentUserIds.add(user.getId());
        relationMap.put(user.getId(), relation);
        result.setCreatedCount(result.getCreatedCount() + 1);
        result.setSuccessCount(result.getSuccessCount() + 1);
    }

    /** 初始密码：学号后 6 位；学号不足 6 位时直接使用学号。 */
    private String initialPassword(String studentNo) {
        return studentNo.length() <= 6 ? studentNo : studentNo.substring(studentNo.length() - 6);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的 Excel 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件不能超过 10MB");
        }
        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase();
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new BusinessException("只支持 .xlsx 或 .xls 格式的 Excel 文件");
        }
    }

    private List<ClassRosterRowDTO> readRows(MultipartFile file) {
        ClassRosterImportListener listener = new ClassRosterImportListener();
        try (InputStream inputStream = file.getInputStream()) {
            EasyExcel.read(inputStream, ClassRosterRowDTO.class, listener).sheet().doRead();
        } catch (Exception e) {
            throw new BusinessException("Excel 解析失败，请确认表头为「学号」「姓名」");
        }
        return listener.getRows();
    }

    private Role requireStudentRole() {
        Role role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getRoleCode, ROLE_STUDENT)
                .eq(Role::getStatus, STATUS_ENABLED));
        if (role == null) {
            throw new BusinessException("学生角色未初始化，请联系管理员");
        }
        return role;
    }

    private Map<Long, String> loadClassNames(Iterable<ClassStudent> relations) {
        Set<Long> classIds = new HashSet<>();
        relations.forEach(relation -> {
            if (relation.getClassId() != null) {
                classIds.add(relation.getClassId());
            }
        });
        if (classIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> names = new HashMap<>();
        classInfoMapper.selectBatchIds(classIds)
                .forEach(clazz -> names.put(clazz.getId(), clazz.getClassName()));
        return names;
    }

    private void addFailure(ClassRosterImportResultVO result, int rowNum, String studentNo,
                            String studentName, String reason) {
        result.getFailures().add(new ClassRosterImportResultVO.FailedRow(
                rowNum, studentNo, studentName, reason));
        result.setFailedCount(result.getFailedCount() + 1);
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }
}
