package com.firefly.ragdemo.service;

import com.firefly.ragdemo.entity.UploadedFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface FileProcessingNotificationService {

    SseEmitter subscribe(String userId);

    void notifyStatus(UploadedFile file, UploadedFile.FileStatus status, String message);
}
