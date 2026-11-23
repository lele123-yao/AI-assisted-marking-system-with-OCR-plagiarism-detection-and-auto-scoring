package com.marking.aimarkingsystem.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.agent.core.AgentStep;
import com.marking.aimarkingsystem.agent.entity.AgentRunTrace;
import com.marking.aimarkingsystem.agent.mapper.AgentRunTraceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 推理轨迹持久化。
 *
 * 关键原则：轨迹写入失败不能影响主流程。
 * Agent 已经算出的分数比日志更重要，所以这里全部 try/catch，只打 warn。
 */
@Service
public class AgentTraceService {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceService.class);

    private static final DateTimeFormatter RUN_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AgentRunTraceMapper traceMapper;

    public AgentTraceService(AgentRunTraceMapper traceMapper) {
        this.traceMapper = traceMapper;
    }

    /** 生成一次运行的唯一编号，例如 AR20261122153000123 */
    public String newRunNo(String prefix) {
        return prefix + LocalDateTime.now().format(RUN_NO_FORMAT)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    /** 记录一次工具调用 */
    public void recordStep(String runNo, String agentType, Long answerId, Long questionId,
                           int attempt, int round, AgentStep step) {
        AgentRunTrace trace = base(runNo, agentType, answerId, questionId, attempt, round);
        trace.setThought(step.thought());
        trace.setToolName(step.tool());
        trace.setToolInput(step.input());
        trace.setObservation(step.observation());
        trace.setSuccess(1);
        save(trace);
    }

    /** 记录一次尝试的最终结论 */
    public void recordFinish(String runNo, String agentType, Long answerId, Long questionId,
                             int attempt, String finalAnswer, boolean success, long durationMs) {
        AgentRunTrace trace = base(runNo, agentType, answerId, questionId, attempt, 0);
        trace.setFinalAnswer(finalAnswer);
        trace.setSuccess(success ? 1 : 0);
        trace.setDurationMs(durationMs);
        save(trace);
    }

    public List<AgentRunTrace> listByRunNo(String runNo) {
        if (!StringUtils.hasText(runNo)) {
            return List.of();
        }
        return traceMapper.selectList(Wrappers.<AgentRunTrace>lambdaQuery()
                .eq(AgentRunTrace::getRunNo, runNo)
                .orderByAsc(AgentRunTrace::getAttemptNo)
                .orderByAsc(AgentRunTrace::getRoundNo));
    }

    public List<AgentRunTrace> listByAnswer(Long answerId) {
        if (answerId == null) {
            return List.of();
        }
        return traceMapper.selectList(Wrappers.<AgentRunTrace>lambdaQuery()
                .eq(AgentRunTrace::getAnswerId, answerId)
                .orderByDesc(AgentRunTrace::getId)
                .last("LIMIT 200"));
    }

    private AgentRunTrace base(String runNo, String agentType, Long answerId, Long questionId,
                               int attempt, int round) {
        AgentRunTrace trace = new AgentRunTrace();
        trace.setRunNo(runNo);
        trace.setAgentType(agentType);
        trace.setAnswerId(answerId);
        trace.setQuestionId(questionId);
        trace.setAttemptNo(attempt);
        trace.setRoundNo(round);
        return trace;
    }

    private void save(AgentRunTrace trace) {
        try {
            traceMapper.insert(trace);
        } catch (Exception e) {
            log.warn("Agent 轨迹写入失败，不影响主流程: runNo={}, error={}",
                    trace.getRunNo(), e.getMessage());
        }
    }
}
