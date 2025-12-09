package com.firefly.ragdemo.ai;

import lombok.Builder;
import lombok.Data;

/**
 * AI模型配置
 */
@Data
@Builder
public class AIModelConfig {

    /**
     * 模型类型：openai, ollama
     */
    private String type;

    /**
     * 模型名称，如 gpt-4o, deepseek-v3, llama3.2
     */
    private String modelName;

    /**
     * API基础URL
     */
    private String baseUrl;

    /**
     * API密钥（OpenAI类型需要）
     */
    private String apiKey;

    /**
     * 温度参数
     */
    @Builder.Default
    private double temperature = 0.7;

    /**
     * 最大token数
     */
    @Builder.Default
    private int maxTokens = 4096;
}
