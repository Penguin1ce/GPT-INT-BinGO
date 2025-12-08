package com.firefly.ragdemo.mapper;

import com.firefly.ragdemo.entity.UploadedFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UploadedFileMapper {

    Optional<UploadedFile> findById(@Param("id") String id);

    List<UploadedFile> findByUserIdOrderByUploadTimeDesc(@Param("userId") String userId,
                                                         @Param("offset") int offset,
                                                         @Param("limit") int limit);

    long countByUserId(@Param("userId") String userId);

    int insert(UploadedFile file);

    int updateStatus(@Param("id") String id, @Param("status") String status);

    int update(UploadedFile file);

    int deleteById(@Param("id") String id);
} 
