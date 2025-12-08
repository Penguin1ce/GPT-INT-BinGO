package com.firefly.ragdemo.mapper;

import com.firefly.ragdemo.entity.ChatMessageRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatMessageRecordMapper {

    List<ChatMessageRecord> findBySession(@Param("sessionId") String sessionId,
                                          @Param("userId") String userId,
                                          @Param("limit") int limit);

    int batchInsert(@Param("messages") List<ChatMessageRecord> messages);
}
