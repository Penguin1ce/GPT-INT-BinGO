package com.firefly.ragdemo.messaging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.messaging.chat-session")
public class ChatSessionMessagingProperties {

    private String exchange = "chat.session.exchange";
    private String queue = "chat.session.queue";
    private String routingKey = "chat.session.routing";
    private int concurrency = 2;
}
