package com.firefly.ragdemo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ChatRequest {

    private String model;

    @NotEmpty(message = "消息列表不能为空")
    private List<ChatMessage> messages;

    private Boolean stream = false;

    private String langid;

    // 会话ID，由前端生成或后端回传
    private String sessionId;

    @Data
    public static class ChatMessage {
        @NotNull(message = "角色不能为空")
        private String role;

        @NotNull(message = "内容不能为空")
        private String content;
    }
}
