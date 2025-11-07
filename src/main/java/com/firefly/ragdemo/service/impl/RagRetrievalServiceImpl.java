package com.firefly.ragdemo.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.ragdemo.entity.DocumentChunk;
import com.firefly.ragdemo.repository.RedisDocumentChunkRepository;
import com.firefly.ragdemo.service.EmbeddingService;
import com.firefly.ragdemo.service.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private final EmbeddingService embeddingService;
    private final RedisDocumentChunkRepository redisDocumentChunkRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> retrieveContext(String userId, String query, int topK, int candidateLimit) {
        List<Double> q = embeddingService.embed(query);
        if (q == null || q.isEmpty()) {
            return Collections.emptyList();
        }
        int limit = candidateLimit > 0 ? candidateLimit : Math.max(topK * 4, 20);
        List<DocumentChunk> chunks = redisDocumentChunkRepository.findByUser(userId, limit);
        if (chunks.isEmpty()) {
            return Collections.emptyList();
        }
        double[] queryVector = toPrimitive(q);
        List<ScoredChunk> scored = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            double[] chunkVector = parseEmbedding(chunk.getEmbeddingJson());
            if (chunkVector.length == 0 || chunk.getContent() == null) {
                continue;
            }
            double score = cosineSimilarity(queryVector, chunkVector);
            if (!Double.isNaN(score)) {
                scored.add(new ScoredChunk(chunk.getContent(), score));
            }
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        List<String> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            results.add(scored.get(i).content());
        }
        return results;
    }

    private double[] parseEmbedding(String embeddingJson) {
        if (embeddingJson == null || embeddingJson.isBlank()) {
            return new double[0];
        }
        try {
            return objectMapper.readValue(embeddingJson, double[].class);
        } catch (Exception e) {
            return new double[0];
        }
    }

    private double[] toPrimitive(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i) != null ? values.get(i) : 0d;
        }
        return result;
    }

    private double cosineSimilarity(double[] a, double[] b) {
        if (a.length == 0 || b.length == 0 || a.length != b.length) {
            return Double.NaN;
        }
        double dot = 0d;
        double normA = 0d;
        double normB = 0d;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        if (denom == 0d) {
            return Double.NaN;
        }
        return dot / denom;
    }

    private record ScoredChunk(String content, double score) {}
}
