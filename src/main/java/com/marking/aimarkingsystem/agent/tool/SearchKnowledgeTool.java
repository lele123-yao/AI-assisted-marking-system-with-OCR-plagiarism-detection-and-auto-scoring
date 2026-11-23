package com.marking.aimarkingsystem.agent.tool;

import cn.hutool.json.JSONObject;
import com.marking.aimarkingsystem.agent.entity.AgentKnowledgeDoc;
import com.marking.aimarkingsystem.agent.service.HybridRetriever;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 工具：检索教师知识库（混合检索入口）。
 *
 * 检索细节全部收在 HybridRetriever 里：
 * - 先按课程 + 题型过滤候选，跨课程数据在召回前就排除；
 * - BM25 与 embedding 向量并行召回，RRF 融合取 Top3；
 * - embedding 未配置/异常时自动降级纯 BM25，工具层拿到的永远是可用的结果。
 *
 * 工具本身不感知底层是向量还是关键词，只负责把 query 变成一段模型能读的文本。
 * 这也是"面向接口开发"的体现：以后把 MySQL 向量表换成 pgvector，只改检索实现。
 */
@Component
public class SearchKnowledgeTool implements AgentTool {

    private static final int MAX_CONTENT_LENGTH = 800;

    private final HybridRetriever hybridRetriever;
    private final AnswerContextProvider contextProvider;

    public SearchKnowledgeTool(HybridRetriever hybridRetriever,
                               AnswerContextProvider contextProvider) {
        this.hybridRetriever = hybridRetriever;
        this.contextProvider = contextProvider;
    }

    @Override
    public String name() {
        return "search_knowledge";
    }

    @Override
    public String description() {
        return "检索评分规范、知识点讲解、讲评建议等知识库文档（BM25 + 向量混合召回，按课程与题型过滤）";
    }

    @Override
    public String argumentHint() {
        return "{\"query\": \"检索关键词\", \"answerId\": 数字（可选，用于限定课程）, "
                + "\"questionId\": 数字（可选，用于限定题型）}";
    }

    @Override
    public List<String> requiredArgs() {
        return List.of("query");
    }

    @Override
    public String execute(JSONObject input) {
        String query = input.getStr("query");
        Long answerId = input.getLong("answerId");
        Long questionId = input.getLong("questionId");

        Long courseId = answerId == null ? null : contextProvider.courseIdOfAnswer(answerId);
        Integer questionType = null;
        if (answerId != null && questionId != null) {
            questionType = contextProvider.questionOf(answerId, questionId).getQuestionType();
        }

        HybridRetriever.HybridResult result = hybridRetriever.search(query, courseId, questionType, 3);
        List<AgentKnowledgeDoc> docs = result.docs();
        if (docs.isEmpty()) {
            return "知识库中没有找到与「" + query + "」相关的内容。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("检索到 ").append(docs.size()).append(" 条知识（")
                .append(result.note()).append("）：\n");
        for (int i = 0; i < docs.size(); i++) {
            AgentKnowledgeDoc doc = docs.get(i);
            sb.append("\n[").append(i + 1).append("] ").append(doc.getTitle());
            if (StringUtils.hasText(doc.getTags())) {
                sb.append("（标签：").append(doc.getTags()).append("）");
            }
            sb.append("\n").append(shorten(doc.getContent())).append("\n");
        }
        return sb.toString();
    }

    private String shorten(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= MAX_CONTENT_LENGTH
                ? content : content.substring(0, MAX_CONTENT_LENGTH) + "...";
    }
}
