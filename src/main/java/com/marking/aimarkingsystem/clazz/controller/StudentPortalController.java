package com.marking.aimarkingsystem.clazz.controller;

import com.marking.aimarkingsystem.clazz.service.StudentPortalService;
import com.marking.aimarkingsystem.clazz.vo.StudentPaperVO;
import com.marking.aimarkingsystem.clazz.vo.StudentScoreVO;
import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生端门户接口：我的试卷、我的成绩。
 *
 * 数据范围固定为当前登录学生本人，不接受前端传入学生ID。
 */
@Tag(name = "学生端", description = "我的试卷与我的成绩")
@RestController
@RequestMapping("/student")
public class StudentPortalController {

    private final StudentPortalService studentPortalService;

    public StudentPortalController(StudentPortalService studentPortalService) {
        this.studentPortalService = studentPortalService;
    }

    @Operation(summary = "我的试卷",
            description = "返回下发到本人所在班级或直接指定给本人的试卷，附带是否已提交")
    @RequiresPermission("ROLE_STUDENT")
    @GetMapping("/papers")
    public Result<List<StudentPaperVO>> myPapers() {
        return Result.success(studentPortalService.myPapers());
    }

    @Operation(summary = "我的成绩", description = "返回本人的阅卷成绩，教师最终分为准")
    @RequiresPermission("ROLE_STUDENT")
    @GetMapping("/scores")
    public Result<List<StudentScoreVO>> myScores() {
        return Result.success(studentPortalService.myScores());
    }
}
