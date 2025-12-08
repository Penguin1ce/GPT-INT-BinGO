package com.firefly.ragdemo.messaging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.messaging.chunk-sync")
public class ChunkSyncMessagingProperties {

    private String exchange = "kb.chunk.sync.exchange";
    private String queue = "kb.chunk.sync.queue";
    private String routingKey = "kb.chunk.sync.routing";
    private int concurrency = 2;
}
