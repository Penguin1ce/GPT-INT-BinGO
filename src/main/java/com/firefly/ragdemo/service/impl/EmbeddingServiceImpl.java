package com.firefly.ragdemo.service.impl;

import com.firefly.ragdemo.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingServiceImpl implements EmbeddingService {

    private final OpenAiEmbeddingModel embeddingModel;

    @Override
    public List<Double> embed(String text) {
        try {
            float[] embedding = embeddingModel.embed(text);
            return toDoubleList(embedding);
        } catch (Exception e) {
            log.error("Embedding failed", e);
            throw new RuntimeException("Embedding失败: " + e.getMessage());
        }
    }

    @Override
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        try {
            List<float[]> embeddings = embeddingModel.embed(texts);
            return embeddings.stream()
                    .map(this::toDoubleList)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Batch embedding failed", e);
            throw new RuntimeException("Embedding批处理失败: " + e.getMessage());
        }
    }

    private List<Double> toDoubleList(float[] embedding) {
        return java.util.stream.IntStream.range(0, embedding.length)
                .mapToDouble(i -> embedding[i])
                .boxed()
                .collect(Collectors.toList());
    }
}
