package com.marking.aimarkingsystem.agent.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.agent.embedding.EmbeddingClient;
import com.marking.aimarkingsystem.agent.entity.AgentKnowledgeDoc;
import com.marking.aimarkingsystem.agent.entity.AgentKnowledgeVector;
import com.marking.aimarkingsystem.agent.mapper.AgentKnowledgeVectorMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量存储与相似度计算（MySQL 实现）。
 *
 * 取舍说明：
 * - pgvector 需要 PostgreSQL，本项目是 MySQL，换库会破坏现有业务；
 * - 知识库规模小，候选集最多几百篇，全部取出在应用层算余弦完全够用；
 * - 向量表只存 JSON 数组，不依赖任何扩展，部署成本为零；
 * - 后续迁移 pgvector 时，实现同一个 VectorStore 语义即可，上层不动。
 *
 * 所有方法对" embedding 未配置 / 调用失败"都保持静默降级：
 * 索引失败只打日志，检索返回空，让 HybridRetriever 退回 BM25。
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final AgentKnowledgeVectorMapper vectorMapper;
    private final EmbeddingClient embeddingClient;

    public VectorStoreService(AgentKnowledgeVectorMapper vectorMapper,
                              EmbeddingClient embeddingClient) {
        this.vectorMapper = vectorMapper;
        this.embeddingClient = embeddingClient;
    }

    public boolean available() {
        return embeddingClient.available();
    }

    /**
     * 为单篇文档建立向量索引。
     *
     * @return true 表示索引成功；false 表示未启用或失败（调用方无需处理）
     */
    public boolean index(AgentKnowledgeDoc doc) {
        if (!embeddingClient.available()) {
            return false;
        }
        try {
            String text = doc.getTitle() + "\n"
                    + (doc.getTags() == null ? "" : doc.getTags()) + "\n"
                    + doc.getContent();
            float[] vector = embeddingClient.embed(text);
            upsert(doc.getId(), vector);
            return true;
        } catch (Exception e) {
            log.warn("知识库向量索引失败，保留 BM25 可用: docId={}, error={}",
                    doc.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 在候选文档范围内做余弦相似度检索。
     *
     * @param queryVector 查询向量
     * @param candidateIds 已按课程/题型过滤后的文档ID
     * @param topN 返回条数
     */
    public List<VectorHit> search(float[] queryVector, List<Long> candidateIds, int topN) {
        if (queryVector == null || queryVector.length == 0 || candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        List<AgentKnowledgeVector> vectors = vectorMapper.selectList(
                Wrappers.<AgentKnowledgeVector>lambdaQuery()
                        .in(AgentKnowledgeVector::getDocId, candidateIds));
        if (vectors.isEmpty()) {
            return List.of();
        }

        List<VectorHit> hits = new ArrayList<>();
        for (AgentKnowledgeVector vector : vectors) {
            float[] stored = parse(vector.getEmbedding());
            if (stored.length != queryVector.length) {
                // 不同模型/维度不一致，直接跳过，避免算错相似度
                continue;
            }
            hits.add(new VectorHit(vector.getDocId(), cosine(queryVector, stored)));
        }
        hits.sort(Comparator.comparingDouble(VectorHit::score).reversed());
        return hits.subList(0, Math.min(topN, hits.size()));
    }

    /**
     * 删除文档对应的向量。
     * 文档编辑或删除时调用：编辑后重建失败会留下过期向量，必须先清掉再尝试重建。
     */
    public void remove(Long docId) {
        if (docId == null) {
            return;
        }
        try {
            vectorMapper.deleteById(docId);
        } catch (Exception e) {
            log.warn("知识库向量清理失败: docId={}, error={}", docId, e.getMessage());
        }
    }

    /** 批量重建索引，返回成功条数 */
    public int reindex(List<AgentKnowledgeDoc> docs) {
        int success = 0;
        for (AgentKnowledgeDoc doc : docs) {
            if (index(doc)) {
                success++;
            }
        }
        return success;
    }

    public Map<Long, AgentKnowledgeDoc> toDocMap(List<AgentKnowledgeDoc> docs) {
        Map<Long, AgentKnowledgeDoc> map = new HashMap<>();
        for (AgentKnowledgeDoc doc : docs) {
            map.put(doc.getId(), doc);
        }
        return map;
    }

    // ==================== 内部方法 ====================

    private void upsert(Long docId, float[] vector) {
        AgentKnowledgeVector record = new AgentKnowledgeVector();
        record.setDocId(docId);
        record.setModel(embeddingClient.model());
        record.setEmbedding(toJson(vector));
        record.setUpdateTime(LocalDateTime.now());

        AgentKnowledgeVector exists = vectorMapper.selectById(docId);
        if (exists == null) {
            vectorMapper.insert(record);
        } else {
            vectorMapper.updateById(record);
        }
    }

    private String toJson(float[] vector) {
        JSONArray array = new JSONArray();
        for (float value : vector) {
            array.add(value);
        }
        return array.toString();
    }

    private float[] parse(String json) {
        if (json == null || json.isBlank()) {
            return new float[0];
        }
        try {
            JSONArray array = JSONUtil.parseArray(json);
            float[] vector = new float[array.size()];
            for (int i = 0; i < array.size(); i++) {
                vector[i] = array.getFloat(i, 0f);
            }
            return vector;
        } catch (Exception e) {
            return new float[0];
        }
    }

    private double cosine(float[] left, float[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    public record VectorHit(Long docId, double score) {
    }
}
