package com.firefly.ragdemo.service.impl;

import com.firefly.ragdemo.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingServiceImpl implements EmbeddingService {

    private final OpenAiEmbeddingModel embeddingModel;

    @Override
    @Retryable(
        retryFor = {ResourceAccessException.class, HttpServerErrorException.class, HttpClientErrorException.TooManyRequests.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000)
    )
    public List<Double> embed(String text) {
        try {
            log.debug("调用OpenAI Embedding API: 文本长度={}", text != null ? text.length() : 0);
            float[] embedding = embeddingModel.embed(text);
            log.debug("Embedding成功: 向量维度={}", embedding.length);
            return toDoubleList(embedding);
        } catch (Exception e) {
            log.error("Embedding失败: {}", e.getMessage());
            throw new RuntimeException("Embedding失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Retryable(
        retryFor = {ResourceAccessException.class, HttpServerErrorException.class, HttpClientErrorException.TooManyRequests.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000)
    )
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        try {
            log.debug("调用OpenAI Batch Embedding API: 批量大小={}", texts.size());
            List<float[]> embeddings = embeddingModel.embed(texts);
            log.debug("Batch Embedding成功: 返回{}个向量", embeddings.size());
            return embeddings.stream()
                    .map(this::toDoubleList)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Batch embedding失败: {}", e.getMessage());
            throw new RuntimeException("Embedding批处理失败: " + e.getMessage(), e);
        }
    }

    private List<Double> toDoubleList(float[] embedding) {
        return java.util.stream.IntStream.range(0, embedding.length)
                .mapToDouble(i -> embedding[i])
                .boxed()
                .collect(Collectors.toList());
    }
}
