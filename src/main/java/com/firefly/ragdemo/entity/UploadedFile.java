package com.firefly.ragdemo.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadedFile {

    private String id;

    private String userId;

    private String filename;

    private String filePath;

    private Long fileSize;

    private String fileType;

    private LocalDateTime uploadTime;

    private String kbId;

    @Builder.Default
    private FileStatus status = FileStatus.PROCESSING;

    public enum FileStatus {
        PROCESSING, COMPLETED, FAILED
    }
}
