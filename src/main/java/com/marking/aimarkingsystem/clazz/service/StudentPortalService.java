package com.marking.aimarkingsystem.clazz.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.answer.entity.ExamAnswer;
import com.marking.aimarkingsystem.answer.mapper.ExamAnswerMapper;
import com.marking.aimarkingsystem.answer.service.PaperAnswerService;
import com.marking.aimarkingsystem.answer.vo.PaperAnswerDetailVO;
import com.marking.aimarkingsystem.clazz.entity.ClassStudent;
import com.marking.aimarkingsystem.clazz.entity.PaperAssignment;
import com.marking.aimarkingsystem.clazz.mapper.ClassStudentMapper;
import com.marking.aimarkingsystem.clazz.mapper.PaperAssignmentMapper;
import com.marking.aimarkingsystem.clazz.support.AccountSupport;
import com.marking.aimarkingsystem.clazz.vo.StudentPaperVO;
import com.marking.aimarkingsystem.clazz.vo.StudentScoreVO;
import com.marking.aimarkingsystem.course.entity.Course;
import com.marking.aimarkingsystem.course.mapper.CourseMapper;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.paper.entity.ExamPaper;
import com.marking.aimarkingsystem.paper.mapper.ExamPaperMapper;
import com.marking.aimarkingsystem.paper.service.PaperService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学生端门户：我的试卷、我的成绩。
 *
 * 所有查询都以「当前登录学生 id」为唯一过滤条件，
 * 不接收前端传入的 studentId，避免越权查看他人数据。
 */
@Service
public class StudentPortalService {

    private final ClassStudentMapper classStudentMapper;

    private final PaperAssignmentMapper paperAssignmentMapper;

    private final ExamPaperMapper examPaperMapper;

    private final ExamAnswerMapper examAnswerMapper;

    private final CourseMapper courseMapper;

    private final AccountSupport accountSupport;

    private final PaperAnswerService paperAnswerService;

    public StudentPortalService(ClassStudentMapper classStudentMapper,
                                PaperAssignmentMapper paperAssignmentMapper,
                                ExamPaperMapper examPaperMapper,
                                ExamAnswerMapper examAnswerMapper,
                                CourseMapper courseMapper,
                                AccountSupport accountSupport,
                                PaperAnswerService paperAnswerService) {
        this.classStudentMapper = classStudentMapper;
        this.paperAssignmentMapper = paperAssignmentMapper;
        this.examPaperMapper = examPaperMapper;
        this.examAnswerMapper = examAnswerMapper;
        this.courseMapper = courseMapper;
        this.accountSupport = accountSupport;
        this.paperAnswerService = paperAnswerService;
    }

    /**
     * 我的答卷详情：只允许查看本人的答卷，复用教师端的详情组装逻辑。
     */
    public PaperAnswerDetailVO myAnswerDetail(Long answerId) {
        Long studentId = accountSupport.requireCurrentUserId();
        ExamAnswer answer = examAnswerMapper.selectById(answerId);
        if (answer == null || !Objects.equals(answer.getStudentId(), studentId)) {
            throw new BusinessException("答卷不存在或无权查看");
        }
        return paperAnswerService.getDetail(answerId);
    }

    /** 我的试卷：所在班级的整班下发 + 直接指定给我的下发。 */
    public List<StudentPaperVO> myPapers() {
        Long studentId = accountSupport.requireCurrentUserId();
        List<Long> classIds = classStudentMapper.selectList(
                        Wrappers.<ClassStudent>lambdaQuery()
                                .eq(ClassStudent::getStudentId, studentId))
                .stream()
                .map(ClassStudent::getClassId)
                .distinct()
                .toList();

        var query = Wrappers.<PaperAssignment>lambdaQuery()
                .eq(PaperAssignment::getStatus, 1);
        if (classIds.isEmpty()) {
            query.eq(PaperAssignment::getStudentId, studentId);
        } else {
            query.and(wrapper -> wrapper
                    .in(PaperAssignment::getClassId, classIds)
                    .or()
                    .eq(PaperAssignment::getStudentId, studentId));
        }
        query.orderByDesc(PaperAssignment::getAssignTime);

        List<PaperAssignment> assignments = paperAssignmentMapper.selectList(query);
        if (assignments.isEmpty()) {
            return Collections.emptyList();
        }

        // 同一试卷可能既有整班下发又有指定下发，这里按 paperId 去重
        Map<Long, PaperAssignment> byPaper = new LinkedHashMap<>();
        assignments.forEach(item -> byPaper.putIfAbsent(item.getPaperId(), item));

        Map<Long, ExamPaper> paperMap = examPaperMapper.selectBatchIds(byPaper.keySet()).stream()
                .collect(Collectors.toMap(ExamPaper::getId, Function.identity()));
        Map<Long, Course> courseMap = loadCourses(paperMap.values());

        Map<Long, ExamAnswer> answerMap = examAnswerMapper.selectList(
                        Wrappers.<ExamAnswer>lambdaQuery()
                                .eq(ExamAnswer::getStudentId, studentId)
                                .in(ExamAnswer::getPaperId, byPaper.keySet()))
                .stream()
                .collect(Collectors.toMap(ExamAnswer::getPaperId, Function.identity(), (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        List<StudentPaperVO> result = new ArrayList<>();
        byPaper.forEach((paperId, assignment) -> {
            ExamPaper paper = paperMap.get(paperId);
            if (paper == null) {
                return;
            }
            // 只展示待考试 / 阅卷中的试卷
            if (!Objects.equals(paper.getStatus(), PaperService.STATUS_PENDING)
                    && !Objects.equals(paper.getStatus(), PaperService.STATUS_MARKING)) {
                return;
            }
            // 已过截止时间的不再展示
            if (assignment.getDeadline() != null && assignment.getDeadline().isBefore(now)) {
                return;
            }

            StudentPaperVO vo = new StudentPaperVO();
            vo.setPaperId(paper.getId());
            vo.setPaperNo(paper.getPaperNo());
            vo.setPaperTitle(paper.getPaperTitle());
            Course course = paper.getCourseId() == null ? null : courseMap.get(paper.getCourseId());
            vo.setCourseName(course == null ? null : course.getCourseName());
            vo.setAnswerMode(paper.getAnswerMode());
            vo.setTotalScore(paper.getTotalScore());
            vo.setDuration(paper.getDuration());
            vo.setStartTime(paper.getStartTime());
            vo.setEndTime(paper.getEndTime());
            vo.setDeadline(assignment.getDeadline());
            vo.setAssignTime(assignment.getAssignTime());
            vo.setSource(assignment.getClassId() != null ? "CLASS" : "STUDENT");
            vo.setPaperStatus(paper.getStatus());

            ExamAnswer answer = answerMap.get(paperId);
            vo.setAnswered(answer != null);
            vo.setAnswerId(answer == null ? null : answer.getId());
            result.add(vo);
        });

        result.sort(Comparator.comparing(StudentPaperVO::getAssignTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    /** 我的成绩：只查本人的答卷记录。 */
    public List<StudentScoreVO> myScores() {
        Long studentId = accountSupport.requireCurrentUserId();
        List<ExamAnswer> answers = examAnswerMapper.selectList(
                Wrappers.<ExamAnswer>lambdaQuery()
                        .eq(ExamAnswer::getStudentId, studentId)
                        .orderByDesc(ExamAnswer::getSubmitTime));
        if (answers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> paperIds = answers.stream()
                .map(ExamAnswer::getPaperId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ExamPaper> paperMap = paperIds.isEmpty()
                ? Collections.emptyMap()
                : examPaperMapper.selectBatchIds(paperIds).stream()
                        .collect(Collectors.toMap(ExamPaper::getId, Function.identity()));

        return answers.stream().map(answer -> {
            StudentScoreVO vo = new StudentScoreVO();
            vo.setAnswerId(answer.getId());
            vo.setPaperId(answer.getPaperId());
            ExamPaper paper = paperMap.get(answer.getPaperId());
            if (paper != null) {
                vo.setPaperNo(paper.getPaperNo());
                vo.setPaperTitle(paper.getPaperTitle());
                vo.setPaperTotalScore(paper.getTotalScore());
            }
            vo.setAiReferenceScore(answer.getAiReferenceScore());
            vo.setFinalScore(answer.getTeacherFinalScore());
            vo.setMarkingStatus(answer.getMarkingStatus());
            vo.setSubmitTime(answer.getSubmitTime());
            vo.setMarkTime(answer.getMarkTime());
            return vo;
        }).toList();
    }

    private Map<Long, Course> loadCourses(Iterable<ExamPaper> papers) {
        List<Long> courseIds = new ArrayList<>();
        papers.forEach(paper -> {
            if (paper.getCourseId() != null) {
                courseIds.add(paper.getCourseId());
            }
        });
        if (courseIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return courseMapper.selectBatchIds(courseIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
    }
}
