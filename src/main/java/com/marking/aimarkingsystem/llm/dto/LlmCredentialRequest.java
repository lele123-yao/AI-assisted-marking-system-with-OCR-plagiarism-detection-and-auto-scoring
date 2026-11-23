package com.marking.aimarkingsystem.llm.dto;

import lombok.Data;

/**
 * 用户提交大模型 Key 的请求体（BYOK）。
 *
 * apiUrl / model 为可选项：
 * - 留空表示使用 application.yml 中的默认地址与模型（DeepSeek 官方）；
 * - 若用户用其他 OpenAI 兼容服务，可在此覆盖地址与模型名。
 */
@Data
public class LlmCredentialRequest {

    /** 用户自己的 API Key（必填，仅保存在后端内存，不回传前端） */
    private String apiKey;

    /** 自定义接口地址（可空） */
    private String apiUrl;

    /** 自定义模型名（可空） */
    private String model;
}
