package com.firefly.ragdemo.service.impl;

import com.firefly.ragdemo.entity.KnowledgeBase;
import com.firefly.ragdemo.mapper.KnowledgeBaseMapper;
import com.firefly.ragdemo.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final String DEFAULT_SHARED_ID = "kb_shared_cpp_tutorial";

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    @Transactional
    public List<String> listAccessibleKbIds(String userId) {
        Set<String> ids = new HashSet<>(knowledgeBaseMapper.findAccessibleKbIds(userId));

        // 确保私人知识库存在
        String privateKbId = ensurePrivateKnowledgeBase(userId, null);
        ids.add(privateKbId);

        // 始终包含共享知识库
        String sharedId = ensureDefaultSharedKb();
        if (sharedId != null) {
            ids.add(sharedId);
        }

        return new ArrayList<>(ids);
    }

    @Override
    @Transactional
    public String resolveUploadKb(String userId, String username, String requestedKbId) {
        if (!StringUtils.hasText(requestedKbId)) {
            return ensurePrivateKnowledgeBase(userId, username);
        }
        Optional<KnowledgeBase> kbOpt = knowledgeBaseMapper.findById(requestedKbId);
        KnowledgeBase kb = kbOpt.orElseGet(() -> {
            if (DEFAULT_SHARED_ID.equals(requestedKbId)) {
                String ensured = ensureDefaultSharedKb();
                return knowledgeBaseMapper.findById(ensured).orElse(null);
            }
            return null;
        });
        if (kb == null) {
            throw new IllegalArgumentException("目标知识库不存在: " + requestedKbId);
        }
        if (kb.getIsActive() != null && !kb.getIsActive()) {
            throw new IllegalArgumentException("目标知识库不可用: " + requestedKbId);
        }
        if ("PRIVATE".equalsIgnoreCase(kb.getType()) && kb.getOwnerId() != null && !kb.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("无权上传到该私人知识库");
        }
        return kb.getId();
    }

    @Override
    @Transactional
    public String ensureDefaultSharedKb() {
        // 优先返回已有的共享知识库
        List<String> sharedIds = knowledgeBaseMapper.findActiveSharedIds();
        if (!sharedIds.isEmpty()) {
            return sharedIds.get(0);
        }
        // 若不存在，则创建一个默认共享知识库
        KnowledgeBase kb = KnowledgeBase.builder()
                .id(DEFAULT_SHARED_ID)
                .name("C++教学官方知识库")
                .description("默认公共知识库")
                .type("SHARED")
                .ownerId(null)
                .isActive(true)
                .build();
        try {
            knowledgeBaseMapper.insert(kb);
            return kb.getId();
        } catch (Exception e) {
            // 并发创建时若已存在，尝试再读一次
            log.warn("创建默认公共知识库失败，尝试复用已有记录: {}", e.getMessage());
            List<String> fallback = knowledgeBaseMapper.findActiveSharedIds();
            return fallback.isEmpty() ? null : fallback.get(0);
        }
    }

    private String ensurePrivateKnowledgeBase(String userId, String username) {
        KnowledgeBase existing = knowledgeBaseMapper.findPrivateByOwner(userId);
        if (existing != null) {
            return existing.getId();
        }
        String kbId = "kb_private_" + userId;
        String name = (StringUtils.hasText(username) ? username : "用户") + "的私人知识库";
        KnowledgeBase kb = KnowledgeBase.builder()
                .id(kbId)
                .name(name)
                .description("用户个人学习资料和笔记")
                .type("PRIVATE")
                .ownerId(userId)
                .isActive(true)
                .build();
        try {
            knowledgeBaseMapper.insert(kb);
            return kbId;
        } catch (Exception e) {
            log.warn("创建私人知识库失败，将尝试复用已存在记录: {}", e.getMessage());
            KnowledgeBase retry = knowledgeBaseMapper.findPrivateByOwner(userId);
            return retry != null ? retry.getId() : kbId;
        }
    }
}
