package com.marking.aimarkingsystem.agent.embedding;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * OpenAI 兼容的 Embedding 客户端（手写 HTTP，不引 SDK）。
 *
 * 配置全部通过环境变量注入，不改 application.yml：
 * - AGENT_EMBEDDING_ENABLED=true
 * - AGENT_EMBEDDING_URL=https://your-provider/v1/embeddings
 * - AGENT_EMBEDDING_API_KEY=xxx
 * - AGENT_EMBEDDING_MODEL=bge-m3 / text-embedding-3-small
 *
 * 说明：DeepSeek 没有公开 embedding 接口，默认关闭；
 * 未配置时 HybridRetriever 自动只走 BM25，不影响主流程。
 */
@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingClient.class);

    @Value("${AGENT_EMBEDDING_ENABLED:false}")
    private boolean enabled;

    @Value("${AGENT_EMBEDDING_URL:}")
    private String url;

    @Value("${AGENT_EMBEDDING_API_KEY:}")
    private String apiKey;

    @Value("${AGENT_EMBEDDING_MODEL:text-embedding-3-small}")
    private String model;

    @Value("${AGENT_EMBEDDING_TIMEOUT_MS:5000}")
    private int timeoutMs;

    @Override
    public boolean available() {
        return enabled && StringUtils.hasText(url) && StringUtils.hasText(apiKey);
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public float[] embed(String text) {
        if (!available()) {
            throw new IllegalStateException("Embedding 未启用或未配置");
        }
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("Embedding 文本不能为空");
        }
        JSONObject body = new JSONObject();
        body.set("model", model);
        body.set("input", text);

        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(timeoutMs)
                .body(body.toString())
                .execute()) {
            String responseBody = response.body();
            if (!response.isOk()) {
                throw new IllegalStateException("Embedding 接口返回 " + response.getStatus());
            }
            JSONObject json = JSONUtil.parseObj(responseBody);
            JSONArray data = json.getJSONArray("data");
            if (data == null || data.isEmpty()) {
                throw new IllegalStateException("Embedding 响应缺少 data");
            }
            JSONArray embedding = data.getJSONObject(0).getJSONArray("embedding");
            if (embedding == null || embedding.isEmpty()) {
                throw new IllegalStateException("Embedding 响应缺少向量");
            }
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = embedding.getFloat(i, 0f);
            }
            log.debug("Embedding 调用成功: model={}, dim={}", model, vector.length);
            return vector;
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Embedding 调用失败：" + e.getMessage(), e);
        }
    }
}
