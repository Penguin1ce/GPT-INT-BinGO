package com.firefly.ragdemo.mapper;

import com.firefly.ragdemo.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface KnowledgeBaseMapper {

    Optional<KnowledgeBase> findById(@Param("id") String id);

    KnowledgeBase findPrivateByOwner(@Param("ownerId") String ownerId);

    List<String> findAccessibleKbIds(@Param("userId") String userId);

    List<String> findActiveSharedIds();

    int insert(KnowledgeBase knowledgeBase);
}
