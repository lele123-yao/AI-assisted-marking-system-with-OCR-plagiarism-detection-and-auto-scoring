package com.marking.aimarkingsystem.course.controller;

import com.marking.aimarkingsystem.common.annotation.OperationLog;
import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.result.PageResult;
import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.course.dto.CourseSaveDTO;
import com.marking.aimarkingsystem.course.service.CourseService;
import com.marking.aimarkingsystem.course.vo.CourseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程管理接口。
 *
 * 权限：管理员与教师均可访问（@RequiresPermission 为"任一命中"语义）。
 * 当前 token 中的权限只有 ROLE_xxx 角色码，因此这里用角色码保护；
 * 后续 sys_role_permission 落地后可替换为 course:add 这类权限码，无需改动切面。
 *
 * 数据权限（占位，暂不实现）：
 * 教师只能操作自己负责的课程（course.teacher_id = 当前用户）。
 * 完整实现时可在 Service 层按当前登录用户过滤，或在 PermissionAspect 中统一校验资源归属。
 */
@Tag(name = "课程管理", description = "课程新增、编辑、分页查询与归档")
@RestController
@RequestMapping("/courses")
@Validated
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @Operation(summary = "新增课程",
            description = "课程编码全局唯一；teacherId 必须是已存在的用户；状态不传默认 1（正常）")
    @OperationLog(module = "课程管理", action = "新增课程")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PostMapping
    public Result<Long> addCourse(@Valid @RequestBody CourseSaveDTO dto) {
        return Result.success("新增成功", courseService.addCourse(dto));
    }

    @Operation(summary = "编辑课程",
            description = "仅草稿状态无关，课程随时可编辑；课程编码修改后仍需全局唯一")
    @OperationLog(module = "课程管理", action = "编辑课程")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PutMapping("/{id}")
    public Result<Void> updateCourse(
            @Parameter(description = "课程ID") @PathVariable Long id,
            @Valid @RequestBody CourseSaveDTO dto) {
        courseService.updateCourse(id, dto);
        return Result.success();
    }

    @Operation(summary = "分页查询课程",
            description = "keyword 同时模糊匹配课程名称与编码；teacherId、status 可为空")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @GetMapping("/page")
    public Result<PageResult<CourseVO>> pageCourses(
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页条数，最大 100") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @Parameter(description = "课程名称/编码关键字") @RequestParam(required = false) @Size(max = 64) String keyword,
            @Parameter(description = "任课教师ID") @RequestParam(required = false) @Min(1) Long teacherId,
            @Parameter(description = "状态：1 正常，0 停用") @RequestParam(required = false) @Min(0) @Max(1) Integer status) {
        return Result.success(courseService.pageCourses(pageNum, pageSize, keyword, teacherId, status));
    }

    @Operation(summary = "归档课程",
            description = "课程下仍有未归档试卷时拒绝归档，需先归档全部试卷")
    @OperationLog(module = "课程管理", action = "归档课程")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PutMapping("/{id}/archive")
    public Result<Void> archiveCourse(@Parameter(description = "课程ID") @PathVariable Long id) {
        courseService.archiveCourse(id);
        return Result.success();
    }
}
