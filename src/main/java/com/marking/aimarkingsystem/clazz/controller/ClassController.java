package com.marking.aimarkingsystem.clazz.controller;

import com.marking.aimarkingsystem.clazz.dto.ClassSaveDTO;
import com.marking.aimarkingsystem.clazz.dto.ClassStudentAddDTO;
import com.marking.aimarkingsystem.clazz.service.ClassRosterImportService;
import com.marking.aimarkingsystem.clazz.service.ClassService;
import com.marking.aimarkingsystem.clazz.vo.ClassRosterImportResultVO;
import com.marking.aimarkingsystem.clazz.vo.ClassStudentVO;
import com.marking.aimarkingsystem.clazz.vo.ClassVO;
import com.marking.aimarkingsystem.common.annotation.OperationLog;
import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.result.PageResult;
import com.marking.aimarkingsystem.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 班级管理接口（管理员 / 教师）。
 *
 * 教师只能维护自己负责的班级，管理员可维护全部班级；
 * 具体归属校验在 ClassService 内完成。
 */
@Tag(name = "班级管理", description = "班级增删改查与班级学生管理")
@RestController
@RequestMapping("/class")
@Validated
public class ClassController {

    private final ClassService classService;

    private final ClassRosterImportService classRosterImportService;

    public ClassController(ClassService classService,
                           ClassRosterImportService classRosterImportService) {
        this.classService = classService;
        this.classRosterImportService = classRosterImportService;
    }

    @Operation(summary = "分页查询班级",
            description = "教师只返回自己负责的班级；keyword 模糊匹配班级名称与编码")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @GetMapping("/page")
    public Result<PageResult<ClassVO>> pageClasses(
            @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @Min(0) @Max(1) Integer status) {
        return Result.success(classService.pageClasses(pageNum, pageSize, keyword, status));
    }

    @Operation(summary = "新增班级", description = "教师新增时自动绑定自己为任课教师")
    @OperationLog(module = "班级管理", action = "新增班级")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PostMapping
    public Result<Long> addClass(@Valid @RequestBody ClassSaveDTO dto) {
        return Result.success("新增成功", classService.addClass(dto));
    }

    @Operation(summary = "编辑班级")
    @OperationLog(module = "班级管理", action = "编辑班级")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PutMapping("/{id}")
    public Result<Void> updateClass(@PathVariable Long id,
                                    @Valid @RequestBody ClassSaveDTO dto) {
        classService.updateClass(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除班级", description = "逻辑删除班级及其学生关系")
    @OperationLog(module = "班级管理", action = "删除班级")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @DeleteMapping("/{id}")
    public Result<Void> deleteClass(@PathVariable Long id) {
        classService.deleteClass(id);
        return Result.success();
    }

    @Operation(summary = "班级下拉选项")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @GetMapping("/options")
    public Result<List<ClassVO>> listOptions() {
        return Result.success(classService.listClassOptions());
    }

    @Operation(summary = "班级学生列表")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @GetMapping("/{id}/students")
    public Result<List<ClassStudentVO>> listStudents(@PathVariable Long id) {
        return Result.success(classService.listStudents(id));
    }

    @Operation(summary = "学生候选列表",
            description = "onlyAvailable=true（默认）只返回未加入任何班级的学生；"
                    + "onlyAvailable=false 返回全部学生账号，供额外指定下发使用")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @GetMapping("/student-options")
    public Result<List<ClassStudentVO>> studentOptions(
            @Parameter(description = "是否只返回未入班学生") @RequestParam(defaultValue = "true") boolean onlyAvailable,
            @RequestParam(required = false) String keyword) {
        return Result.success(onlyAvailable
                ? classService.listAvailableStudents(keyword)
                : classService.listAllStudents(keyword));
    }

    @Operation(summary = "批量添加学生到班级",
            description = "只允许 STUDENT 角色；学生已在其他班级时返回业务错误")
    @OperationLog(module = "班级管理", action = "添加班级学生")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PostMapping("/{id}/students")
    public Result<Integer> addStudents(@PathVariable Long id,
                                       @Valid @RequestBody ClassStudentAddDTO dto) {
        return Result.success("添加成功", classService.addStudents(id, dto.getStudentIds()));
    }

    @Operation(summary = "将学生移出班级")
    @OperationLog(module = "班级管理", action = "移出班级学生")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @DeleteMapping("/{id}/students/{studentId}")
    public Result<Void> removeStudent(@PathVariable Long id, @PathVariable Long studentId) {
        classService.removeStudent(id, studentId);
        return Result.success();
    }

    @Operation(summary = "Excel 名单导入班级",
            description = "Excel 第一行表头需为「学号」「姓名」；"
                    + "autoCreateUser=true 时学号不存在会自动创建学生账号（初始密码为学号后 6 位）。"
                    + "单行失败不会中断整批导入，返回失败明细")
    @OperationLog(module = "班级管理", action = "导入班级名单")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PostMapping("/{id}/students/import")
    public Result<ClassRosterImportResultVO> importStudents(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "autoCreateUser", defaultValue = "false") boolean autoCreateUser) {
        return Result.success("导入完成",
                classRosterImportService.importRoster(id, autoCreateUser, file));
    }
}
