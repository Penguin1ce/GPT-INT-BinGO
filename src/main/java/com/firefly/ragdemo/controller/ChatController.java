package com.firefly.ragdemo.controller;

import com.firefly.ragdemo.DTO.ChatRequest;
import com.firefly.ragdemo.VO.ApiResponse;
import com.firefly.ragdemo.VO.ChatResponseVO;
import com.firefly.ragdemo.secutiry.CustomUserPrincipal;
import com.firefly.ragdemo.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.Disposable;
import java.util.concurrent.atomic.AtomicBoolean;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
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
    public ResponseEntity<?> ask(@Valid @RequestBody ChatRequest request,
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

        try {
            String userId = principal.getUserId();

            if (Boolean.TRUE.equals(request.getStream())) {
                // 流式响应
                return handleStreamResponse(request, userId);
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

    private ResponseEntity<SseEmitter> handleStreamResponse(ChatRequest request, String userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 通过原子标志跟踪是否已完成，避免完成后继续发送
        AtomicBoolean isCompleted = new AtomicBoolean(false);
        // 订阅引用，便于在完成/出错时取消
        final Disposable[] subscriptionRef = new Disposable[1];
        final ScheduledFuture<?>[] heartbeatRef = new ScheduledFuture<?>[1];

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

    private ResponseEntity<ApiResponse<ChatResponseVO>> handleNormalResponse(ChatRequest request, String userId) {
        try {
            ChatResponseVO response = chatService.chat(request, userId);
            ApiResponse<ChatResponseVO> apiResponse = ApiResponse.success("对话完成", response);
            return ResponseEntity.ok(apiResponse);

        } catch (Exception e) {
            log.error("对话请求处理失败", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("对话请求失败"));
        }
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
