package com.firefly.ragdemo.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI助手管理器
 * 采用单例模式管理用户-会话-AIHelper的映射关系，实现实例缓存和生命周期控制
 */
@Component
@Slf4j
public class AIHelperManager {

    /**
     * 缓存键格式：userId:sessionId
     */
    private final Map<String, AIHelper> helperCache = new ConcurrentHashMap<>();

    private final AIModelFactory modelFactory;

    /**
     * 最大空闲时间（分钟），超过则自动清理
     */
    private static final long MAX_IDLE_MINUTES = 30;

    public AIHelperManager(AIModelFactory modelFactory) {
        this.modelFactory = modelFactory;
        log.info("AIHelperManager初始化完成");
    }

    /**
     * 获取或创建AIHelper
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return AIHelper实例
     */
    public AIHelper getOrCreate(String userId, String sessionId) {
        return getOrCreate(userId, sessionId, null);
    }

    /**
     * 获取或创建AIHelper（指定模型配置）
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param config 模型配置（为null则使用默认模型）
     * @return AIHelper实例
     */
    public AIHelper getOrCreate(String userId, String sessionId, AIModelConfig config) {
        String cacheKey = buildCacheKey(userId, sessionId);
        return helperCache.computeIfAbsent(cacheKey, k -> {
            AIModel model;
            if (config != null) {
                String modelCacheKey = config.getType() + ":" + config.getModelName();
                model = modelFactory.getOrCreate(modelCacheKey, config);
            } else {
                model = modelFactory.getDefaultModel();
            }
            log.debug("创建新的AIHelper: userId={}, sessionId={}, model={}", userId, sessionId, model.getModelName());
            return new AIHelper(userId, sessionId, model);
        });
    }

    /**
     * 获取已存在的AIHelper
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return AIHelper实例，不存在则返回null
     */
    public AIHelper get(String userId, String sessionId) {
        return helperCache.get(buildCacheKey(userId, sessionId));
    }

    /**
     * 移除指定的AIHelper
     * @param userId 用户ID
     * @param sessionId 会话ID
     */
    public void remove(String userId, String sessionId) {
        String cacheKey = buildCacheKey(userId, sessionId);
        AIHelper removed = helperCache.remove(cacheKey);
        if (removed != null) {
            log.debug("移除AIHelper: userId={}, sessionId={}", userId, sessionId);
        }
    }

    /**
     * 移除用户的所有AIHelper
     * @param userId 用户ID
     */
    public void removeByUser(String userId) {
        String prefix = userId + ":";
        helperCache.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                log.debug("移除用户的AIHelper: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * 定时清理过期的AIHelper（每5分钟执行一次）
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredHelpers() {
        int before = helperCache.size();
        helperCache.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired(MAX_IDLE_MINUTES)) {
                log.debug("清理过期的AIHelper: {}", entry.getKey());
                return true;
            }
            return false;
        });
        int after = helperCache.size();
        if (before != after) {
            log.info("清理过期AIHelper: {} -> {} (移除{}个)", before, after, before - after);
        }
    }

    /**
     * 获取当前缓存的AIHelper数量
     */
    public int getCacheSize() {
        return helperCache.size();
    }

    private String buildCacheKey(String userId, String sessionId) {
        return userId + ":" + sessionId;
    }
}
