package com.marking.aimarkingsystem.agent.service;

import com.marking.aimarkingsystem.agent.core.AgentResult;
import com.marking.aimarkingsystem.agent.core.AgentTraceContext;
import com.marking.aimarkingsystem.agent.core.ReActAgentExecutor;
import com.marking.aimarkingsystem.agent.dto.AgentChatRequest;
import com.marking.aimarkingsystem.agent.tool.AgentToolRegistry;
import com.marking.aimarkingsystem.agent.vo.AgentChatVO;
import com.marking.aimarkingsystem.agent.vo.AgentStepVO;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 教师助手 Agent（方案 B）。
 *
 * 场景：教师在阅卷详情页问"这题为什么扣 3 分""帮我写段讲评"，
 * Agent 自己决定查答卷总览、查某道题、还是检索知识库，再基于真实数据回答。
 *
 * 为什么比"把答卷塞进 prompt 直接问"更好：
 * - 答卷逐题内容很长，全塞进去既贵又容易超上下文；
 * - 工具按需取数，模型只在需要时查某一道题，成本和可解释性都更好；
 * - 返回 steps 轨迹，教师能看到回答依据来自哪次查询。
 *
 * 无状态会话：
 * 前端回传最近几轮对话，后端只取最近 5 轮拼进 prompt，多的直接丢，
 * 既不依赖服务端 session，也不会让历史无限膨胀。
 */
@Service
public class TeacherAssistantAgentService {

    /** 最多回传的历史轮数，防止多轮对话把 prompt 撑大 */
    private static final int MAX_HISTORY_TURNS = 5;

    /** 轨迹里的 Agent 类型 */
    private static final String AGENT_TYPE = "TEACHER_CHAT";

    private final ReActAgentExecutor executor;
    private final AgentToolRegistry toolRegistry;
    private final AgentTraceService traceService;

    public TeacherAssistantAgentService(ReActAgentExecutor executor,
                                        AgentToolRegistry toolRegistry,
                                        AgentTraceService traceService) {
        this.executor = executor;
        this.toolRegistry = toolRegistry;
        this.traceService = traceService;
    }

    public AgentChatVO chat(AgentChatRequest request, String username) {
        String runNo = traceService.newRunNo("AC");
        AgentTraceContext trace = new AgentTraceContext(
                runNo, AGENT_TYPE, request.getAnswerId(), null, traceService);

        String systemPrompt = buildSystemPrompt(request.getAnswerId());
        String userPrompt = buildUserPrompt(request);
        try {
            AgentResult result = executor.run(username, systemPrompt, userPrompt, toolRegistry, trace, 1);
            AgentChatVO vo = new AgentChatVO();
            vo.setRunNo(runNo);
            vo.setReply(result.answer());
            List<AgentStepVO> steps = result.steps().stream().map(AgentStepVO::from).toList();
            vo.setSteps(steps);
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("AI 助教调用失败：" + e.getMessage());
        }
    }

    private String buildSystemPrompt(Long answerId) {
        return "你是一名面向高校教师的 AI 助教，可以调用工具查询真实数据。\n"
                + "可用工具：\n" + toolRegistry.describe() + "\n"
                + "【强制约束】\n"
                + "1. 回答只能基于工具返回的数据，禁止使用模型自身记忆编造分数、题目、学生答案或评分点；\n"
                + "2. 整体性问题先调用 get_answer_overview；追问某道题调用 get_question_detail；\n"
                + "3. 需要评分规范、知识点讲解或讲评建议时调用 search_knowledge；\n"
                + "4. 工具没有返回相关信息时，直接回答“资料不足，无法判定”，不要猜；\n"
                + "5. 每次只调用一个工具，当前答卷ID固定为 " + answerId + "；\n"
                + "6. 用中文回答，先给结论再给依据，控制在 300 字以内。\n"
                + "输出协议（严格 JSON，不要 markdown）：\n"
                + "{\"thought\":\"简短思考\",\"action\":\"工具名\",\"actionInput\":{...}}\n"
                + "或\n"
                + "{\"thought\":\"简短思考\",\"finalAnswer\":\"给教师的回答\"}";
    }

    private String buildUserPrompt(AgentChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前答卷ID：").append(request.getAnswerId()).append("\n");
        List<AgentChatRequest.ChatTurn> history = request.getHistory();
        if (history != null && !history.isEmpty()) {
            sb.append("\n历史对话（只保留最近").append(MAX_HISTORY_TURNS).append("轮）：\n");
            // 服务端再次截断：即使前端多传，也只取最后 5 轮
            int start = Math.max(0, history.size() - MAX_HISTORY_TURNS);
            for (int i = start; i < history.size(); i++) {
                AgentChatRequest.ChatTurn turn = history.get(i);
                if (turn == null || !StringUtils.hasText(turn.getContent())) {
                    continue;
                }
                String role = "assistant".equalsIgnoreCase(turn.getRole()) ? "助手" : "教师";
                sb.append(role).append("：").append(shorten(turn.getContent())).append("\n");
            }
        }
        sb.append("\n教师最新问题：").append(request.getMessage());
        return sb.toString();
    }

    /** 单条历史过长时截断，避免某一条讲评把上下文占满 */
    private String shorten(String content) {
        final int maxLength = 500;
        return content.length() <= maxLength ? content : content.substring(0, maxLength) + "...";
    }
}
