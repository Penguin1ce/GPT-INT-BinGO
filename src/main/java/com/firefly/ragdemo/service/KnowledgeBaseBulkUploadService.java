package com.firefly.ragdemo.service;

import com.firefly.ragdemo.VO.FileVO;
import com.firefly.ragdemo.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseBulkUploadService {

    private final FileService fileService;
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 将目录下的所有文件上传到指定知识库（可用于公共知识库导入）
     */
    public List<FileVO> uploadDirectory(Path directory, User user, String targetKbId) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("目录不存在或不可访问: " + directory);
        }
        String resolvedKbId = knowledgeBaseService.resolveUploadKb(user.getId(), user.getUsername(), targetKbId);
        List<FileVO> uploaded = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    MultipartFile multipartFile = toMultipartFile(path);
                    FileVO vo = fileService.uploadFile(multipartFile, user, resolvedKbId);
                    uploaded.add(vo);
                } catch (Exception e) {
                    log.warn("上传文件失败，已跳过: {} ({})", path, e.getMessage());
                }
            });
        }
        return uploaded;
    }

    private MultipartFile toMultipartFile(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String originalName = path.getFileName().toString();
        String contentType = Files.probeContentType(path);
        return new SimplePathMultipartFile(originalName, bytes, contentType);
    }

    /**
     * 轻量级 MultipartFile 实现，避免引入额外依赖
     */
    private static class SimplePathMultipartFile implements MultipartFile {

        private final String originalFilename;
        private final byte[] content;
        private final String contentType;

        SimplePathMultipartFile(String originalFilename, byte[] content, String contentType) {
            this.originalFilename = originalFilename;
            this.content = content;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return StringUtils.hasText(originalFilename) ? originalFilename : "file-" + Instant.now().toEpochMilli();
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }
}
