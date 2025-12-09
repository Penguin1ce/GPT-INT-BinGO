package com.firefly.ragdemo.messaging;

import com.firefly.ragdemo.VO.ChatResponseVO;
import com.firefly.ragdemo.messaging.ChatHistoryPersistPayload;
import com.firefly.ragdemo.messaging.ChatHistoryQueueProducer;
import com.firefly.ragdemo.service.ChatService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMessageListener {

    private final ChatService chatService;
    private final ChatMessageQueueService chatMessageQueueService;
    private final ChatHistoryQueueProducer chatHistoryQueueProducer;
    @Value("${spring.ai.openai.chat.options.model}")
    private String configuredChatModel;

    @RabbitListener(queues = "${app.messaging.chat.queue}", concurrency = "${app.messaging.chat.concurrency:3}")
    public void handleChatMessage(AiMessagePayload payload) {
        log.debug("后台开始消费AI消息 requestId={}, userId={}", payload.getRequestId(), payload.getUserId());
        try {
            ChatResponseVO response = chatService.chat(payload.getChatRequest(), payload.getUserId());
            chatMessageQueueService.complete(payload.getRequestId(), response);
            publishHistory(payload, response);
        } catch (Exception e) {
            log.error("处理AI消息失败 requestId={}, userId={}", payload.getRequestId(), payload.getUserId(), e);
            chatMessageQueueService.fail(payload.getRequestId(), e);
        }
    }

    private void publishHistory(AiMessagePayload payload, ChatResponseVO response) {
        if (payload == null || payload.getChatRequest() == null) {
            return;
        }
        String userMessage = extractLatestUserMessage(payload.getChatRequest());
        if (userMessage == null) {
            return;
        }
        List<ChatHistoryPersistPayload.Message> messages = new ArrayList<>();
        messages.add(ChatHistoryPersistPayload.Message.builder()
                .role("user")
                .content(userMessage)
                .createdAt(payload.getCreatedAt())
                .build());
        if (response != null && response.getResponse() != null) {
            messages.add(ChatHistoryPersistPayload.Message.builder()
                    .role("assistant")
                    .content(response.getResponse())
                    .createdAt(Instant.now())
                    .build());
        }
        String modelToPersist = StringUtils.hasText(configuredChatModel)
                ? configuredChatModel
                : payload.getChatRequest().getModel();
        ChatHistoryPersistPayload persistPayload = ChatHistoryPersistPayload.builder()
                .sessionId(payload.getChatRequest().getSessionId())
                .userId(payload.getUserId())
                .sessionTitle(response != null ? response.getSessionTitle() : userMessage)
                .model(modelToPersist)
                .createdAt(payload.getCreatedAt())
                .messages(messages)
                .build();
        chatHistoryQueueProducer.publish(persistPayload);
    }

    private String extractLatestUserMessage(com.firefly.ragdemo.DTO.ChatRequest chatRequest) {
        if (chatRequest.getMessages() == null) {
            return null;
        }
        for (int i = chatRequest.getMessages().size() - 1; i >= 0; i--) {
            var msg = chatRequest.getMessages().get(i);
            if (msg != null && "user".equalsIgnoreCase(msg.getRole()) && msg.getContent() != null) {
                return msg.getContent();
            }
        }
        return null;
    }
}
