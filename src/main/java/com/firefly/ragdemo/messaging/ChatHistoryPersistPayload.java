package com.firefly.ragdemo.messaging;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryPersistPayload {

    private String sessionId;
    private String userId;
    private String sessionTitle;
    private String model;
    private Instant createdAt;
    private List<Message> messages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
        private Instant createdAt;
    }
}
