package com.firefly.ragdemo.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.ragdemo.entity.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisDocumentChunkRepository {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String USER_CHUNKS_PREFIX = "rag:user:";
    private static final String FILE_CHUNKS_PREFIX = "rag:file:";
    private static final String CHUNK_PREFIX = "rag:chunk:";

    public void saveAll(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        var valueOps = stringRedisTemplate.opsForValue();
        var zSetOps = stringRedisTemplate.opsForZSet();
        var setOps = stringRedisTemplate.opsForSet();
        for (DocumentChunk chunk : chunks) {
            if (chunk == null || chunk.getId() == null) {
                continue;
            }
            String serialized = serialize(chunk);
            valueOps.set(chunkKey(chunk.getId()), serialized);
            double score = chunk.getCreatedAt() != null
                    ? chunk.getCreatedAt().toEpochSecond(ZoneOffset.UTC)
                    : System.currentTimeMillis() / 1000.0;
            zSetOps.add(userChunksKey(chunk.getUserId()), chunk.getId(), score);
            if (chunk.getFileId() != null) {
                setOps.add(fileChunksKey(chunk.getFileId()), chunk.getId());
            }
        }
    }

    public List<DocumentChunk> findByUser(String userId, int candidateLimit) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        int range = candidateLimit > 0 ? candidateLimit : 200;
        Set<String> chunkIds = stringRedisTemplate.opsForZSet()
                .reverseRange(userChunksKey(userId), 0, range - 1);
        if (chunkIds == null || chunkIds.isEmpty()) {
            return Collections.emptyList();
        }
        var valueOps = stringRedisTemplate.opsForValue();
        List<DocumentChunk> result = new ArrayList<>(chunkIds.size());
        for (String chunkId : chunkIds) {
            if (chunkId == null) {
                continue;
            }
            String json = valueOps.get(chunkKey(chunkId));
            if (json == null) {
                continue;
            }
            DocumentChunk chunk = deserialize(json);
            if (chunk != null && Objects.equals(userId, chunk.getUserId())) {
                result.add(chunk);
            }
        }
        return result;
    }

    public void deleteByFileIdAndUser(String fileId, String userId) {
        if (fileId == null || fileId.isBlank()) {
            return;
        }
        Set<String> chunkIds = stringRedisTemplate.opsForSet().members(fileChunksKey(fileId));
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        for (String chunkId : chunkIds) {
            stringRedisTemplate.delete(chunkKey(chunkId));
            if (userId != null) {
                stringRedisTemplate.opsForZSet().remove(userChunksKey(userId), chunkId);
            }
        }
        stringRedisTemplate.delete(fileChunksKey(fileId));
    }

    private String serialize(DocumentChunk chunk) {
        try {
            return objectMapper.writeValueAsString(chunk);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize DocumentChunk", e);
        }
    }

    private DocumentChunk deserialize(String json) {
        try {
            return objectMapper.readValue(json, DocumentChunk.class);
        } catch (Exception e) {
            log.warn("反序列化DocumentChunk失败: {}", e.getMessage());
            return null;
        }
    }

    private String userChunksKey(String userId) {
        return USER_CHUNKS_PREFIX + userId + ":chunks";
    }

    private String fileChunksKey(String fileId) {
        return FILE_CHUNKS_PREFIX + fileId + ":chunks";
    }

    private String chunkKey(String chunkId) {
        return CHUNK_PREFIX + chunkId;
    }
}
