package com.firefly.ragdemo.messaging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.messaging.chat")
public class ChatMessagingProperties {

    private String exchange = "ai.chat.exchange";
    private String queue = "ai.chat.queue";
    private String routingKey = "ai.chat.routing";
    private int concurrency = 3;
    private long requestTimeoutMs = 45000L;
}
