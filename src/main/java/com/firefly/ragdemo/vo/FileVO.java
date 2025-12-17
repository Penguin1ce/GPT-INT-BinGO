package com.firefly.ragdemo.vo;

import com.firefly.ragdemo.entity.UploadedFile.FileStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileVO {

    private String id;
    private String filename;
    private Long fileSize;
    private String fileType;
    private LocalDateTime uploadTime;
    private FileStatus status;
    private String kbId;
}
