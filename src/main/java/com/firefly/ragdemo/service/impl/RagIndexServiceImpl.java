package com.firefly.ragdemo.service.impl;

import com.firefly.ragdemo.entity.DocumentChunk;
import com.firefly.ragdemo.entity.UploadedFile;
import com.firefly.ragdemo.mapper.UploadedFileMapper;
import com.firefly.ragdemo.repository.RedisDocumentChunkRepository;
import com.firefly.ragdemo.service.EmbeddingService;
import com.firefly.ragdemo.service.RagIndexService;
import com.firefly.ragdemo.service.TextChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagIndexServiceImpl implements RagIndexService {

    private final UploadedFileMapper uploadedFileMapper;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final RedisDocumentChunkRepository redisDocumentChunkRepository;

    private final Tika tika = new Tika();

    @Override
    @Transactional
    public void indexFile(String fileId) {
        Optional<UploadedFile> fileOpt = uploadedFileMapper.findById(fileId);
        if (fileOpt.isEmpty()) {
            log.warn("找不到文件: {}", fileId);
            return;
        }
        UploadedFile file = fileOpt.get();
        try {
            Path path = Paths.get(file.getFilePath());
            String text;
            if (Files.exists(path)) {
                text = tika.parseToString(path);
            } else {
                log.warn("文件不存在于磁盘: {}", file.getFilePath());
                text = "";
            }
            log.info("索引提取文本长度: {} (fileId={})", text != null ? text.length() : 0, fileId);
            List<String> chunks = textChunker.split(text);
            log.info("分块数量: {} (fileId={})", chunks.size(), fileId);
            if (chunks.isEmpty()) {
                log.info("文件无可索引内容: {}", fileId);
                uploadedFileMapper.updateStatus(fileId, UploadedFile.FileStatus.COMPLETED.name());
                return;
            }
            List<List<Double>> embeddings = embeddingService.embedBatch(chunks);
            log.info("已生成向量数: {} (fileId={})", embeddings != null ? embeddings.size() : 0, fileId);
            List<DocumentChunk> entities = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                List<Double> vec = embeddings.get(i);
                String json = toJsonArray(vec);
                entities.add(DocumentChunk.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(file.getUserId())
                        .fileId(file.getId())
                        .chunkIndex(i)
                        .content(chunks.get(i))
                        .embeddingJson(json)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
            redisDocumentChunkRepository.saveAll(entities);
            log.info("已写入Redis分块记录数: {} (fileId={})", entities.size(), fileId);
            uploadedFileMapper.updateStatus(fileId, UploadedFile.FileStatus.COMPLETED.name());
        } catch (Exception e) {
            log.error("索引文件失败: {}", fileId, e);
            uploadedFileMapper.updateStatus(fileId, UploadedFile.FileStatus.FAILED.name());
        }
    }

    private String toJsonArray(List<Double> vec) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < vec.size(); i++) {
            if (i > 0) sb.append(',');
            // 保留足够精度
            sb.append(String.format(java.util.Locale.US, "%.8f", vec.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }
} 
