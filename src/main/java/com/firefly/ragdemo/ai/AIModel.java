package com.firefly.ragdemo.ai;

import reactor.core.publisher.Flux;

/**
 * AI模型统一接口，支持多种AI模型实现（OpenAI、Ollama等）
 */
public interface AIModel {

    /**
     * 获取模型名称
     */
    String getModelName();

    /**
     * 同步调用AI模型
     * @param prompt 完整的提示词
     * @return AI响应内容
     */
    String call(String prompt);

    /**
     * 流式调用AI模型
     * @param prompt 完整的提示词
     * @return 流式响应
     */
    Flux<String> stream(String prompt);

    /**
     * 检查模型是否可用
     */
    default boolean isAvailable() {
        return true;
    }
}
