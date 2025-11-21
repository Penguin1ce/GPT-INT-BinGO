package com.firefly.ragdemo.messaging;

import com.firefly.ragdemo.VO.ChatResponseVO;
import com.firefly.ragdemo.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMessageListener {

    private final ChatService chatService;
    private final ChatMessageQueueService chatMessageQueueService;

    @RabbitListener(queues = "${app.messaging.chat.queue}", concurrency = "${app.messaging.chat.concurrency:3}")
    public void handleChatMessage(AiMessagePayload payload) {
        log.debug("后台开始消费AI消息 requestId={}, userId={}", payload.getRequestId(), payload.getUserId());
        try {
            ChatResponseVO response = chatService.chat(payload.getChatRequest(), payload.getUserId());
            chatMessageQueueService.complete(payload.getRequestId(), response);
        } catch (Exception e) {
            log.error("处理AI消息失败 requestId={}, userId={}", payload.getRequestId(), payload.getUserId(), e);
            chatMessageQueueService.fail(payload.getRequestId(), e);
        }
    }
}
