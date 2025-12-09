package com.firefly.ragdemo.controller;

import com.firefly.ragdemo.DTO.ChatRequest;
import com.firefly.ragdemo.VO.ApiResponse;
import com.firefly.ragdemo.VO.ChatResponseVO;
import com.firefly.ragdemo.VO.ChatSessionVO;
import com.firefly.ragdemo.VO.ChatMessageVO;
import com.firefly.ragdemo.entity.ChatSession;
import com.firefly.ragdemo.entity.ChatMessageRecord;
import com.firefly.ragdemo.messaging.ChatMessageQueueService;
import com.firefly.ragdemo.messaging.ChatMessagingProperties;
import com.firefly.ragdemo.messaging.ChatQueueTicket;
import com.firefly.ragdemo.messaging.ChatHistoryPersistPayload;
import com.firefly.ragdemo.messaging.ChatHistoryQueueProducer;
import com.firefly.ragdemo.secutiry.CustomUserPrincipal;
import com.firefly.ragdemo.service.ChatService;
import com.firefly.ragdemo.service.ChatSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.Instant;
import reactor.core.publisher.Flux;
import reactor.core.Disposable;
import java.util.concurrent.atomic.AtomicBoolean;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.util.StringUtils;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageQueueService chatMessageQueueService;
    private final ChatMessagingProperties chatMessagingProperties;
    private final ChatSessionService chatSessionService;
    private final ChatHistoryQueueProducer chatHistoryQueueProducer;
    @Value("${spring.ai.openai.chat.options.model}")
    private String configuredChatModel;
    private final ExecutorService executorService =
        new DelegatingSecurityContextExecutorService(Executors.newCachedThreadPool());
    private final ScheduledExecutorService heartbeatScheduler =
        Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });

    private static final long SSE_TIMEOUT_MS = 0L; // 不主动中断流
    private static final long HEARTBEAT_INTERVAL_MS = 15000L;

    @PostMapping("/ask")
    public Object ask(@Valid @RequestBody ChatRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        if (bindingResult.hasErrors()) {
            List<ApiResponse.ValidationError> errors = bindingResult.getFieldErrors().stream()
                    .map(error -> ApiResponse.ValidationError.builder()
                            .field(error.getField())
                            .message(error.getDefaultMessage())
                            .build())
                    .collect(Collectors.toList());

            ApiResponse<ChatResponseVO> response = ApiResponse.error("参数验证失败", 400, errors);
            return ResponseEntity.badRequest().body(response);
        }

        if (!StringUtils.hasText(configuredChatModel)) {
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("服务端未配置模型"));
        }
        request.setModel(configuredChatModel);

        try {
            String userId = principal.getUserId();
            String sessionId = resolveSessionId(request);
            String sessionTitle = deriveSessionTitle(request);
            request.setSessionId(sessionId);

            if (Boolean.TRUE.equals(request.getStream())) {
                // 流式响应
                return handleStreamResponse(request, userId, sessionId, sessionTitle);
            } else {
                // 非流式响应
                return handleNormalResponse(request, userId);
            }

        } catch (Exception e) {
            log.error("对话请求失败 for user {}: {}", principal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("对话请求失败"));
        }
    }

    @GetMapping("/chat/sessions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        try {
            String userId = principal.getUserId();
            List<ChatSession> sessions = chatSessionService.listSessions(userId, page, limit);
            long total = chatSessionService.countSessions(userId);
            List<ChatSessionVO> items = sessions.stream()
                    .map(this::toSessionVO)
                    .collect(Collectors.toList());
            Map<String, Object> pagination = Map.of(
                    "page", page,
                    "limit", limit,
                    "total", total,
                    "totalPages", limit > 0 ? (int) Math.ceil((double) total / (double) limit) : 1
            );
            Map<String, Object> data = Map.of(
                    "sessions", items,
                    "pagination", pagination
            );
            return ResponseEntity.ok(ApiResponse.success("获取会话列表成功", data));
        } catch (Exception e) {
            log.error("获取会话列表失败 for user {}: {}", principal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取会话列表失败"));
        }
    }

    @GetMapping("/chat/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSessionMessages(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "200") int limit,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        try {
            String userId = principal.getUserId();
            List<ChatMessageRecord> records = chatSessionService.listMessages(sessionId, userId, limit);
            List<ChatMessageVO> messages = records.stream()
                    .map(this::toMessageVO)
                    .collect(Collectors.toList());
            List<ChatRequest.ChatMessage> history = chatSessionService.buildHistory(sessionId, userId, limit);
            Map<String, Object> data = Map.of(
                    "messages", messages,
                    "history", history
            );
            return ResponseEntity.ok(ApiResponse.success("获取会话历史成功", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(ApiResponse.error(e.getMessage(), 403));
        } catch (Exception e) {
            log.error("获取会话历史失败 sessionId={}, userId={}", sessionId, principal.getUserId(), e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取会话历史失败"));
        }
    }

    private ResponseEntity<SseEmitter> handleStreamResponse(ChatRequest request, String userId, String sessionId, String sessionTitle) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String userMessage = extractLatestUserMessage(request);
        StringBuilder assistantBuilder = new StringBuilder();

        // 通过原子标志跟踪是否已完成，避免完成后继续发送
        AtomicBoolean isCompleted = new AtomicBoolean(false);
        // 订阅引用，便于在完成/出错时取消
        final Disposable[] subscriptionRef = new Disposable[1];
        final ScheduledFuture<?>[] heartbeatRef = new ScheduledFuture<?>[1];

        try {
            String safeTitle = escapeJson(sessionTitle);
            emitter.send(SseEmitter.event().name("session")
                    .data("{\"sessionId\":\"" + sessionId + "\",\"title\":\"" + safeTitle + "\"}"));
        } catch (Exception sendSessionError) {
            log.warn("发送会话信息失败: {}", sendSessionError.getMessage());
        }

        // 设置完成和超时回调
        emitter.onCompletion(() -> {
            log.debug("SSE连接完成");
            isCompleted.set(true);
            cancelHeartbeat(heartbeatRef[0]);
            if (subscriptionRef[0] != null && !subscriptionRef[0].isDisposed()) {
                subscriptionRef[0].dispose();
            }
        });
        emitter.onTimeout(() -> {
            log.debug("SSE连接超时");
            if (!isCompleted.get()) {
                isCompleted.set(true);
                emitter.complete();
            }
            cancelHeartbeat(heartbeatRef[0]);
            if (subscriptionRef[0] != null && !subscriptionRef[0].isDisposed()) {
                subscriptionRef[0].dispose();
            }
        });
        emitter.onError(throwable -> {
            log.error("SSE连接出错", throwable);
            if (!isCompleted.get()) {
                isCompleted.set(true);
                try { emitter.complete(); } catch (Exception ignore) {}
            }
            cancelHeartbeat(heartbeatRef[0]);
            if (subscriptionRef[0] != null && !subscriptionRef[0].isDisposed()) {
                subscriptionRef[0].dispose();
            }
        });

        // 保存当前安全上下文
        SecurityContext securityContext = SecurityContextHolder.getContext();
        
        executorService.execute(() -> {
            // 在异步线程中设置安全上下文
            SecurityContextHolder.setContext(securityContext);
            try {
                Flux<String> responseStream = chatService.chatStream(request, userId);
                heartbeatRef[0] = scheduleHeartbeat(emitter, isCompleted, subscriptionRef);

                subscriptionRef[0] = responseStream.subscribe(
                        chunk -> {
                            if (isCompleted.get()) return;
                            try {
                                // 发送SSE格式的数据，仅发送JSON在data行
                                String payload = chunk.replace("\"", "\\\"").replace("\n", "\\n");
                                String json = "{\"message\":{\"content\":\"" + payload + "\"}}";
                                assistantBuilder.append(chunk);
                                emitter.send(SseEmitter.event().data(json));
                            } catch (Exception e) {
                                log.error("发送SSE数据失败", e);
                                if (!isCompleted.get()) {
                                    isCompleted.set(true);
                                    try { emitter.complete(); } catch (Exception ignore) {}
                                }
                                if (subscriptionRef[0] != null && !subscriptionRef[0].isDisposed()) {
                                    subscriptionRef[0].dispose();
                                }
                                cancelHeartbeat(heartbeatRef[0]);
                            }
                        },
                        error -> {
                            log.error("流式对话出错", error);
                            if (!isCompleted.get()) {
                                try {
                                    String errorMsg = error.getMessage() == null ? "unknown" : error.getMessage();
                                    String safeMsg = errorMsg.replace("\"", "\\\"").replace("\n", "\\n");
                                    String json = "{\"error\":\"" + safeMsg + "\"}";
                                    emitter.send(SseEmitter.event().data(json));
                                } catch (Exception e) {
                                    log.error("发送错误信息失败", e);
                                } finally {
                                    isCompleted.set(true);
                                    try { emitter.complete(); } catch (Exception ignore) {}
                                }
                            }
                            if (subscriptionRef[0] != null && !subscriptionRef[0].isDisposed()) {
                                subscriptionRef[0].dispose();
                            }
                            cancelHeartbeat(heartbeatRef[0]);
                        },
                        () -> {
                            log.info("流式对话完成");
                            if (!isCompleted.get()) {
                                try {
                                    String json = "{\"done\":true}";
                                    emitter.send(SseEmitter.event().data(json));
                                    publishHistory(sessionId, userId, sessionTitle, userMessage,
                                            assistantBuilder.toString(), request.getModel());
                                } catch (Exception e) {
                                    log.error("发送完成信号失败", e);
                                } finally {
                                    isCompleted.set(true);
                                    try { emitter.complete(); } catch (Exception ignore) {}
                                }
                            }
                            if (subscriptionRef[0] != null && !subscriptionRef[0].isDisposed()) {
                                subscriptionRef[0].dispose();
                            }
                            cancelHeartbeat(heartbeatRef[0]);
                        });

            } catch (Exception e) {
                log.error("启动流式对话失败", e);
                if (!isCompleted.get()) {
                    try {
                        String errorMsg = e.getMessage() == null ? "unknown" : e.getMessage();
                        String safeMsg = errorMsg.replace("\"", "\\\"").replace("\n", "\\n");
                        String json = "{\"error\":\"" + safeMsg + "\"}";
                        emitter.send(SseEmitter.event().data(json));
                    } catch (Exception sendError) {
                        log.error("发送初始错误信息失败", sendError);
                    } finally {
                        isCompleted.set(true);
                        try { emitter.complete(); } catch (Exception ignore) {}
                    }
                }
                cancelHeartbeat(heartbeatRef[0]);
            } finally {
                // 清理安全上下文
                SecurityContextHolder.clearContext();
            }
        });

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("X-Accel-Buffering", "no") // 禁用Nginx缓冲
                .body(emitter);
    }

    private DeferredResult<ResponseEntity<ApiResponse<ChatResponseVO>>> handleNormalResponse(ChatRequest request,
            String userId) {
        DeferredResult<ResponseEntity<ApiResponse<ChatResponseVO>>> deferredResult =
                new DeferredResult<>(chatMessagingProperties.getRequestTimeoutMs() + 5000L);

        ChatQueueTicket ticket = chatMessageQueueService.submit(request, userId);
        CompletableFuture<ChatResponseVO> future = ticket.future()
                .orTimeout(chatMessagingProperties.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);

        future.thenAccept(response -> {
            if (!deferredResult.isSetOrExpired()) {
                deferredResult.setResult(ResponseEntity.ok(ApiResponse.success("对话完成", response)));
            }
        }).exceptionally(ex -> {
            Throwable actual =
                    (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
            int status = (actual instanceof TimeoutException) ? 504 : 500;
            String message;
            if (actual instanceof TimeoutException) {
                message = "AI处理超时";
            } else {
                message = actual.getMessage() == null ? "对话请求失败" : actual.getMessage();
            }
            if (!deferredResult.isSetOrExpired()) {
                deferredResult.setResult(ResponseEntity.status(status)
                        .body(ApiResponse.error(message, status)));
            }
            return null;
        });

        deferredResult.onTimeout(() -> {
            log.warn("请求处理超时，requestId={}", ticket.requestId());
            if (!deferredResult.isSetOrExpired()) {
                deferredResult.setErrorResult(ResponseEntity.status(504)
                        .body(ApiResponse.error("AI处理超时", 504)));
            }
        });

        deferredResult.onError(error -> {
            log.error("DeferredResult执行失败", error);
            if (!deferredResult.isSetOrExpired()) {
                deferredResult.setErrorResult(ResponseEntity.status(500)
                        .body(ApiResponse.error("对话请求失败", 500)));
            }
        });

        return deferredResult;
    }

    private String resolveSessionId(ChatRequest request) {
        if (request != null && StringUtils.hasText(request.getSessionId())) {
            return request.getSessionId();
        }
        return UUID.randomUUID().toString();
    }

    private String deriveSessionTitle(ChatRequest request) {
        if (request == null || request.getMessages() == null) {
            return null;
        }
        for (ChatRequest.ChatMessage msg : request.getMessages()) {
            if (msg != null && "user".equalsIgnoreCase(msg.getRole()) && StringUtils.hasText(msg.getContent())) {
                String trimmed = msg.getContent().strip();
                return trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed;
            }
        }
        return null;
    }

    private ChatSessionVO toSessionVO(ChatSession session) {
        if (session == null) {
            return null;
        }
        return ChatSessionVO.builder()
                .sessionId(session.getId())
                .title(session.getTitle())
                .firstMessage(session.getFirstMessage())
                .model(session.getModel())
                .messageCount(session.getMessageCount())
                .lastMessageAt(session.getLastMessageAt())
                .createdAt(session.getCreatedAt())
                .build();
    }

    private ChatMessageVO toMessageVO(ChatMessageRecord record) {
        if (record == null) {
            return null;
        }
        return ChatMessageVO.builder()
                .sessionId(record.getSessionId())
                .role(record.getRole())
                .content(record.getContent())
                .seq(record.getSeq())
                .createdAt(record.getCreatedAt())
                .build();
    }

    private String extractLatestUserMessage(ChatRequest request) {
        if (request == null || request.getMessages() == null) {
            return null;
        }
        for (int i = request.getMessages().size() - 1; i >= 0; i--) {
            ChatRequest.ChatMessage msg = request.getMessages().get(i);
            if (msg != null && "user".equalsIgnoreCase(msg.getRole()) && StringUtils.hasText(msg.getContent())) {
                return msg.getContent();
            }
        }
        return null;
    }

    private void publishHistory(String sessionId,
                                String userId,
                                String sessionTitle,
                                String userMessage,
                                String assistantReply,
                                String model) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(userMessage)) {
            return;
        }
        List<ChatHistoryPersistPayload.Message> messages = new java.util.ArrayList<>();
        messages.add(ChatHistoryPersistPayload.Message.builder()
                .role("user")
                .content(userMessage)
                .createdAt(Instant.now())
                .build());
        if (StringUtils.hasText(assistantReply)) {
            messages.add(ChatHistoryPersistPayload.Message.builder()
                    .role("assistant")
                    .content(assistantReply)
                    .createdAt(Instant.now())
                    .build());
        }
        ChatHistoryPersistPayload payload = ChatHistoryPersistPayload.builder()
                .sessionId(sessionId)
                .userId(userId)
                .sessionTitle(StringUtils.hasText(sessionTitle) ? sessionTitle : userMessage)
                .model(model)
                .createdAt(Instant.now())
                .messages(messages)
                .build();
        chatHistoryQueueProducer.publish(payload);
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private ScheduledFuture<?> scheduleHeartbeat(SseEmitter emitter,
                                                 AtomicBoolean isCompleted,
                                                 Disposable[] subscriptionRef) {
        return heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (isCompleted.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
            } catch (Exception heartbeatError) {
                log.debug("发送SSE心跳失败，结束连接: {}", heartbeatError.getMessage());
                if (isCompleted.compareAndSet(false, true)) {
                    try { emitter.complete(); } catch (Exception ignore) {}
                }
                if (subscriptionRef[0] != null && !subscriptionRef[0].isDisposed()) {
                    subscriptionRef[0].dispose();
                }
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void cancelHeartbeat(ScheduledFuture<?> heartbeatFuture) {
        if (heartbeatFuture != null && !heartbeatFuture.isCancelled()) {
            heartbeatFuture.cancel(true);
        }
    }
}
