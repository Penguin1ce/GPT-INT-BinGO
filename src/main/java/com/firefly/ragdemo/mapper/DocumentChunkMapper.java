package com.firefly.ragdemo.mapper;

import com.firefly.ragdemo.entity.DocumentChunk;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentChunkMapper {

    int batchInsertIgnore(@Param("chunks") List<DocumentChunk> chunks);
}
