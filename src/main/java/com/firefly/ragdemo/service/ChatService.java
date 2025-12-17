package com.firefly.ragdemo.service;

import com.firefly.ragdemo.dto.ChatRequest;
import com.firefly.ragdemo.vo.ChatResponseVO;
import reactor.core.publisher.Flux;

public interface ChatService {

    ChatResponseVO chat(ChatRequest request, String userId);

    Flux<String> chatStream(ChatRequest request, String userId);
}
