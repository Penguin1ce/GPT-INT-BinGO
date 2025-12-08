package com.firefly.ragdemo.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageRecord {

    private String id;
    private String sessionId;
    private String userId;
    private String role;
    private String content;
    private Integer seq;
    private String model;
    private LocalDateTime createdAt;
}
