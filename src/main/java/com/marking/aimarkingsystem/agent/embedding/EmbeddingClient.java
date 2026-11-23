package com.marking.aimarkingsystem.agent.embedding;

/**
 * Embedding 客户端抽象。
 *
 * 为什么不直接叫 DeepSeekEmbeddingClient：
 * DeepSeek 目前没有公开的 embedding 接口，写死名字等于撒谎。
 * 这里面向 OpenAI 兼容协议编程，可接 BGE、SiliconFlow、通义等任意兼容服务；
 * 未配置时 available() 返回 false，检索自动降级为纯 BM25。
 */
public interface EmbeddingClient {

    /** 是否已配置且启用 */
    boolean available();

    /** 当前模型名，写入向量表便于区分不同模型产生的向量 */
    String model();

    /**
     * 把文本转成向量。
     *
     * @throws IllegalStateException 未配置或调用失败
     */
    float[] embed(String text);
}
