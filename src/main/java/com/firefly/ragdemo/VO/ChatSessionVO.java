package com.firefly.ragdemo.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatSessionVO {

    private String sessionId;
    private String title;
    private String firstMessage;
    private String model;
    private Integer messageCount;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
