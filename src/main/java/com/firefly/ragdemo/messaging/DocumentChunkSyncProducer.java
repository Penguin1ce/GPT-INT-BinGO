package com.firefly.ragdemo.messaging;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentChunkSyncProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ChunkSyncMessagingProperties properties;

    public void publish(String fileId, String userId, String kbId) {
        DocumentChunkSyncPayload payload = DocumentChunkSyncPayload.builder()
                .fileId(fileId)
                .userId(userId)
                .kbId(kbId)
                .createdAt(Instant.now())
                .build();
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), payload);
        log.debug("已写入分块同步队列, fileId={}, kbId={}", fileId, kbId);
    }
}
