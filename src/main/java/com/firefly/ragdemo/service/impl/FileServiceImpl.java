package com.firefly.ragdemo.service.impl;

import com.firefly.ragdemo.vo.FileVO;
import com.firefly.ragdemo.entity.UploadedFile;
import com.firefly.ragdemo.entity.User;
import com.firefly.ragdemo.mapper.DocumentChunkMapper;
import com.firefly.ragdemo.mapper.UploadedFileMapper;
import com.firefly.ragdemo.service.FileService;
import com.firefly.ragdemo.service.RagIndexService;
import com.firefly.ragdemo.repository.RedisDocumentChunkRepository;
import com.firefly.ragdemo.service.KnowledgeBaseService;
import com.firefly.ragdemo.util.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileServiceImpl implements FileService {

    private final UploadedFileMapper uploadedFileMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final RagIndexService ragIndexService;
    private final RedisDocumentChunkRepository redisDocumentChunkRepository;
    private final KnowledgeBaseService knowledgeBaseService;

    @Value("${app.file.upload-dir:uploads}")
    private String uploadDir;

    private final List<String> ALLOWED_EXTENSIONS = Arrays.asList("txt", "md", "pdf", "docx");
    private final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @Override
    @Transactional
    public FileVO uploadFile(MultipartFile file, User user) throws IOException {
        return uploadFile(file, user, null);
    }

    @Override
    @Transactional
    public FileVO uploadFile(MultipartFile file, User user, String kbId) throws IOException {
        validateFile(file);

        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String newFilename = UUID.randomUUID().toString() + "." + extension;
        Path filePath = uploadPath.resolve(newFilename);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        String resolvedKbId = knowledgeBaseService.resolveUploadKb(user.getId(), user.getUsername(), kbId);

        UploadedFile uploadedFile = UploadedFile.builder()
                .id(UUID.randomUUID().toString())
                .userId(user.getId())
                .filename(originalFilename)
                .filePath(filePath.toString())
                .fileSize(file.getSize())
                .fileType(extension)
                .uploadTime(LocalDateTime.now())
                .kbId(resolvedKbId)
                .status(UploadedFile.FileStatus.PROCESSING)
                .build();

        uploadedFileMapper.insert(uploadedFile);

        processFileAfterCommit(uploadedFile.getId());

        return FileVO.builder()
                .id(uploadedFile.getId())
                .filename(uploadedFile.getFilename())
                .fileSize(uploadedFile.getFileSize())
                .fileType(uploadedFile.getFileType())
                .uploadTime(uploadedFile.getUploadTime())
                .kbId(uploadedFile.getKbId())
                .status(uploadedFile.getStatus())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<FileVO> getUserFiles(String userId, int page, int limit) {
        int pageIndex = Math.max(page, 1);
        int pageSize = Math.max(limit, 1);
        int offset = (pageIndex - 1) * pageSize;

        List<UploadedFile> files = uploadedFileMapper.findByUserIdOrderByUploadTimeDesc(userId, offset, pageSize);
        long total = uploadedFileMapper.countByUserId(userId);
        int totalPages = (int) Math.ceil(total / (double) pageSize);

        List<FileVO> items = new ArrayList<>();
        for (UploadedFile f : files) {
            items.add(FileVO.builder()
                    .id(f.getId())
                    .filename(f.getFilename())
                    .fileSize(f.getFileSize())
                    .fileType(f.getFileType())
                    .uploadTime(f.getUploadTime())
                    .kbId(f.getKbId())
                    .status(f.getStatus())
                    .build());
        }

        return PageResult.<FileVO>builder()
                .page(pageIndex)
                .limit(pageSize)
                .total(total)
                .totalPages(totalPages)
                .items(items)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UploadedFile> findById(String fileId) {
        return uploadedFileMapper.findById(fileId);
    }

    @Override
    @Transactional
    public void deleteUserFile(String userId, String fileId) {
        Optional<UploadedFile> fileOpt = uploadedFileMapper.findById(fileId);
        if (fileOpt.isEmpty()) {
            return;
        }
        UploadedFile file = fileOpt.get();
        if (!Objects.equals(file.getUserId(), userId)) {
            throw new AccessDeniedException("无权删除他人文件");
        }
        // 删除Redis中的chunks
        redisDocumentChunkRepository.deleteByFileIdAndUser(fileId, userId, file.getKbId());
        // 删除MySQL中的chunks
        int deletedChunks = documentChunkMapper.deleteByFileId(fileId);
        log.info("已删除文件{}的MySQL chunks记录: {}条", fileId, deletedChunks);
        // 删除磁盘文件
        try {
            Path p = Paths.get(file.getFilePath());
            Files.deleteIfExists(p);
        } catch (Exception e) {
            log.warn("删除磁盘文件失败: {} ({})", file.getFilePath(), e.getMessage());
        }
        // 删除uploaded_files记录
        uploadedFileMapper.deleteById(fileId);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过10MB");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String extension = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("不支持的文件类型，支持的类型: " + String.join(", ", ALLOWED_EXTENSIONS));
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1);
        }
        return "";
    }

    private void processFileAfterCommit(String fileId) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            log.info("事务提交后开始异步索引文件: {}", fileId);
                            ragIndexService.indexFile(fileId);
                        }
                    }
            );
        } else {
            log.info("直接开始异步索引文件: {}", fileId);
            ragIndexService.indexFile(fileId);
        }
    }
}
