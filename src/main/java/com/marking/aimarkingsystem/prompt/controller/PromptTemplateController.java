package com.marking.aimarkingsystem.prompt.controller;

import com.marking.aimarkingsystem.common.annotation.OperationLog;
import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.result.PageResult;
import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.prompt.dto.PromptTemplateSaveDTO;
import com.marking.aimarkingsystem.prompt.service.PromptTemplateService;
import com.marking.aimarkingsystem.prompt.vo.PromptTemplateVO;
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

/**
 * AI 评分提示词模板接口。
 *
 * 模板范围：courseId 为空表示全校通用，questionType = 0 表示通用题型。
 * 版本：新增固定 version=1，编辑时版本号自增，便于追溯某次阅卷使用的提示词版本。
 *
 * 权限：管理员与教师可访问。
 *
 * 数据权限（占位，暂不实现）：
 * 全校通用模板应仅管理员可维护；教师只能维护自己课程的专属模板。
 */
@Tag(name = "评分提示词模板", description = "按题型维护 AI 评分 Prompt")
@RestController
@RequestMapping("/prompt-templates")
@Validated
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    public PromptTemplateController(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @Operation(summary = "新增模板",
            description = "模板编码唯一；userPrompt 必填，支持 {{question}} 等占位符")
    @OperationLog(module = "提示词模板", action = "新增模板")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PromptTemplateSaveDTO dto) {
        return Result.success("新增成功", promptTemplateService.create(dto));
    }

    @Operation(summary = "编辑模板",
            description = "修改后版本号自动 +1，用于追溯评分口径的历史版本")
    @OperationLog(module = "提示词模板", action = "编辑模板")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PutMapping("/{id}")
    public Result<Void> update(
            @Parameter(description = "模板ID") @PathVariable Long id,
            @Valid @RequestBody PromptTemplateSaveDTO dto) {
        promptTemplateService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "分页查询模板",
            description = "questionType 传 0 表示只查通用模板；courseId 为空不限制课程范围")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @GetMapping("/page")
    public Result<PageResult<PromptTemplateVO>> pageTemplates(
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页条数，最大 100") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @Parameter(description = "适用题型：0 通用，1-8 具体题型")
            @RequestParam(required = false) @Min(0) @Max(8) Integer questionType,
            @Parameter(description = "限定课程ID，不传不限制") @RequestParam(required = false) @Min(1) Long courseId,
            @Parameter(description = "状态：1 启用，0 停用") @RequestParam(required = false) @Min(0) @Max(1) Integer status) {
        return Result.success(promptTemplateService.pageTemplates(
                pageNum, pageSize, questionType, courseId, status));
    }

    @Operation(summary = "删除模板",
            description = "已被题目引用的模板不允许删除，建议改为停用")
    @OperationLog(module = "提示词模板", action = "删除模板")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "模板ID") @PathVariable Long id) {
        promptTemplateService.delete(id);
        return Result.success();
    }
}
