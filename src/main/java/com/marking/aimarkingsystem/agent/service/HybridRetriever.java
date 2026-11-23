package com.marking.aimarkingsystem.agent.service;

import com.marking.aimarkingsystem.agent.embedding.EmbeddingClient;
import com.marking.aimarkingsystem.agent.entity.AgentKnowledgeDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 混合检索：BM25 词法召回 + Embedding 向量召回，RRF 融合后输出 Top3。
 *
 * 分层设计（对应面试里的"检索架构"）：
 * 1. 权限过滤：先按课程 + 题型取候选集，两路召回共用，跨课程数据直接排除；
 * 2. 并行召回：BM25 与向量同时跑，谁慢等谁，但都有超时；
 * 3. 融合重排：用 RRF（Reciprocal Rank Fusion）按排名融合，不依赖两路分数量纲；
 * 4. 降级：embedding 未配置/超时/报错时，直接返回 BM25 结果，并标记 degraded。
 *
 * 为什么用 RRF 而不是加权求和：
 * BM25 分数没有上界，余弦在 -1~1，直接相加需要拍脑袋归一化；
 * RRF 只看名次，对两路分数量纲不敏感，工程上更稳。
 */
@Service
public class HybridRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);

    /** RRF 常数，取常用值 60 */
    private static final int RRF_K = 60;

    /** 余弦低于该值视为不相关，避免把噪声文档融合进来 */
    private static final double MIN_VECTOR_SCORE = 0.2;

    /** 单路召回候选数 */
    private static final int RECALL_SIZE = 10;

    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreService vectorStoreService;

    public HybridRetriever(KnowledgeBaseService knowledgeBaseService,
                           EmbeddingClient embeddingClient,
                           VectorStoreService vectorStoreService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.embeddingClient = embeddingClient;
        this.vectorStoreService = vectorStoreService;
    }

    public HybridResult search(String query, Long courseId, Integer questionType, int limit) {
        int topN = limit <= 0 ? 3 : limit;
        List<AgentKnowledgeDoc> candidates = knowledgeBaseService.candidates(courseId, questionType);
        if (candidates.isEmpty()) {
            return new HybridResult(List.of(), false, false, "知识库为空");
        }

        // 第一路：BM25，CPU 计算，放到公共线程池与向量召回并行
        CompletableFuture<List<AgentKnowledgeDoc>> bm25Future = CompletableFuture.supplyAsync(
                () -> knowledgeBaseService.bm25Search(query, candidates, RECALL_SIZE));

        // 未配置 embedding：直接走 BM25，属于预期降级，不算异常
        if (!vectorStoreService.available()) {
            List<AgentKnowledgeDoc> bm25 = safeGet(bm25Future, List.of());
            return new HybridResult(bm25, false, true, "Embedding 未配置，已降级为纯 BM25");
        }

        // 第二路：向量召回（embedding HTTP + 本地余弦）
        CompletableFuture<List<VectorStoreService.VectorHit>> vectorFuture = CompletableFuture.supplyAsync(() -> {
            float[] queryVector = embeddingClient.embed(query);
            List<Long> ids = candidates.stream().map(AgentKnowledgeDoc::getId).toList();
            return vectorStoreService.search(queryVector, ids, RECALL_SIZE);
        });

        List<AgentKnowledgeDoc> bm25Docs = safeGet(bm25Future, List.of());
        List<VectorStoreService.VectorHit> vectorHits;
        try {
            vectorHits = vectorFuture.get(6, TimeUnit.SECONDS);
        } catch (Exception e) {
            vectorFuture.cancel(true);
            log.warn("向量召回失败，降级为 BM25: {}", e.getMessage());
            return new HybridResult(bm25Docs, false, true, "向量召回异常，已降级为纯 BM25");
        }

        if (vectorHits.isEmpty()) {
            return new HybridResult(bm25Docs, false, true, "向量索引为空，已降级为纯 BM25（可调用重建索引接口）");
        }

        Map<Long, AgentKnowledgeDoc> docMap = vectorStoreService.toDocMap(candidates);
        Map<Long, Double> fused = new HashMap<>();
        for (int i = 0; i < bm25Docs.size(); i++) {
            fused.merge(bm25Docs.get(i).getId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }
        for (int i = 0; i < vectorHits.size(); i++) {
            VectorStoreService.VectorHit hit = vectorHits.get(i);
            if (hit.score() < MIN_VECTOR_SCORE) {
                continue;
            }
            fused.merge(hit.docId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }

        List<AgentKnowledgeDoc> merged = new ArrayList<>();
        fused.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(topN)
                .forEach(entry -> {
                    AgentKnowledgeDoc doc = docMap.get(entry.getKey());
                    if (doc != null) {
                        merged.add(doc);
                    }
                });

        if (merged.isEmpty()) {
            return new HybridResult(bm25Docs, false, true, "向量结果与词法结果均未命中，已回退 BM25");
        }
        return new HybridResult(merged, true, false, "BM25 + 向量混合召回");
    }

    private List<AgentKnowledgeDoc> safeGet(CompletableFuture<List<AgentKnowledgeDoc>> future,
                                            List<AgentKnowledgeDoc> fallback) {
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            return fallback;
        }
    }

    /**
     * 检索结果。
     *
     * @param docs      最终 TopN
     * @param vectorUsed 是否真正用到了向量召回
     * @param degraded  是否发生降级
     * @param note      降级原因，写进 Observation 让模型知道检索范围可能不完整
     */
    public record HybridResult(List<AgentKnowledgeDoc> docs,
                               boolean vectorUsed,
                               boolean degraded,
                               String note) {
    }
}
