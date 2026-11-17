package com.marking.aimarkingsystem.paper.controller;

import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.result.PageResult;
import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.paper.dto.PaperSaveDTO;
import com.marking.aimarkingsystem.paper.service.PaperService;
import com.marking.aimarkingsystem.paper.vo.PaperVO;
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
 * 试卷管理接口。
 *
 * 两种作答模式（answerMode）：
 * 1 线上作答：必须设置开考/收卷时间，学生登录后在线答题；
 * 2 纸质线下阅卷：时间字段可空，答题卡扫描件由后续答卷模块处理。
 *
 * 权限：管理员与教师可访问。
 *
 * 数据权限（占位，暂不实现）：
 * 教师只能操作自己课程下的试卷（exam_paper.course_id 属于本人课程）。
 * 完整实现时在分页查询中先查本人 courseId 集合再 in 过滤，单条操作则校验试卷归属。
 */
@Tag(name = "试卷管理", description = "试卷创建、草稿编辑、分页查询与状态流转")
@RestController
@RequestMapping("/papers")
@Validated
public class PaperController {

    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    @Operation(summary = "新增试卷",
            description = "创建后状态固定为 0（草稿）；status、总分、题数、创建人均由服务端决定")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PostMapping
    public Result<Long> createPaper(@Valid @RequestBody PaperSaveDTO dto) {
        return Result.success("新增成功", paperService.createPaper(dto));
    }

    @Operation(summary = "编辑试卷（草稿保存）",
            description = "只有草稿状态可编辑；已录入题目时禁止切换作答模式")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PutMapping("/{id}")
    public Result<Void> saveDraft(
            @Parameter(description = "试卷ID") @PathVariable Long id,
            @Valid @RequestBody PaperSaveDTO dto) {
        paperService.saveDraft(id, dto);
        return Result.success();
    }

    @Operation(summary = "分页查询试卷",
            description = "keyword 同时模糊匹配标题与试卷编号；courseId、status、answerMode 可为空")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @GetMapping("/page")
    public Result<PageResult<PaperVO>> pagePapers(
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页条数，最大 100") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @Parameter(description = "所属课程ID") @RequestParam(required = false) @Min(1) Long courseId,
            @Parameter(description = "试卷标题/编号关键字") @RequestParam(required = false) @Size(max = 64) String keyword,
            @Parameter(description = "状态：0 草稿，1 待考试，2 阅卷中，3 已归档")
            @RequestParam(required = false) @Min(0) @Max(3) Integer status,
            @Parameter(description = "作答模式：1 线上作答，2 纸质线下阅卷")
            @RequestParam(required = false) @Min(1) @Max(2) Integer answerMode) {
        return Result.success(paperService.pagePapers(
                pageNum, pageSize, courseId, keyword, status, answerMode));
    }

    @Operation(summary = "修改试卷状态",
            description = "只允许 0草稿 -> 1待考试 -> 2阅卷中 -> 3已归档 单向前进；"
                    + "进入待考试前校验题目、总分与线上模式的考试时间")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(
            @Parameter(description = "试卷ID") @PathVariable Long id,
            @Parameter(description = "目标状态：1 待考试，2 阅卷中，3 已归档")
            @RequestParam @Min(0) @Max(3) Integer status) {
        paperService.changeStatus(id, status);
        return Result.success();
    }
}
