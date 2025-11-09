package com.firefly.ragdemo.VO;

import com.firefly.ragdemo.entity.UploadedFile;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class FileProcessingNotification {

    String fileId;
    String filename;
    String status;
    String message;
    LocalDateTime timestamp;

    public static FileProcessingNotification from(UploadedFile file, UploadedFile.FileStatus status, String message) {
        return FileProcessingNotification.builder()
                .fileId(file.getId())
                .filename(file.getFilename())
                .status(status != null ? status.name() : null)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
