package com.marking.aimarkingsystem.course.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.PageResult;
import com.marking.aimarkingsystem.common.result.ResultCode;
import com.marking.aimarkingsystem.course.dto.CourseSaveDTO;
import com.marking.aimarkingsystem.course.entity.Course;
import com.marking.aimarkingsystem.course.mapper.CourseMapper;
import com.marking.aimarkingsystem.course.vo.CourseVO;
import com.marking.aimarkingsystem.paper.entity.ExamPaper;
import com.marking.aimarkingsystem.paper.mapper.ExamPaperMapper;
import com.marking.aimarkingsystem.user.entity.User;
import com.marking.aimarkingsystem.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 课程服务：新增、编辑、分页查询、归档。
 *
 * 数据权限说明（占位，暂不实现）：
 * 教师只能操作"teacherId = 自己"的课程。完整实现时有两种方式：
 * 1. 在 Controller 层用 @RequiresPermission 的扩展属性声明资源归属，由切面统一校验；
 * 2. 在每个查询/写操作入口取出当前登录用户，校验 course.teacherId 是否等于当前用户 id。
 * 本阶段只在关键方法内保留占位注释，不做实际过滤，避免把未定稿的数据权限规则写死。
 */
@Service
public class CourseService {

    /** 课程正常状态 */
    private static final int STATUS_NORMAL = 1;

    /** 课程归档（停用）状态 */
    private static final int STATUS_ARCHIVED = 0;

    /** 试卷已归档状态，与 PaperService.STATUS_ARCHIVED 保持一致（此处避免 Service 间循环依赖） */
    private static final int PAPER_STATUS_ARCHIVED = 3;

    private final CourseMapper courseMapper;

    private final UserMapper userMapper;

    private final ExamPaperMapper examPaperMapper;

    public CourseService(CourseMapper courseMapper,
                         UserMapper userMapper,
                         ExamPaperMapper examPaperMapper) {
        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
        this.examPaperMapper = examPaperMapper;
    }

    /**
     * 新增课程。
     *
     * 数据权限占位：后续仅允许管理员新增课程，或教师只能给自己创建课程
     * （即 dto.teacherId 必须等于当前登录用户 id），当前不做限制。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long addCourse(CourseSaveDTO dto) {
        assertCourseCodeUnique(dto.getCourseCode(), null);
        requireTeacher(dto.getTeacherId());

        Course course = new Course();
        applyDto(course, dto);
        if (course.getStatus() == null) {
            course.setStatus(STATUS_NORMAL);
        }
        courseMapper.insert(course);
        return course.getId();
    }

    /**
     * 编辑课程。
     *
     * 数据权限占位：后续需校验 course.teacherId 是否等于当前登录教师 id，
     * 否则返回 403，防止教师修改他人课程。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCourse(Long id, CourseSaveDTO dto) {
        Course exists = requireCourse(id);
        assertCourseCodeUnique(dto.getCourseCode(), id);
        requireTeacher(dto.getTeacherId());

        Course update = new Course();
        update.setId(id);
        applyDto(update, dto);
        // 状态不传时保持原值，避免编辑基础信息时被意外改成默认值
        if (update.getStatus() == null) {
            update.setStatus(exists.getStatus());
        }
        courseMapper.updateById(update);
    }

    /**
     * 分页查询课程。
     *
     * @param keyword   课程名称/编码模糊关键字，可为空
     * @param teacherId 任课教师过滤，可为空
     * @param status    状态过滤，可为空
     *
     * 数据权限占位（本方法最关键）：
     * 后续当登录角色为 TEACHER 时，应强制把 teacherId 覆盖为当前登录用户 id，
     * 使教师只能看到自己的课程；ADMIN 则不加限制。当前按传入参数原样查询。
     */
    public PageResult<CourseVO> pageCourses(Integer pageNum, Integer pageSize,
                                            String keyword, Long teacherId, Integer status) {
        Page<Course> page = new Page<>(pageNum, pageSize);
        Page<Course> coursePage = courseMapper.selectPage(page,
                Wrappers.<Course>lambdaQuery()
                        // 关键字同时匹配课程名称与课程编码，前端只传一个搜索框即可
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(Course::getCourseName, keyword)
                                .or()
                                .like(Course::getCourseCode, keyword))
                        .eq(teacherId != null, Course::getTeacherId, teacherId)
                        .eq(status != null, Course::getStatus, status)
                        .orderByDesc(Course::getCreateTime));

        List<CourseVO> records = coursePage.getRecords().stream()
                .map(this::toCourseVO)
                .toList();
        fillTeacherNames(records);
        return PageResult.of(coursePage, records);
    }

    /**
     * 归档课程（置为停用）。
     *
     * 业务规则：课程下仍有未归档试卷时不允许归档，避免出现"课程已停用、
     * 但试卷还在待考试/阅卷中"的悬空数据。
     *
     * 数据权限占位：同编辑，需校验课程归属当前教师。
     */
    @Transactional(rollbackFor = Exception.class)
    public void archiveCourse(Long id) {
        requireCourse(id);

        Long unarchivedPapers = examPaperMapper.selectCount(
                Wrappers.<ExamPaper>lambdaQuery()
                        .eq(ExamPaper::getCourseId, id)
                        .ne(ExamPaper::getStatus, PAPER_STATUS_ARCHIVED));
        if (unarchivedPapers != null && unarchivedPapers > 0) {
            throw new BusinessException("该课程下仍有未归档的试卷，请先归档试卷");
        }

        Course update = new Course();
        update.setId(id);
        update.setStatus(STATUS_ARCHIVED);
        courseMapper.updateById(update);
    }

    /** 课程编码唯一性校验（逻辑删除场景下不加数据库唯一索引，改由业务层保证） */
    private void assertCourseCodeUnique(String courseCode, Long excludeId) {
        Long count = courseMapper.selectCount(
                Wrappers.<Course>lambdaQuery()
                        .eq(Course::getCourseCode, courseCode)
                        .ne(excludeId != null, Course::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("课程编码已存在");
        }
    }

    private Course requireCourse(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在");
        }
        return course;
    }

    /** 任课教师必须存在，避免课程挂在一个不存在的用户 id 上 */
    private void requireTeacher(Long teacherId) {
        User teacher = userMapper.selectById(teacherId);
        if (teacher == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任课教师不存在");
        }
    }

    private void applyDto(Course course, CourseSaveDTO dto) {
        course.setCourseCode(dto.getCourseCode());
        course.setCourseName(dto.getCourseName());
        course.setTeacherId(dto.getTeacherId());
        course.setTerm(dto.getTerm());
        course.setCredit(dto.getCredit());
        course.setDescription(dto.getDescription());
        course.setStatus(dto.getStatus());
    }

    /** 批量填充教师姓名，避免分页结果逐条查用户表（N+1） */
    private void fillTeacherNames(List<CourseVO> courses) {
        if (courses.isEmpty()) {
            return;
        }

        List<Long> teacherIds = courses.stream()
                .map(CourseVO::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (teacherIds.isEmpty()) {
            return;
        }

        Map<Long, String> nameMap = userMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(User::getId,
                        user -> StringUtils.hasText(user.getNickname())
                                ? user.getNickname() : user.getUsername()));

        courses.forEach(vo -> vo.setTeacherName(nameMap.get(vo.getTeacherId())));
    }

    private CourseVO toCourseVO(Course course) {
        CourseVO vo = new CourseVO();
        vo.setId(course.getId());
        vo.setCourseCode(course.getCourseCode());
        vo.setCourseName(course.getCourseName());
        vo.setTeacherId(course.getTeacherId());
        vo.setTerm(course.getTerm());
        vo.setCredit(course.getCredit());
        vo.setDescription(course.getDescription());
        vo.setStatus(course.getStatus());
        vo.setCreateTime(course.getCreateTime());
        vo.setUpdateTime(course.getUpdateTime());
        return vo;
    }
}
