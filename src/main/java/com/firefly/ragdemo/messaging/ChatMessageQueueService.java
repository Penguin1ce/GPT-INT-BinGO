package com.firefly.ragdemo.messaging;

import com.firefly.ragdemo.DTO.ChatRequest;
import com.firefly.ragdemo.VO.ChatResponseVO;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageQueueService {

    private final RabbitTemplate rabbitTemplate;
    private final ChatMessagingProperties properties;
    private final PendingChatRequestManager pendingChatRequestManager;

    public ChatQueueTicket submit(ChatRequest request, String userId) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<ChatResponseVO> future = pendingChatRequestManager.register(requestId);

        AiMessagePayload payload = AiMessagePayload.builder()
                .requestId(requestId)
                .userId(userId)
                .chatRequest(request)
                .createdAt(Instant.now())
                .build();

        try {
            rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), payload);
            log.debug("已写入AI消息队列, requestId={}, userId={}", requestId, userId);
        } catch (AmqpException e) {
            pendingChatRequestManager.completeExceptionally(requestId, e);
            throw e;
        }

        return new ChatQueueTicket(requestId, future);
    }

    public void complete(String requestId, ChatResponseVO response) {
        pendingChatRequestManager.complete(requestId, response);
    }

    public void fail(String requestId, Throwable throwable) {
        pendingChatRequestManager.completeExceptionally(requestId, throwable);
    }
}
