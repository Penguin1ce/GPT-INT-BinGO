package com.firefly.ragdemo.mapper;

import com.firefly.ragdemo.entity.ChatSession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatSessionMapper {

    Optional<ChatSession> findById(@Param("id") String id);

    List<ChatSession> findByUser(@Param("userId") String userId,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    long countByUser(@Param("userId") String userId);

    int insert(ChatSession session);

    int updateStats(@Param("id") String id,
                    @Param("messageCount") int messageCount,
                    @Param("lastMessageAt") LocalDateTime lastMessageAt);
}
