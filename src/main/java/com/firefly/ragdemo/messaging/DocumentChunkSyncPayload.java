package com.firefly.ragdemo.messaging;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunkSyncPayload {

    private String fileId;
    private String userId;
    private String kbId;
    private Instant createdAt;
}
