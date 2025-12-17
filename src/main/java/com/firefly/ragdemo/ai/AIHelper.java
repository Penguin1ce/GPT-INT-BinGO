package com.firefly.ragdemo.ai;

import com.firefly.ragdemo.dto.ChatRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI助手
 * 封装AI模型的调用逻辑，管理会话上下文
 */
@Slf4j
public class AIHelper {

    @Getter
    private final String userId;

    @Getter
    private final String sessionId;

    private final AIModel model;

    @Getter
    private final LocalDateTime createdAt;

    @Getter
    private LocalDateTime lastAccessedAt;

    public AIHelper(String userId, String sessionId, AIModel model) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.model = model;
        this.createdAt = LocalDateTime.now();
        this.lastAccessedAt = LocalDateTime.now();
    }

    /**
     * 同步调用AI
     * @param prompt 完整提示词
     * @return AI响应
     */
    public String call(String prompt) {
        this.lastAccessedAt = LocalDateTime.now();
        return model.call(prompt);
    }

    /**
     * 流式调用AI
     * @param prompt 完整提示词
     * @return 流式响应
     */
    public Flux<String> stream(String prompt) {
        this.lastAccessedAt = LocalDateTime.now();
        return model.stream(prompt);
    }

    /**
     * 获取模型名称
     */
    public String getModelName() {
        return model.getModelName();
    }

    /**
     * 检查是否过期（超过指定分钟未访问）
     */
    public boolean isExpired(long maxIdleMinutes) {
        return lastAccessedAt.plusMinutes(maxIdleMinutes).isBefore(LocalDateTime.now());
    }
}
