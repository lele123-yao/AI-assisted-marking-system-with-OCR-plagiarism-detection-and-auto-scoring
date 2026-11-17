package com.marking.aimarkingsystem.paper.controller;

import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.paper.dto.QuestionAddDTO;
import com.marking.aimarkingsystem.paper.dto.QuestionKnowledgeAssignDTO;
import com.marking.aimarkingsystem.paper.service.QuestionService;
import com.marking.aimarkingsystem.paper.vo.QuestionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 题目管理接口。
 *
 * 业务约束：
 * - 只有草稿状态的试卷才能增删改题目；
 * - 主观题（题型 5~8）必须填写标准答案与评分细则；
 * - 题目与知识点是多对多，可通过 /knowledge 子接口单独维护标签。
 *
 * 权限：管理员与教师可访问。
 *
 * 数据权限（占位，暂不实现）：
 * 教师只能维护自己课程下试卷的题目，后续需由 paperId 反查试卷 → 课程归属校验。
 */
@Tag(name = "题目管理", description = "题目新增、编辑、删除、按试卷查询与知识点绑定")
@RestController
@RequestMapping("/questions")
@Validated
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @Operation(summary = "新增题目",
            description = "仅草稿试卷可加题；题号在试卷内唯一；knowledgeIds 可同时绑定知识点")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PostMapping
    public Result<Long> addQuestion(@Valid @RequestBody QuestionAddDTO dto) {
        return Result.success("新增成功", questionService.addQuestion(dto));
    }

    @Operation(summary = "编辑题目",
            description = "不允许移动到其他试卷；知识点按传入的 knowledgeIds 整体覆盖")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PutMapping("/{id}")
    public Result<Void> updateQuestion(
            @Parameter(description = "题目ID") @PathVariable Long id,
            @Valid @RequestBody QuestionAddDTO dto) {
        questionService.updateQuestion(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除题目",
            description = "逻辑删除题目并清理知识点关联，同时回写试卷总分与题数")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @DeleteMapping("/{id}")
    public Result<Void> deleteQuestion(@Parameter(description = "题目ID") @PathVariable Long id) {
        questionService.deleteQuestion(id);
        return Result.success();
    }

    @Operation(summary = "查询试卷下全部题目",
            description = "按排序号升序返回，已填充知识点ID/名称与提示词模板名称")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @GetMapping("/paper/{paperId}")
    public Result<List<QuestionVO>> listByPaper(
            @Parameter(description = "试卷ID") @PathVariable @Min(1) Long paperId) {
        return Result.success(questionService.listByPaper(paperId));
    }

    @Operation(summary = "维护题目知识点标签",
            description = "整体覆盖语义：传空数组表示清空该题目的全部知识点")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PutMapping("/{id}/knowledge")
    public Result<Void> assignKnowledge(
            @Parameter(description = "题目ID") @PathVariable Long id,
            @Valid @RequestBody QuestionKnowledgeAssignDTO dto) {
        questionService.assignKnowledge(id, dto.getKnowledgeIds());
        return Result.success();
    }
}
