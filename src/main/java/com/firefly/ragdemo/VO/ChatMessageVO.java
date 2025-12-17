package com.firefly.ragdemo.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatMessageVO {

    private String sessionId;
    private String role;
    private String content;
    private Integer seq;
    private LocalDateTime createdAt;
}
