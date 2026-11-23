package com.marking.aimarkingsystem.agent.controller;

import com.marking.aimarkingsystem.agent.dto.AgentChatRequest;
import com.marking.aimarkingsystem.agent.dto.AgentReviewRequest;
import com.marking.aimarkingsystem.agent.dto.KnowledgeDocSaveDTO;
import com.marking.aimarkingsystem.agent.entity.AgentKnowledgeDoc;
import com.marking.aimarkingsystem.agent.entity.AgentRunTrace;
import com.marking.aimarkingsystem.agent.service.AgentTraceService;
import com.marking.aimarkingsystem.agent.service.KnowledgeBaseService;
import com.marking.aimarkingsystem.agent.service.MarkingReviewAgentService;
import com.marking.aimarkingsystem.agent.service.TeacherAssistantAgentService;
import com.marking.aimarkingsystem.agent.vo.AgentChatVO;
import com.marking.aimarkingsystem.agent.vo.AgentReviewVO;
import com.marking.aimarkingsystem.common.annotation.RateLimit;
import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 接口（手写 ReAct 实现，不依赖 Spring AI）。
 *
 * 三个能力：
 * 1. 阅卷 Agent：教师对某道题发起深度复核，返回初评、自检后的定稿与调用轨迹；
 * 2. 教师助手 Agent：带当前答卷上下文的多轮问答，按需查题目、查知识库；
 * 3. 知识库维护：新增 / 查看文档，供 RAG 检索。
 *
 * 权限：教师与管理员。数据权限与项目其他接口保持一致（当前为占位，未做课程归属过滤）。
 * 限流：Agent 每次请求会调用多次大模型，成本远高于普通接口，因此单独限流。
 */
@Tag(name = "AI Agent", description = "阅卷复核 Agent、教师助手 Agent、知识库")
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final MarkingReviewAgentService markingReviewAgentService;
    private final TeacherAssistantAgentService teacherAssistantAgentService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AgentTraceService traceService;

    public AgentController(MarkingReviewAgentService markingReviewAgentService,
                           TeacherAssistantAgentService teacherAssistantAgentService,
                           KnowledgeBaseService knowledgeBaseService,
                           AgentTraceService traceService) {
        this.markingReviewAgentService = markingReviewAgentService;
        this.teacherAssistantAgentService = teacherAssistantAgentService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.traceService = traceService;
    }

    @Operation(summary = "阅卷 Agent 复核单题",
            description = "主 Agent 查题目、细则、案例后初评；Checker 规则自检，最多打回修正 2 轮；"
                    + "返回初评、定稿、自检问题与完整工具轨迹")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @RateLimit(key = "agent:review", limit = 10, windowSeconds = 60,
            message = "AI 复核过于频繁，请稍后再试")
    @PostMapping("/marking/review")
    public Result<AgentReviewVO> reviewQuestion(@Valid @RequestBody AgentReviewRequest request) {
        return Result.success(markingReviewAgentService.review(
                request.getAnswerId(), request.getQuestionId(), currentUsername()));
    }

    @Operation(summary = "教师助手对话",
            description = "带当前答卷上下文的多轮问答；Agent 会按需查询答卷、题目或知识库，并返回调用轨迹")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @RateLimit(key = "agent:chat", limit = 20, windowSeconds = 60,
            message = "AI 助教调用过于频繁，请稍后再试")
    @PostMapping("/chat")
    public Result<AgentChatVO> chat(@Valid @RequestBody AgentChatRequest request) {
        return Result.success(teacherAssistantAgentService.chat(request, currentUsername()));
    }

    @Operation(summary = "查看知识库文档", description = "按课程筛选；不传 courseId 返回全部")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @GetMapping("/knowledge")
    public Result<List<AgentKnowledgeDoc>> listKnowledge(
            @Parameter(description = "课程ID，可选") @RequestParam(required = false) Long courseId) {
        return Result.success(knowledgeBaseService.list(courseId));
    }

    @Operation(summary = "新增知识库文档",
            description = "courseId 为空表示全局文档；文档会参与 search_knowledge 的 BM25 检索")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @PostMapping("/knowledge")
    public Result<AgentKnowledgeDoc> saveKnowledge(@Valid @RequestBody KnowledgeDocSaveDTO dto) {
        return Result.success("保存成功", knowledgeBaseService.save(dto));
    }

    @Operation(summary = "编辑知识库文档",
            description = "更新课程、标题、标签、题型与正文；若 embedding 已启用会同步重建向量，"
                    + "重建失败会清理旧向量，检索退回纯 BM25")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @PutMapping("/knowledge/{id}")
    public Result<AgentKnowledgeDoc> updateKnowledge(@PathVariable Long id,
                                                     @Valid @RequestBody KnowledgeDocSaveDTO dto) {
        return Result.success("修改成功", knowledgeBaseService.update(id, dto));
    }

    @Operation(summary = "删除知识库文档", description = "逻辑删除文档并清理对应向量，删除后不再参与检索")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @DeleteMapping("/knowledge/{id}")
    public Result<Void> deleteKnowledge(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "重建知识库向量索引",
            description = "文档变更或 embedding 服务刚接入时手动触发；未配置 embedding 时返回 0，"
                    + "检索仍可走纯 BM25")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @RateLimit(key = "agent:reindex", limit = 2, windowSeconds = 60,
            message = "重建索引过于频繁，请稍后再试")
    @PostMapping("/knowledge/reindex")
    public Result<Integer> reindexKnowledge() {
        return Result.success("重建完成", knowledgeBaseService.reindexAll());
    }

    @Operation(summary = "按运行编号查询推理轨迹",
            description = "返回该次运行的全部 Thought / Action / Observation 与最终结论，用于可追溯与审计")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @GetMapping("/trace/{runNo}")
    public Result<List<AgentRunTrace>> traceByRunNo(@PathVariable String runNo) {
        return Result.success(traceService.listByRunNo(runNo));
    }

    @Operation(summary = "按答卷查询推理轨迹",
            description = "返回该答卷最近 200 条轨迹，便于教师回看某次复核的完整过程")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @GetMapping("/trace")
    public Result<List<AgentRunTrace>> traceByAnswer(
            @Parameter(description = "答卷ID") @RequestParam Long answerId) {
        return Result.success(traceService.listByAnswer(answerId));
    }

    /** Agent 使用触发人自己的大模型 Key（BYOK），因此必须从登录上下文取用户名 */
    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return authentication.getName();
    }
}
