package com.firefly.ragdemo.messaging;

import com.firefly.ragdemo.VO.ChatResponseVO;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PendingChatRequestManager {

    private final ConcurrentHashMap<String, CompletableFuture<ChatResponseVO>> pendingRequests = new ConcurrentHashMap<>();

    public CompletableFuture<ChatResponseVO> register(String requestId) {
        CompletableFuture<ChatResponseVO> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);
        future.whenComplete((response, throwable) -> pendingRequests.remove(requestId));
        return future;
    }

    public void complete(String requestId, ChatResponseVO response) {
        CompletableFuture<ChatResponseVO> future = pendingRequests.get(requestId);
        if (future != null) {
            future.complete(response);
        } else {
            log.warn("完成Chat响应时未找到请求ID: {}，可能已超时或被取消", requestId);
        }
    }

    public void completeExceptionally(String requestId, Throwable throwable) {
        CompletableFuture<ChatResponseVO> future = pendingRequests.get(requestId);
        if (future != null) {
            future.completeExceptionally(throwable);
        } else {
            log.warn("异常完成Chat响应时未找到请求ID: {}，可能已超时或被取消", requestId);
        }
    }
}
