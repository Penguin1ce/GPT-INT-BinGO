package com.firefly.ragdemo.service;

import java.util.List;

public interface RagRetrievalService {

    /**
     * 按指定知识库集合检索上下文
     */
    List<String> retrieveContext(List<String> kbIds, String query, int topK, int candidateLimit);

    /**
     * 按用户检索其可用的全部知识片段（Redis），不依赖数据库
     */
    List<String> retrieveContextByUser(String userId, String query, int topK, int candidateLimit);
}
