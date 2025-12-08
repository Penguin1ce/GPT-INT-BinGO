package com.firefly.ragdemo.service;

import com.firefly.ragdemo.VO.FileVO;
import com.firefly.ragdemo.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseBulkUploadServiceTest {

    @Mock
    private FileService fileService;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @InjectMocks
    private KnowledgeBaseBulkUploadService bulkUploadService;

    @TempDir
    Path tempDir;

    @Test
    void shouldUploadAllFilesInDirectoryToSharedKnowledgeBase() throws Exception {
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.md");
        Files.writeString(file1, "hello");
        Files.writeString(file2, "world");
        Files.createDirectory(tempDir.resolve("nested")); // 目录应被忽略

        User user = User.builder().id("user-1").username("tester").build();
        String sharedKbId = "kb_shared_cpp_tutorial";

        when(knowledgeBaseService.resolveUploadKb(user.getId(), user.getUsername(), sharedKbId))
                .thenReturn(sharedKbId);

        AtomicInteger counter = new AtomicInteger();
        when(fileService.uploadFile(any(MultipartFile.class), eq(user), eq(sharedKbId)))
                .thenAnswer(invocation -> FileVO.builder()
                        .id("file-" + counter.incrementAndGet())
                        .kbId(sharedKbId)
                        .build());

        List<FileVO> uploaded = bulkUploadService.uploadDirectory(tempDir, user, sharedKbId);

        assertThat(uploaded).hasSize(2);
        assertThat(uploaded).allMatch(vo -> sharedKbId.equals(vo.getKbId()));

        verify(knowledgeBaseService).resolveUploadKb(user.getId(), user.getUsername(), sharedKbId);
        verify(fileService, times(2)).uploadFile(any(MultipartFile.class), eq(user), eq(sharedKbId));
    }
}
