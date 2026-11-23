package com.marking.aimarkingsystem.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.agent.dto.KnowledgeDocSaveDTO;
import com.marking.aimarkingsystem.agent.entity.AgentKnowledgeDoc;
import com.marking.aimarkingsystem.agent.mapper.AgentKnowledgeDocMapper;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识库 BM25 词法检索（混合检索的第一路）。
 *
 * 为什么保留 BM25：
 * - 专有名词、公式、评分术语需要精准匹配，词法召回比向量更稳；
 * - Embedding 服务不可用时它是唯一兜底，保证检索不挂；
 * - 二分词 + BM25 全手写，零依赖、可解释，面试能逐行讲。
 *
 * 分词策略：
 * - 连续中文切二元组（"椭圆方程" -> 椭圆/圆方/方程）；
 * - 英文与数字按单词切分并转小写；
 * - 单字仅在整段只有一个汉字时保留，避免"的/了"干扰。
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    /** 参与检索的最大文档数 */
    private static final int MAX_CANDIDATES = 500;

    /** 默认返回条数 */
    private static final int DEFAULT_LIMIT = 3;

    /** BM25 参数 */
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("[\\u4e00-\\u9fa5]+|[a-zA-Z0-9]+");

    private final AgentKnowledgeDocMapper docMapper;
    private final VectorStoreService vectorStoreService;

    public KnowledgeBaseService(AgentKnowledgeDocMapper docMapper,
                                VectorStoreService vectorStoreService) {
        this.docMapper = docMapper;
        this.vectorStoreService = vectorStoreService;
    }

    /** 新增文档；若 embedding 已配置，顺带建向量索引（失败不影响保存） */
    public AgentKnowledgeDoc save(KnowledgeDocSaveDTO dto) {
        AgentKnowledgeDoc doc = new AgentKnowledgeDoc();
        doc.setCourseId(dto.getCourseId());
        doc.setTitle(dto.getTitle().trim());
        doc.setContent(dto.getContent().trim());
        doc.setTags(StringUtils.hasText(dto.getTags()) ? dto.getTags().trim() : null);
        doc.setQuestionType(dto.getQuestionType());
        docMapper.insert(doc);
        vectorStoreService.index(doc);
        return doc;
    }

    /**
     * 编辑文档：字段更新后重建向量。
     * 先清理旧向量再重建，避免重建失败时继续使用过期向量。
     */
    public AgentKnowledgeDoc update(Long id, KnowledgeDocSaveDTO dto) {
        AgentKnowledgeDoc existing = docMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("知识库文档不存在");
        }
        String tags = StringUtils.hasText(dto.getTags()) ? dto.getTags().trim() : null;
        docMapper.update(null, Wrappers.<AgentKnowledgeDoc>lambdaUpdate()
                .eq(AgentKnowledgeDoc::getId, id)
                .set(AgentKnowledgeDoc::getCourseId, dto.getCourseId())
                .set(AgentKnowledgeDoc::getTitle, dto.getTitle().trim())
                .set(AgentKnowledgeDoc::getContent, dto.getContent().trim())
                .set(AgentKnowledgeDoc::getTags, tags)
                .set(AgentKnowledgeDoc::getQuestionType, dto.getQuestionType())
                .set(AgentKnowledgeDoc::getUpdateTime, LocalDateTime.now()));

        existing.setCourseId(dto.getCourseId());
        existing.setTitle(dto.getTitle().trim());
        existing.setContent(dto.getContent().trim());
        existing.setTags(tags);
        existing.setQuestionType(dto.getQuestionType());
        vectorStoreService.remove(id);
        vectorStoreService.index(existing);
        return existing;
    }

    /** 删除文档：逻辑删除，同时清理向量，避免残留向量继续参与召回 */
    public void delete(Long id) {
        AgentKnowledgeDoc existing = docMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("知识库文档不存在");
        }
        docMapper.deleteById(id);
        vectorStoreService.remove(id);
    }

    /** 按课程列文档；courseId 为空时返回全部 */
    public List<AgentKnowledgeDoc> list(Long courseId) {
        return docMapper.selectList(Wrappers.<AgentKnowledgeDoc>lambdaQuery()
                .eq(courseId != null, AgentKnowledgeDoc::getCourseId, courseId)
                .orderByDesc(AgentKnowledgeDoc::getId));
    }

    /** 兼容旧调用：不按题型过滤 */
    public List<AgentKnowledgeDoc> search(String query, Long courseId, int limit) {
        return search(query, courseId, null, limit);
    }

    /** BM25 检索（带课程 + 题型过滤） */
    public List<AgentKnowledgeDoc> search(String query, Long courseId, Integer questionType, int limit) {
        return bm25Search(query, candidates(courseId, questionType), limit);
    }

    /**
     * 取候选文档：本课程 + 全局，本题型 + 通用。
     * 这是"权限过滤"的落点：跨课程、跨题型的文档在召回前就被排除。
     */
    public List<AgentKnowledgeDoc> candidates(Long courseId, Integer questionType) {
        return docMapper.selectList(Wrappers.<AgentKnowledgeDoc>lambdaQuery()
                .and(courseId != null, w -> w
                        .eq(AgentKnowledgeDoc::getCourseId, courseId)
                        .or()
                        .isNull(AgentKnowledgeDoc::getCourseId))
                .and(questionType != null, w -> w
                        .eq(AgentKnowledgeDoc::getQuestionType, questionType)
                        .or()
                        .isNull(AgentKnowledgeDoc::getQuestionType))
                .orderByDesc(AgentKnowledgeDoc::getId)
                .last("LIMIT " + MAX_CANDIDATES));
    }

    /**
     * 对给定候选集做 BM25 打分。
     * 抽成独立方法是为了让混合检索复用同一份候选集，避免两路召回范围不一致。
     */
    public List<AgentKnowledgeDoc> bm25Search(String query, List<AgentKnowledgeDoc> docs, int limit) {
        int topN = limit <= 0 ? DEFAULT_LIMIT : limit;
        if (docs == null || docs.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return docs.subList(0, Math.min(topN, docs.size()));
        }

        List<DocVector> vectors = new ArrayList<>(docs.size());
        for (AgentKnowledgeDoc doc : docs) {
            String text = doc.getTitle() + " " + (doc.getTags() == null ? "" : doc.getTags())
                    + " " + doc.getContent();
            List<String> tokens = tokenize(text);
            vectors.add(new DocVector(doc, tokens, toTermFrequency(tokens)));
        }
        double avgLength = vectors.stream().mapToInt(DocVector::length).average().orElse(1.0);

        Set<String> terms = new HashSet<>(queryTokens);
        Map<String, Integer> docFrequency = new HashMap<>();
        for (String term : terms) {
            int df = 0;
            for (DocVector vector : vectors) {
                if (vector.termFrequency().containsKey(term)) {
                    df++;
                }
            }
            docFrequency.put(term, df);
        }

        List<ScoredDoc> scored = new ArrayList<>();
        for (DocVector vector : vectors) {
            double score = 0;
            for (String term : terms) {
                int tf = vector.termFrequency().getOrDefault(term, 0);
                if (tf == 0) {
                    continue;
                }
                int df = docFrequency.getOrDefault(term, 0);
                double idf = Math.log(1 + (vectors.size() - df + 0.5) / (df + 0.5));
                double denominator = tf + K1 * (1 - B + B * vector.length() / avgLength);
                score += idf * tf * (K1 + 1) / denominator;
            }
            if (score > 0) {
                scored.add(new ScoredDoc(vector.doc(), score));
            }
        }
        if (scored.isEmpty()) {
            return Collections.emptyList();
        }
        scored.sort(Comparator.comparingDouble(ScoredDoc::score).reversed());
        List<AgentKnowledgeDoc> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, scored.size()); i++) {
            result.add(scored.get(i).doc());
        }
        return result;
    }

    /** 重建全部文档向量；返回成功条数 */
    public int reindexAll() {
        List<AgentKnowledgeDoc> docs = docMapper.selectList(null);
        if (docs.isEmpty()) {
            return 0;
        }
        int success = vectorStoreService.reindex(docs);
        log.info("知识库向量重建完成: total={}, success={}", docs.size(), success);
        return success;
    }

    // ==================== 内部方法 ====================

    /** 中文二元组 + 英文/数字单词 */
    private List<String> tokenize(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String piece = matcher.group();
            if (piece.charAt(0) >= '\u4e00' && piece.charAt(0) <= '\u9fa5') {
                if (piece.length() == 1) {
                    tokens.add(piece);
                } else {
                    for (int i = 0; i + 1 < piece.length(); i++) {
                        tokens.add(piece.substring(i, i + 2));
                    }
                }
            } else if (piece.length() > 1) {
                tokens.add(piece);
            }
        }
        return tokens;
    }

    private Map<String, Integer> toTermFrequency(List<String> tokens) {
        Map<String, Integer> tf = new LinkedHashMap<>();
        for (String token : tokens) {
            tf.merge(token, 1, Integer::sum);
        }
        return tf;
    }

    private record DocVector(AgentKnowledgeDoc doc, List<String> tokens, Map<String, Integer> termFrequency) {
        int length() {
            return tokens.size();
        }
    }

    private record ScoredDoc(AgentKnowledgeDoc doc, double score) {
    }
}
