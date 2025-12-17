package com.firefly.ragdemo.service.impl;

import com.firefly.ragdemo.vo.FileProcessingNotification;
import com.firefly.ragdemo.entity.UploadedFile;
import com.firefly.ragdemo.service.FileProcessingNotificationService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class FileProcessingNotificationServiceImpl implements FileProcessingNotificationService {

    private static final long SSE_TIMEOUT_MS = 0L; // No timeout, VS Code 插件主动断开

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emittersByUser.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onTimeout(() -> completeEmitter(userId, emitter));
        emitter.onCompletion(() -> completeEmitter(userId, emitter));
        emitter.onError(ex -> completeEmitter(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("listening"));
        } catch (IOException e) {
            log.debug("SSE连接初始化失败: {}", e.getMessage());
            completeEmitter(userId, emitter);
        }
        return emitter;
    }

    @Override
    public void notifyStatus(UploadedFile file, UploadedFile.FileStatus status, String message) {
        if (file == null || file.getUserId() == null) {
            return;
        }
        FileProcessingNotification notification = FileProcessingNotification.from(file, status, message);
        List<SseEmitter> emitters = emittersByUser.get(file.getUserId());
        if (emitters == null || emitters.isEmpty()) {
            log.debug("用户{}暂无SSE连接，跳过推送", file.getUserId());
            return;
        }
        emitters.removeIf(Objects::isNull);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("file-processing").data(notification));
            } catch (Exception e) {
                log.debug("推送给用户{}失败: {}", file.getUserId(), e.getMessage());
                completeEmitter(file.getUserId(), emitter);
            }
        }
    }

    @PreDestroy
    public void shutdownEmitters() {
        emittersByUser.forEach((userId, emitters) -> emitters.forEach(emitter -> completeEmitter(userId, emitter)));
        emittersByUser.clear();
    }

    private void completeEmitter(String userId, SseEmitter emitter) {
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("关闭用户{}的SSE连接异常: {}", userId, e.getMessage());
            }
        }
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(userId);
        }
    }
}
