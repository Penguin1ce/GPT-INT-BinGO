package com.firefly.ragdemo.service.impl;

import com.firefly.ragdemo.DTO.ChatRequest;
import com.firefly.ragdemo.entity.ChatMessageRecord;
import com.firefly.ragdemo.entity.ChatSession;
import com.firefly.ragdemo.mapper.ChatMessageRecordMapper;
import com.firefly.ragdemo.mapper.ChatSessionMapper;
import com.firefly.ragdemo.messaging.ChatHistoryPersistPayload;
import com.firefly.ragdemo.service.ChatSessionService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSessionServiceImpl implements ChatSessionService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_LIMIT = 100;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageRecordMapper chatMessageRecordMapper;

    @Override
    @Transactional
    public void persistFromQueue(ChatHistoryPersistPayload payload) {
        if (payload == null || !StringUtils.hasText(payload.getSessionId()) || !StringUtils.hasText(payload.getUserId())) {
            log.warn("会话持久化参数缺失，sessionId={}, userId={}", payload != null ? payload.getSessionId() : null,
                    payload != null ? payload.getUserId() : null);
            return;
        }
        LocalDateTime baseTime = payload.getCreatedAt() != null
                ? LocalDateTime.ofInstant(payload.getCreatedAt(), ZoneOffset.UTC)
                : LocalDateTime.now();
        ChatSession session = chatSessionMapper.findById(payload.getSessionId()).orElseGet(() -> {
            String firstUserMessage = extractFirstUserMessage(payload);
            ChatSession newSession = ChatSession.builder()
                    .id(payload.getSessionId())
                    .userId(payload.getUserId())
                    .title(resolveTitle(payload.getSessionTitle(), firstUserMessage))
                    .firstMessage(firstUserMessage)
                    .model(payload.getModel())
                    .messageCount(0)
                    .lastMessageAt(baseTime)
                    .createdAt(baseTime)
                    .updatedAt(baseTime)
                    .build();
            chatSessionMapper.insert(newSession);
            return newSession;
        });

        List<ChatHistoryPersistPayload.Message> incoming = payload.getMessages();
        if (incoming == null || incoming.isEmpty()) {
            return;
        }

        int seq = session.getMessageCount() != null ? session.getMessageCount() : 0;
        List<ChatMessageRecord> toInsert = new ArrayList<>();
        LocalDateTime lastMessageAt = session.getLastMessageAt();
        for (ChatHistoryPersistPayload.Message msg : incoming) {
            if (msg == null || !StringUtils.hasText(msg.getRole()) || !StringUtils.hasText(msg.getContent())) {
                continue;
            }
            LocalDateTime ts = msg.getCreatedAt() != null
                    ? LocalDateTime.ofInstant(msg.getCreatedAt(), ZoneOffset.UTC)
                    : baseTime;
            seq += 1;
            ChatMessageRecord record = ChatMessageRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .sessionId(session.getId())
                    .userId(session.getUserId())
                    .role(msg.getRole())
                    .content(msg.getContent())
                    .seq(seq)
                    .model(payload.getModel())
                    .createdAt(ts)
                    .build();
            toInsert.add(record);
            lastMessageAt = ts;
        }
        if (toInsert.isEmpty()) {
            return;
        }
        chatMessageRecordMapper.batchInsert(toInsert);
        chatSessionMapper.updateStats(session.getId(), seq, lastMessageAt != null ? lastMessageAt : baseTime);
    }

    @Override
    public List<ChatSession> listSessions(String userId, int page, int limit) {
        if (!StringUtils.hasText(userId)) {
            return Collections.emptyList();
        }
        int safePage = page > 0 ? page : DEFAULT_PAGE;
        int safeLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        int offset = (safePage - 1) * safeLimit;
        return chatSessionMapper.findByUser(userId, offset, safeLimit);
    }

    @Override
    public long countSessions(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        return chatSessionMapper.countByUser(userId);
    }

    @Override
    public List<ChatMessageRecord> listMessages(String sessionId, String userId, int limit) {
        Optional<ChatSession> sessionOpt = chatSessionMapper.findById(sessionId);
        if (sessionOpt.isEmpty() || !userId.equals(sessionOpt.get().getUserId())) {
            throw new IllegalArgumentException("无权访问该会话或会话不存在");
        }
        int safeLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        return chatMessageRecordMapper.findBySession(sessionId, userId, safeLimit);
    }

    @Override
    public List<ChatRequest.ChatMessage> buildHistory(String sessionId, String userId, int limit) {
        List<ChatMessageRecord> records = listMessages(sessionId, userId, limit);
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatRequest.ChatMessage> history = new ArrayList<>();
        for (ChatMessageRecord record : records) {
            ChatRequest.ChatMessage msg = new ChatRequest.ChatMessage();
            msg.setRole(record.getRole());
            msg.setContent(record.getContent());
            history.add(msg);
        }
        return history;
    }

    private String extractFirstUserMessage(ChatHistoryPersistPayload payload) {
        if (payload.getMessages() == null) {
            return null;
        }
        for (ChatHistoryPersistPayload.Message msg : payload.getMessages()) {
            if (msg != null && "user".equalsIgnoreCase(msg.getRole()) && StringUtils.hasText(msg.getContent())) {
                return msg.getContent();
            }
        }
        return null;
    }

    private String resolveTitle(String preferred, String fallback) {
        String base = StringUtils.hasText(preferred) ? preferred : fallback;
        if (!StringUtils.hasText(base)) {
            return "新的对话";
        }
        String trimmed = base.strip();
        return trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed;
    }
}
