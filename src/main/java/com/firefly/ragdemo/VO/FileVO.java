package com.firefly.ragdemo.VO;

import com.firefly.ragdemo.entity.UploadedFile.FileStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

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
