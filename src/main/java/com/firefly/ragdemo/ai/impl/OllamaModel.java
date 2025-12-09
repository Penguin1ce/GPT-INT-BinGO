package com.firefly.ragdemo.ai.impl;

import com.firefly.ragdemo.ai.AIModel;
import com.firefly.ragdemo.ai.AIModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Ollama本地模型实现
 * 通过Ollama API调用本地部署的大语言模型
 */
@Slf4j
public class OllamaModel implements AIModel {

    private final AIModelConfig config;
    private final WebClient webClient;

    public OllamaModel(AIModelConfig config) {
        this.config = config;
        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : "http://localhost:11434";
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        log.info("初始化Ollama模型: {} (baseUrl: {})", config.getModelName(), baseUrl);
    }

    @Override
    public String getModelName() {
        return config.getModelName();
    }

    @Override
    public String call(String prompt) {
        try {
            Map<String, Object> request = Map.of(
                    "model", config.getModelName(),
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of(
                            "temperature", config.getTemperature()
                    )
            );

            Map<?, ?> response = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("response")) {
                return (String) response.get("response");
            }
            return "";
        } catch (Exception e) {
            log.error("Ollama API调用失败: {}", e.getMessage());
            throw new RuntimeException("Ollama调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> stream(String prompt) {
        try {
            Map<String, Object> request = Map.of(
                    "model", config.getModelName(),
                    "prompt", prompt,
                    "stream", true,
                    "options", Map.of(
                            "temperature", config.getTemperature()
                    )
            );

            return webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .map(chunk -> {
                        Object response = chunk.get("response");
                        return response != null ? response.toString() : "";
                    })
                    .filter(content -> !content.isEmpty());
        } catch (Exception e) {
            log.error("Ollama API流式调用失败: {}", e.getMessage());
            return Flux.error(new RuntimeException("Ollama流式调用失败: " + e.getMessage(), e));
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            List<?> models = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(response -> (List<?>) response.get("models"))
                    .block();
            return models != null && !models.isEmpty();
        } catch (Exception e) {
            log.warn("Ollama服务不可用: {}", e.getMessage());
            return false;
        }
    }
}
