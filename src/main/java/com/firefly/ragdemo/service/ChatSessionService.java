package com.firefly.ragdemo.service;

import com.firefly.ragdemo.dto.ChatRequest;
import com.firefly.ragdemo.entity.ChatMessageRecord;
import com.firefly.ragdemo.entity.ChatSession;
import com.firefly.ragdemo.messaging.ChatHistoryPersistPayload;
import java.util.List;

public interface ChatSessionService {

    void persistFromQueue(ChatHistoryPersistPayload payload);

    List<ChatSession> listSessions(String userId, int page, int limit);

    long countSessions(String userId);

    List<ChatMessageRecord> listMessages(String sessionId, String userId, int limit);

    List<ChatRequest.ChatMessage> buildHistory(String sessionId, String userId, int limit);
}
