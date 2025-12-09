package com.firefly.ragdemo.ai;

import com.firefly.ragdemo.ai.impl.OllamaModel;
import com.firefly.ragdemo.ai.impl.OpenAICompatibleModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * AI模型工厂
 * 采用工厂模式实现AI模型的创建和管理，支持动态注册和实例化多种AI模型
 */
@Component
@Slf4j
public class AIModelFactory {

    /**
     * 模型创建者函数映射表
     */
    private final Map<String, Function<AIModelConfig, AIModel>> creators = new ConcurrentHashMap<>();

    /**
     * 已创建的模型实例缓存
     */
    private final Map<String, AIModel> modelCache = new ConcurrentHashMap<>();

    private final OpenAiChatModel defaultOpenAiChatModel;

    public AIModelFactory(OpenAiChatModel defaultOpenAiChatModel) {
        this.defaultOpenAiChatModel = defaultOpenAiChatModel;
        registerDefaultCreators();
    }

    /**
     * 注册默认的模型创建者
     */
    private void registerDefaultCreators() {
        // OpenAI兼容API（包括DeepSeek、Claude等）
        register("openai", config -> new OpenAICompatibleModel(config, defaultOpenAiChatModel));

        // Ollama本地模型
        register("ollama", OllamaModel::new);

        log.info("AI模型工厂初始化完成，已注册模型类型: {}", creators.keySet());
    }

    /**
     * 注册模型创建者
     * @param type 模型类型标识
     * @param creator 创建者函数
     */
    public void register(String type, Function<AIModelConfig, AIModel> creator) {
        creators.put(type.toLowerCase(), creator);
        log.debug("注册AI模型创建者: {}", type);
    }

    /**
     * 创建AI模型实例
     * @param config 模型配置
     * @return AI模型实例
     */
    public AIModel create(AIModelConfig config) {
        String type = config.getType().toLowerCase();
        Function<AIModelConfig, AIModel> creator = creators.get(type);
        if (creator == null) {
            throw new IllegalArgumentException("不支持的AI模型类型: " + type + "，支持的类型: " + creators.keySet());
        }
        return creator.apply(config);
    }

    /**
     * 获取或创建AI模型实例（带缓存）
     * @param cacheKey 缓存键（如 "openai:gpt-4o" 或 "ollama:llama3.2"）
     * @param config 模型配置
     * @return AI模型实例
     */
    public AIModel getOrCreate(String cacheKey, AIModelConfig config) {
        return modelCache.computeIfAbsent(cacheKey, k -> create(config));
    }

    /**
     * 获取默认的OpenAI模型
     */
    public AIModel getDefaultModel() {
        return modelCache.computeIfAbsent("default", k -> {
            AIModelConfig config = AIModelConfig.builder()
                    .type("openai")
                    .modelName("default")
                    .build();
            return new OpenAICompatibleModel(config, defaultOpenAiChatModel);
        });
    }

    /**
     * 移除缓存的模型实例
     */
    public void evict(String cacheKey) {
        modelCache.remove(cacheKey);
    }

    /**
     * 清空所有缓存
     */
    public void clearCache() {
        modelCache.clear();
    }

    /**
     * 检查是否支持指定类型
     */
    public boolean supports(String type) {
        return creators.containsKey(type.toLowerCase());
    }
}
