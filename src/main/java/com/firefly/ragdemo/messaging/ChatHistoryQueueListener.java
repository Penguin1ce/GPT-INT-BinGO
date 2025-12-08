package com.firefly.ragdemo.messaging;

import com.firefly.ragdemo.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatHistoryQueueListener {

    private final ChatSessionService chatSessionService;

    @RabbitListener(queues = "${app.messaging.chat-session.queue}", concurrency = "${app.messaging.chat-session.concurrency:2}")
    public void handle(ChatHistoryPersistPayload payload) {
        if (payload == null) {
            log.warn("收到空的会话持久化消息，忽略");
            return;
        }
        try {
            chatSessionService.persistFromQueue(payload);
        } catch (Exception e) {
            log.error("消费会话持久化消息失败 sessionId={}, userId={}", payload.getSessionId(), payload.getUserId(), e);
            throw e;
        }
    }
}
