package com.firefly.ragdemo.service;

import java.util.List;

public interface KnowledgeBaseService {

    /**
     * 获取用户可访问的知识库（公共 + 私人 + 授权）
     */
    List<String> listAccessibleKbIds(String userId);

    /**
     * 解析上传目标知识库：优先用户指定，否则回落到私人知识库。
     */
    String resolveUploadKb(String userId, String username, String requestedKbId);

    /**
     * 获取一个默认公共知识库ID（若不存在则自动创建）。
     */
    String ensureDefaultSharedKb();
}
