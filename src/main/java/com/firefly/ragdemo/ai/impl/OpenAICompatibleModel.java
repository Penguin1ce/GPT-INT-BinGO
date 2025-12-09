package com.firefly.ragdemo.ai.impl;

import com.firefly.ragdemo.ai.AIModel;
import com.firefly.ragdemo.ai.AIModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import reactor.core.publisher.Flux;

/**
 * OpenAI兼容API模型实现
 * 支持OpenAI官方API及其兼容API（如DeepSeek、Claude等）
 */
@Slf4j
public class OpenAICompatibleModel implements AIModel {

    private final AIModelConfig config;
    private final OpenAiChatModel chatModel;

    public OpenAICompatibleModel(AIModelConfig config, OpenAiChatModel chatModel) {
        this.config = config;
        this.chatModel = chatModel;
        log.info("初始化OpenAI兼容模型: {} (baseUrl: {})", config.getModelName(), config.getBaseUrl());
    }

    @Override
    public String getModelName() {
        return config.getModelName();
    }

    @Override
    public String call(String prompt) {
        try {
            return chatModel.call(prompt);
        } catch (Exception e) {
            log.error("OpenAI API调用失败: {}", e.getMessage());
            throw new RuntimeException("AI调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> stream(String prompt) {
        try {
            return chatModel.stream(prompt)
                    .map(chunk -> chunk != null ? chunk : "")
                    .filter(content -> !content.isEmpty());
        } catch (Exception e) {
            log.error("OpenAI API流式调用失败: {}", e.getMessage());
            return Flux.error(new RuntimeException("AI流式调用失败: " + e.getMessage(), e));
        }
    }

    @Override
    public boolean isAvailable() {
        return chatModel != null && config.getApiKey() != null;
    }
}
