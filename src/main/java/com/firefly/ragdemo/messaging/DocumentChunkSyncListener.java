package com.firefly.ragdemo.messaging;

import com.firefly.ragdemo.entity.DocumentChunk;
import com.firefly.ragdemo.mapper.DocumentChunkMapper;
import com.firefly.ragdemo.repository.RedisDocumentChunkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentChunkSyncListener {

    private final RedisDocumentChunkRepository redisDocumentChunkRepository;
    private final DocumentChunkMapper documentChunkMapper;

    @RabbitListener(queues = "${app.messaging.chunk-sync.queue}", concurrency = "${app.messaging.chunk-sync.concurrency:2}")
    public void handle(DocumentChunkSyncPayload payload) {
        if (payload == null || payload.getFileId() == null) {
            log.warn("收到空的分块同步消息，忽略");
            return;
        }
        log.info("开始消费分块同步消息 fileId={}, kbId={}", payload.getFileId(), payload.getKbId());
        List<DocumentChunk> chunks = redisDocumentChunkRepository.findByFileId(payload.getFileId());
        if (chunks.isEmpty()) {
            log.info("Redis 未找到需要同步的分块 fileId={}", payload.getFileId());
            return;
        }
        int inserted = documentChunkMapper.batchInsertIgnore(chunks);
        log.info("完成分块同步 fileId={}, total={}, inserted={}", payload.getFileId(), chunks.size(), inserted);
    }
}
