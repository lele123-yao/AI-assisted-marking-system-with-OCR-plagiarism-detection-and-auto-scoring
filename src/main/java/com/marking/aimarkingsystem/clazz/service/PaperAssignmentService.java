package com.marking.aimarkingsystem.clazz.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.clazz.dto.PaperAssignDTO;
import com.marking.aimarkingsystem.clazz.entity.ClassInfo;
import com.marking.aimarkingsystem.clazz.entity.PaperAssignment;
import com.marking.aimarkingsystem.clazz.mapper.ClassInfoMapper;
import com.marking.aimarkingsystem.clazz.mapper.PaperAssignmentMapper;
import com.marking.aimarkingsystem.clazz.support.AccountSupport;
import com.marking.aimarkingsystem.clazz.vo.PaperAssignmentVO;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.ResultCode;
import com.marking.aimarkingsystem.course.entity.Course;
import com.marking.aimarkingsystem.course.mapper.CourseMapper;
import com.marking.aimarkingsystem.paper.entity.ExamPaper;
import com.marking.aimarkingsystem.paper.mapper.ExamPaperMapper;
import com.marking.aimarkingsystem.paper.service.PaperService;
import com.marking.aimarkingsystem.role.entity.Role;
import com.marking.aimarkingsystem.role.mapper.RoleMapper;
import com.marking.aimarkingsystem.user.entity.User;
import com.marking.aimarkingsystem.user.entity.UserRole;
import com.marking.aimarkingsystem.user.mapper.UserMapper;
import com.marking.aimarkingsystem.user.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 试卷下发服务：把试卷下发到整班或指定学生，并记录截止时间。
 *
 * 权限：
 * - ADMIN 可下发任意试卷；
 * - TEACHER 只能下发自己课程（course.teacher_id = 自己）下的试卷，
 *   且只能选择自己负责的班级。
 */
@Service
public class PaperAssignmentService {

    private static final String ROLE_STUDENT = "STUDENT";

    private static final int STATUS_ENABLED = 1;

    private final PaperAssignmentMapper paperAssignmentMapper;

    private final ClassInfoMapper classInfoMapper;

    private final ExamPaperMapper examPaperMapper;

    private final CourseMapper courseMapper;

    private final UserMapper userMapper;

    private final UserRoleMapper userRoleMapper;

    private final RoleMapper roleMapper;

    private final ClassService classService;

    private final AccountSupport accountSupport;

    public PaperAssignmentService(PaperAssignmentMapper paperAssignmentMapper,
                                  ClassInfoMapper classInfoMapper,
                                  ExamPaperMapper examPaperMapper,
                                  CourseMapper courseMapper,
                                  UserMapper userMapper,
                                  UserRoleMapper userRoleMapper,
                                  RoleMapper roleMapper,
                                  ClassService classService,
                                  AccountSupport accountSupport) {
        this.paperAssignmentMapper = paperAssignmentMapper;
        this.classInfoMapper = classInfoMapper;
        this.examPaperMapper = examPaperMapper;
        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.classService = classService;
        this.accountSupport = accountSupport;
    }

    /** 下发试卷：整班与指定学生可同时提交，重复目标自动跳过。 */
    @Transactional(rollbackFor = Exception.class)
    public int assign(PaperAssignDTO dto) {
        Long currentUserId = accountSupport.requireCurrentUserId();
        ExamPaper paper = requireAssignablePaper(dto.getPaperId(), currentUserId);

        if (dto.getClassId() != null) {
            // 只能选择自己负责的班级（管理员不受限）
            classService.requireOwnedClass(dto.getClassId());
        }

        List<Long> studentIds = dto.getStudentIds() == null
                ? Collections.emptyList()
                : dto.getStudentIds().stream().filter(Objects::nonNull).distinct().toList();
        if (dto.getClassId() == null && studentIds.isEmpty()) {
            throw new BusinessException("请选择班级或至少指定一名学生");
        }
        validateStudents(studentIds);

        int created = 0;
        if (dto.getClassId() != null) {
            Long exists = paperAssignmentMapper.selectCount(
                    Wrappers.<PaperAssignment>lambdaQuery()
                            .eq(PaperAssignment::getPaperId, paper.getId())
                            .eq(PaperAssignment::getClassId, dto.getClassId()));
            if (exists == null || exists == 0) {
                PaperAssignment assignment = new PaperAssignment();
                assignment.setPaperId(paper.getId());
                assignment.setClassId(dto.getClassId());
                assignment.setTeacherId(currentUserId);
                assignment.setAssignTime(LocalDateTime.now());
                assignment.setDeadline(dto.getDeadline());
                assignment.setStatus(STATUS_ENABLED);
                paperAssignmentMapper.insert(assignment);
                created++;
            }
        }

        for (Long studentId : studentIds) {
            Long exists = paperAssignmentMapper.selectCount(
                    Wrappers.<PaperAssignment>lambdaQuery()
                            .eq(PaperAssignment::getPaperId, paper.getId())
                            .eq(PaperAssignment::getStudentId, studentId));
            if (exists != null && exists > 0) {
                continue;
            }
            PaperAssignment assignment = new PaperAssignment();
            assignment.setPaperId(paper.getId());
            assignment.setStudentId(studentId);
            assignment.setTeacherId(currentUserId);
            assignment.setAssignTime(LocalDateTime.now());
            assignment.setDeadline(dto.getDeadline());
            assignment.setStatus(STATUS_ENABLED);
            paperAssignmentMapper.insert(assignment);
            created++;
        }
        return created;
    }

    /** 查询某试卷的下发记录。 */
    public List<PaperAssignmentVO> pageByPaper(Long paperId) {
        ExamPaper paper = examPaperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        requirePaperOwnership(paper, accountSupport.requireCurrentUserId());

        List<PaperAssignment> list = paperAssignmentMapper.selectList(
                Wrappers.<PaperAssignment>lambdaQuery()
                        .eq(PaperAssignment::getPaperId, paperId)
                        .orderByDesc(PaperAssignment::getCreateTime));
        if (list.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, String> classNames = new HashMap<>();
        List<Long> classIds = list.stream()
                .map(PaperAssignment::getClassId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!classIds.isEmpty()) {
            classInfoMapper.selectBatchIds(classIds)
                    .forEach(clazz -> classNames.put(clazz.getId(), clazz.getClassName()));
        }

        Map<Long, String> studentNames = new HashMap<>();
        List<Long> studentIds = list.stream()
                .map(PaperAssignment::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!studentIds.isEmpty()) {
            userMapper.selectBatchIds(studentIds)
                    .forEach(user -> studentNames.put(user.getId(), displayName(user)));
        }

        return list.stream().map(item -> {
            PaperAssignmentVO vo = new PaperAssignmentVO();
            vo.setId(item.getId());
            vo.setPaperId(item.getPaperId());
            vo.setClassId(item.getClassId());
            vo.setStudentId(item.getStudentId());
            vo.setAssignTime(item.getAssignTime());
            vo.setDeadline(item.getDeadline());
            vo.setStatus(item.getStatus());
            if (item.getClassId() != null) {
                vo.setClassName(classNames.get(item.getClassId()));
                vo.setTargetName(classNames.getOrDefault(item.getClassId(), "未知班级"));
            } else {
                vo.setStudentName(studentNames.get(item.getStudentId()));
                vo.setTargetName(studentNames.getOrDefault(item.getStudentId(), "未知学生"));
            }
            return vo;
        }).toList();
    }

    /** 撤销下发（逻辑删除）。 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        PaperAssignment assignment = paperAssignmentMapper.selectById(id);
        if (assignment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "下发记录不存在");
        }
        if (!accountSupport.isAdmin()
                && !Objects.equals(assignment.getTeacherId(), accountSupport.requireCurrentUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能撤销自己下发的记录");
        }
        paperAssignmentMapper.deleteById(id);
    }

    private ExamPaper requireAssignablePaper(Long paperId, Long currentUserId) {
        ExamPaper paper = examPaperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        if (!Objects.equals(paper.getStatus(), PaperService.STATUS_PENDING)
                && !Objects.equals(paper.getStatus(), PaperService.STATUS_MARKING)) {
            throw new BusinessException("只有「待考试」或「阅卷中」的试卷可以下发");
        }
        requirePaperOwnership(paper, currentUserId);
        return paper;
    }

    private void requirePaperOwnership(ExamPaper paper, Long currentUserId) {
        if (accountSupport.isAdmin()) {
            return;
        }
        Course course = paper.getCourseId() == null ? null : courseMapper.selectById(paper.getCourseId());
        if (course == null || !Objects.equals(course.getTeacherId(), currentUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作自己课程下的试卷");
        }
    }

    private void validateStudents(List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return;
        }
        Role studentRole = roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getRoleCode, ROLE_STUDENT)
                .eq(Role::getStatus, STATUS_ENABLED));
        if (studentRole == null) {
            throw new BusinessException("学生角色未初始化，请联系管理员");
        }
        for (Long studentId : studentIds) {
            User user = userMapper.selectById(studentId);
            if (user == null) {
                throw new BusinessException("学生不存在（ID=" + studentId + "）");
            }
            Long count = userRoleMapper.selectCount(Wrappers.<UserRole>lambdaQuery()
                    .eq(UserRole::getUserId, studentId)
                    .eq(UserRole::getRoleId, studentRole.getId()));
            if (count == null || count == 0) {
                throw new BusinessException("用户「" + displayName(user) + "」不是学生身份");
            }
        }
    }

    private String displayName(User user) {
        if (user == null) {
            return "";
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }
}
