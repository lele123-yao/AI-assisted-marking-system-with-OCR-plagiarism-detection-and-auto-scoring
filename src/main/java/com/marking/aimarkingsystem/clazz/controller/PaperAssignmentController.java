package com.marking.aimarkingsystem.clazz.controller;

import com.marking.aimarkingsystem.clazz.dto.PaperAssignDTO;
import com.marking.aimarkingsystem.clazz.service.PaperAssignmentService;
import com.marking.aimarkingsystem.clazz.vo.PaperAssignmentVO;
import com.marking.aimarkingsystem.common.annotation.OperationLog;
import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 试卷下发接口（管理员 / 教师）。
 *
 * 教师只能下发自己课程下、且处于待考试或阅卷中的试卷。
 */
@Tag(name = "试卷下发", description = "把试卷下发到班级或指定学生")
@RestController
@RequestMapping("/paper-assignment")
@Validated
public class PaperAssignmentController {

    private final PaperAssignmentService paperAssignmentService;

    public PaperAssignmentController(PaperAssignmentService paperAssignmentService) {
        this.paperAssignmentService = paperAssignmentService;
    }

    @Operation(summary = "下发试卷",
            description = "classId 与 studentIds 可同时提交；重复目标自动跳过，返回本次新增条数")
    @OperationLog(module = "试卷下发", action = "下发试卷")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PostMapping
    public Result<Integer> assign(@Valid @RequestBody PaperAssignDTO dto) {
        return Result.success("下发成功", paperAssignmentService.assign(dto));
    }

    @Operation(summary = "查看某试卷的下发记录")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @GetMapping("/paper/{paperId}")
    public Result<List<PaperAssignmentVO>> pageByPaper(@PathVariable Long paperId) {
        return Result.success(paperAssignmentService.pageByPaper(paperId));
    }

    @Operation(summary = "撤销下发")
    @OperationLog(module = "试卷下发", action = "撤销下发")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @DeleteMapping("/{id}")
    public Result<Void> cancel(@PathVariable Long id) {
        paperAssignmentService.cancel(id);
        return Result.success();
    }
}
