package com.firefly.ragdemo.service;

import java.util.List;

public interface RagRetrievalService {

    /**
     * 按指定知识库集合检索上下文
     */
    List<String> retrieveContext(List<String> kbIds, String query, int topK, int candidateLimit);
}
