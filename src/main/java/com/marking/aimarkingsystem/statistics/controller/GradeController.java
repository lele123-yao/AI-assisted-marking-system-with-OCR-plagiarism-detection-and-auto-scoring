package com.marking.aimarkingsystem.statistics.controller;

import com.marking.aimarkingsystem.common.annotation.OperationLog;
import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.statistics.dto.GradeReviewDTO;
import com.marking.aimarkingsystem.statistics.service.GradeReviewService;
import com.marking.aimarkingsystem.statistics.service.GradeStatisticsService;
import com.marking.aimarkingsystem.statistics.vo.GradeStatisticsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成绩复核与统计接口（教师端）。
 *
 * 接口范围：
 * 1. 成绩复核：通过 / 改分 / 驳回重批，纸质答卷必须人工录入最终分；
 * 2. 试卷成绩统计：最高分、最低分、平均分、中位数、及格率、优秀率、分数段分布；
 * 3. 导出原始成绩 Excel：线上与纸质答卷统一列。
 *
 * 权限：教师与管理员可访问，学生无权查看他人成绩。
 *
 * 数据权限（占位，暂不实现）：
 * 任课教师只能复核、统计自己所授课程下的试卷成绩，
 * 实现时应由 paper.courseId 找到 course.teacherId 与当前登录用户比对。
 */
@Tag(name = "成绩复核与统计", description = "成绩复核、试卷成绩统计、成绩单导出")
@RestController
@RequestMapping("/grades")
@Validated
public class GradeController {

    private final GradeReviewService gradeReviewService;
    private final GradeStatisticsService gradeStatisticsService;

    public GradeController(GradeReviewService gradeReviewService,
                           GradeStatisticsService gradeStatisticsService) {
        this.gradeReviewService = gradeReviewService;
        this.gradeStatisticsService = gradeStatisticsService;
    }

    @Operation(summary = "成绩复核",
            description = "reviewAction=1 复核通过（纸质答卷必须已有教师最终分）；"
                    + "reviewAction=2 改分（必须提交 finalScore，且不能与 AI 参考分相同）；"
                    + "reviewAction=3 驳回重批（清空最终分，退回待教师确认）")
    @OperationLog(module = "成绩管理", action = "成绩复核")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @PostMapping("/reviews")
    public Result<Void> review(@Valid @RequestBody GradeReviewDTO dto) {
        // 数据权限（占位）：校验答卷所属试卷的课程归属当前教师
        gradeReviewService.review(dto);
        return Result.success("复核已提交", null);
    }

    @Operation(summary = "试卷成绩统计",
            description = "统计已出分答卷的最高分、最低分、平均分、中位数、及格率、优秀率与分数段分布；"
                    + "未出分答卷计入总数但不参与均分计算")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @GetMapping("/papers/{paperId}/statistics")
    public Result<GradeStatisticsVO> statistics(
            @Parameter(description = "试卷ID", example = "3")
            @PathVariable @Min(value = 1, message = "试卷ID不合法") Long paperId) {
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户
        return Result.success(gradeStatisticsService.statistics(paperId));
    }

    @Operation(summary = "导出成绩单",
            description = "导出 xlsx 文件，线上与纸质答卷统一表头；该接口直接返回文件流，不使用 Result 包装")
    @OperationLog(module = "成绩管理", action = "导出成绩单", recordResult = false)
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @GetMapping("/papers/{paperId}/export")
    public void export(
            @Parameter(description = "试卷ID", example = "3")
            @PathVariable @Min(value = 1, message = "试卷ID不合法") Long paperId,
            HttpServletResponse response) {
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户
        gradeStatisticsService.exportExcel(paperId, response);
    }
}
