package com.marking.aimarkingsystem.learning.controller;

import com.marking.aimarkingsystem.common.annotation.OperationLog;
import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.learning.dto.LearningAnalysisCreateDTO;
import com.marking.aimarkingsystem.learning.service.LearningAnalysisService;
import com.marking.aimarkingsystem.learning.vo.LearningAnalysisReportVO;
import com.marking.aimarkingsystem.learning.vo.LearningStatisticsVO;
import com.marking.aimarkingsystem.learning.vo.PptDownloadVO;
import com.marking.aimarkingsystem.learning.vo.StudentLearningCardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学情智能分析 Controller（模块8）。
 *
 * 接口清单：
 * 1. GET  /learning/papers/{paperId}/statistics              获取试卷班级学情统计
 * 2. POST /learning/reports                                  触发 AI 生成教学讲评建议
 * 3. POST /learning/reports/{reportId}/ppt                   生成讲评 PPT 课件并返回下载地址
 * 4. GET  /learning/papers/{paperId}/students/{studentId}/card  学生个人学情卡片
 *
 * 权限说明：
 * - 教师/管理员可查看班级统计、生成讲评与课件、查看学生卡片；
 * - 学生只允许查看本人卡片（学生角色已放开接口权限，行级校验见下方占位说明）。
 *
 * 数据权限（占位）：
 * 教师只能查看自己课程（paper.courseId → course.teacherId）下的数据；
 * 本阶段只在 Service 中保留占位注释，未接入实际的行级过滤。
 *
 * 统一返回：
 * 所有接口使用 Result 包装；参数校验失败与业务异常由全局异常处理器统一转换为 Result。
 */
@RestController
@RequestMapping("/learning")
@Validated
@Tag(name = "学情智能分析", description = "班级学情统计、AI教学讲评、讲评PPT课件、学生个人学情卡片")
public class LearningAnalysisController {

    private final LearningAnalysisService learningAnalysisService;

    public LearningAnalysisController(LearningAnalysisService learningAnalysisService) {
        this.learningAnalysisService = learningAnalysisService;
    }

    /**
     * 1. 获取试卷班级学情统计数据。
     *
     * 返回整体指标（均分/最值/及格率/优秀率）、班级对比、高频错题、知识点得分率。
     * 统计实时聚合答卷数据，不读缓存，保证教师看到的是最新成绩。
     */
    @GetMapping("/papers/{paperId}/statistics")
    @Operation(summary = "获取试卷班级学情统计",
            description = "统计整体指标、班级指标、高频错题与知识点得分率；实时聚合，不缓存")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    public Result<LearningStatisticsVO> statistics(
            @Parameter(description = "试卷ID", required = true, example = "3")
            @PathVariable Long paperId) {
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户
        return Result.success(learningAnalysisService.statistics(paperId));
    }

    /**
     * 2. 触发 AI 生成教学讲评建议。
     *
     * 同步调用大模型，返回 Markdown 讲评正文与统计快照。
     * 本接口只生成文字，不生成 PPT；PPT 由接口 3 单独按需生成，避免长耗时耦合。
     */
    @PostMapping("/reports")
    @Operation(summary = "触发AI生成教学讲评建议",
            description = "基于试卷统计快照调用大模型生成Markdown讲评；只生成正文，不生成PPT")
    @OperationLog(module = "学情分析", action = "生成AI教学讲评")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    public Result<LearningAnalysisReportVO> generateAnalysis(
            @Parameter(description = "创建请求：试卷ID + 可选报告标题", required = true)
            @Valid @RequestBody LearningAnalysisCreateDTO dto) {
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户
        return Result.success("讲评生成成功", learningAnalysisService.generateAnalysis(dto));
    }

    /**
     * 3. 生成讲评 PPT 课件，返回 PPT 下载地址。
     *
     * PPT 上传到私有 MinIO 桶，返回短时效签名地址；过期后重新调用本接口即可。
     * 如果报告还没有讲评正文，会提示先调用接口 2。
     */
    @PostMapping("/reports/{reportId}/ppt")
    @Operation(summary = "生成讲评PPT课件",
            description = "根据报告已生成的讲评正文生成pptx并上传MinIO，返回短时效下载地址")
    @OperationLog(module = "学情分析", action = "生成讲评PPT")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    public Result<PptDownloadVO> generatePpt(
            @Parameter(description = "学情分析报告ID", required = true, example = "1")
            @PathVariable Long reportId) {
        // 数据权限（占位）：校验报告所属课程的任课教师是否为当前用户
        return Result.success("PPT生成成功", learningAnalysisService.generatePpt(reportId));
    }

    /**
     * 4. 学生个人学情卡片查询。
     *
     * 返回个人分数、班级/试卷排名、班级均分对比、薄弱题目、知识点掌握情况与学习建议。
     * 学生角色只能查询本人（行级校验占位），教师/管理员可查询所授课程的学生。
     */
    @GetMapping("/papers/{paperId}/students/{studentId}/card")
    @Operation(summary = "学生个人学情卡片",
            description = "查询指定学生在指定试卷上的成绩、排名、薄弱题目、知识点掌握与学习建议")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN", "ROLE_STUDENT"})
    public Result<StudentLearningCardVO> studentCard(
            @Parameter(description = "试卷ID", required = true, example = "3")
            @PathVariable Long paperId,
            @Parameter(description = "学生ID（sys_user.id）", required = true, example = "12")
            @PathVariable Long studentId) {
        // 数据权限（占位）：
        // 1. 教师角色：校验 paper.courseId 所属课程的任课教师是否为当前用户；
        // 2. 学生角色：校验 studentId 是否等于当前登录用户，禁止查看他人卡片。
        return Result.success(learningAnalysisService.studentCard(paperId, studentId));
    }
}
