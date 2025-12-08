package com.firefly.ragdemo.messaging;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatHistoryQueueProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ChatSessionMessagingProperties properties;

    public void publish(ChatHistoryPersistPayload payload) {
        if (payload == null) {
            return;
        }
        if (payload.getCreatedAt() == null) {
            payload.setCreatedAt(Instant.now());
        }
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), payload);
        log.debug("已写入会话持久化队列 sessionId={}, userId={}", payload.getSessionId(), payload.getUserId());
    }
}
