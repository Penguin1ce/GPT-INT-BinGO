package com.firefly.ragdemo.messaging;

import com.firefly.ragdemo.VO.ChatResponseVO;
import java.util.concurrent.CompletableFuture;

public record ChatQueueTicket(String requestId, CompletableFuture<ChatResponseVO> future) {
}
