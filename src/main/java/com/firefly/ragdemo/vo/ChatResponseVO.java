package com.firefly.ragdemo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatResponseVO {

    private String response;
    private UsageVO usage;
    private String sessionId;
    private String sessionTitle;

    @Data
    @Builder
    public static class UsageVO {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
