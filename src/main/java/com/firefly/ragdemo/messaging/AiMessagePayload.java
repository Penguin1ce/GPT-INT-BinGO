package com.firefly.ragdemo.messaging;

import com.firefly.ragdemo.DTO.ChatRequest;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessagePayload {

    private String requestId;
    private String userId;
    private ChatRequest chatRequest;
    private Instant createdAt;
}
