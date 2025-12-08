package com.firefly.ragdemo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunk {

    private String id;

    private String userId;

    private String fileId;

    private String kbId;

    private Integer chunkIndex;

    private String content;

    // 将embedding以JSON数组字符串形式存储（MySQL可用JSON/TEXT字段）
    private String embeddingJson;

    private LocalDateTime createdAt;
} 
