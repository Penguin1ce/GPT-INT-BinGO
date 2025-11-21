# 知识库系统快速上手指南

## 🚀 快速开始

### 步骤 1：执行数据库迁移

```bash
# 确保PostgreSQL正在运行
docker ps | grep postgres

# 执行迁移脚本
psql -U postgres -d ragdemo -f migration_knowledge_bases.sql
```

**预期输出**：
```
CREATE TABLE
CREATE INDEX
INSERT 0 1
NOTICE: 所有文件已正确关联到知识库
NOTICE: 知识库系统迁移成功完成！
```

### 步骤 2：验证数据库迁移

```bash
psql -U postgres -d ragdemo
```

```sql
-- 检查表是否创建成功
\dt knowledge_bases
\dt user_knowledge_base_access

-- 查看默认共享知识库
SELECT * FROM knowledge_bases WHERE type = 'SHARED';

-- 查看用户的私人知识库
SELECT id, name, type, owner_id FROM knowledge_bases WHERE type = 'PRIVATE' LIMIT 5;

-- 查看可访问知识库视图
SELECT * FROM v_user_accessible_knowledge_bases LIMIT 10;
```

### 步骤 3：创建示例知识库（可选）

```sql
-- 插入测试用户（如果不存在）
INSERT INTO users (id, username, email, password_hash)
VALUES ('test_user_001', 'testuser', 'test@example.com', '$2a$10$dummyhash')
ON CONFLICT (id) DO NOTHING;

-- 为测试用户创建私人知识库
INSERT INTO knowledge_bases (id, name, description, type, owner_id, is_active)
VALUES (
    'kb_test_private',
    '测试私人知识库',
    '用于测试的私人知识库',
    'PRIVATE',
    'test_user_001',
    TRUE
);

-- 创建另一个共享知识库
INSERT INTO knowledge_bases (id, name, description, type, owner_id, is_active)
VALUES (
    'kb_shared_algorithms',
    '算法与数据结构知识库',
    '计算机算法和数据结构参考资料',
    'SHARED',
    NULL,
    TRUE
);

-- 授予测试用户对某个知识库的写入权限
INSERT INTO user_knowledge_base_access (id, user_id, kb_id, role, granted_by)
VALUES (
    'access_001',
    'test_user_001',
    'kb_shared_cpp_tutorial',
    'WRITER',
    'admin_user_id'
);
```

---

## 📊 核心使用场景

### 场景 1：用户上传文件到私人知识库

```java
// Controller
@PostMapping("/files/upload")
public ApiResponse<UploadedFile> uploadFile(
    @AuthenticationPrincipal UserDetails userDetails,
    @RequestParam("file") MultipartFile file,
    @RequestParam("kbId") String kbId
) {
    String userId = userDetails.getUsername();

    // 1. 验证用户对知识库有写入权限
    boolean hasAccess = knowledgeBaseService.checkAccess(userId, kbId, AccessRole.WRITER);
    if (!hasAccess) {
        throw new ForbiddenException("无权限上传文件到该知识库");
    }

    // 2. 保存文件并关联到知识库
    UploadedFile uploadedFile = fileService.uploadFile(userId, kbId, file);

    return ApiResponse.success(uploadedFile);
}
```

```java
// FileService
public void processFile(String fileId, String kbId) {
    // 1. 读取文件内容
    String content = extractTextContent(fileId);

    // 2. 分块
    List<String> chunks = textChunker.chunk(content);

    // 3. 生成向量并保存到Redis
    List<DocumentChunk> documentChunks = new ArrayList<>();
    for (int i = 0; i < chunks.size(); i++) {
        List<Double> embedding = embeddingService.embed(chunks.get(i));

        DocumentChunk chunk = DocumentChunk.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .fileId(fileId)
            .kbId(kbId)  // ← 关键：设置知识库ID
            .chunkIndex(i)
            .content(chunks.get(i))
            .embeddingJson(objectMapper.writeValueAsString(embedding))
            .createdAt(LocalDateTime.now())
            .build();

        documentChunks.add(chunk);
    }

    // 保存到Redis（会自动创建 rag:kb:{kbId}:chunks 索引）
    redisDocumentChunkRepository.saveAll(documentChunks);
}
```

### 场景 2：从多个知识库检索

```java
// ChatService
@Override
public Flux<String> chat(String userId, String message, List<String> kbIds) {
    // 1. 获取用户可访问的知识库
    List<String> accessibleKbIds = kbIds.stream()
        .filter(kbId -> knowledgeBaseService.checkAccess(userId, kbId, AccessRole.READER))
        .toList();

    if (accessibleKbIds.isEmpty()) {
        throw new ForbiddenException("无权限访问指定的知识库");
    }

    // 2. 从多个知识库检索上下文
    List<String> context = ragRetrievalService.retrieveContext(
        accessibleKbIds,  // 支持多知识库联合检索
        message,
        5,  // topK
        200  // candidateLimit
    );

    // 3. 构建提示词并调用GPT
    String systemPrompt = buildSystemPrompt(context);
    return chatClient.stream(systemPrompt, message);
}
```

### 场景 3：管理知识库权限

```java
// KnowledgeBaseService
@Override
public void grantAccess(String kbId, String userId, AccessRole role, String grantedBy) {
    // 1. 验证授权者权限
    boolean isAdmin = checkAccess(grantedBy, kbId, AccessRole.ADMIN);
    if (!isAdmin) {
        throw new ForbiddenException("仅管理员可授予权限");
    }

    // 2. 创建或更新权限记录
    UserKnowledgeBaseAccess access = UserKnowledgeBaseAccess.builder()
        .id(UUID.randomUUID().toString())
        .userId(userId)
        .kbId(kbId)
        .role(role)
        .grantedBy(grantedBy)
        .grantedAt(LocalDateTime.now())
        .build();

    kbMapper.grantAccess(access);

    // 3. 更新Redis缓存（可选）
    updateUserAccessCache(userId);
}
```

---

## 🔍 检索策略对比

### 策略 A：平等检索（推荐）

每个知识库贡献相同数量的候选chunks。

```java
public List<DocumentChunk> findByKnowledgeBases(List<String> kbIds, int candidateLimit) {
    int perKbLimit = Math.max(candidateLimit / kbIds.size(), 50);

    List<DocumentChunk> allChunks = new ArrayList<>();
    for (String kbId : kbIds) {
        Set<String> chunkIds = stringRedisTemplate.opsForZSet()
            .reverseRange(kbChunksKey(kbId), 0, perKbLimit - 1);

        allChunks.addAll(fetchChunks(chunkIds));
    }
    return allChunks;
}
```

**优点**：保证各知识库都有代表性
**适用**：共享库+私人库联合检索

### 策略 B：加权检索

私人知识库获得更高权重。

```java
public List<DocumentChunk> findByKnowledgeBasesWeighted(
    String userId, List<String> kbIds, int candidateLimit
) {
    Map<String, Double> weights = new HashMap<>();
    for (String kbId : kbIds) {
        KnowledgeBase kb = kbMapper.findById(kbId);
        // 私人知识库权重 2.0，共享知识库权重 1.0
        weights.put(kbId, kb.getOwnerId() != null && kb.getOwnerId().equals(userId) ? 2.0 : 1.0);
    }

    double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();

    List<DocumentChunk> allChunks = new ArrayList<>();
    for (String kbId : kbIds) {
        int perKbLimit = (int) (candidateLimit * weights.get(kbId) / totalWeight);
        Set<String> chunkIds = stringRedisTemplate.opsForZSet()
            .reverseRange(kbChunksKey(kbId), 0, perKbLimit - 1);

        allChunks.addAll(fetchChunks(chunkIds));
    }
    return allChunks;
}
```

**优点**：优先使用用户专属数据
**适用**：个性化学习助手

### 策略 C：智能路由

根据查询意图选择知识库。

```java
public List<String> retrieveContextSmart(String userId, String query) {
    // 1. 使用LLM分析查询意图
    String intent = analyzeQueryIntent(query);  // "personal_notes" | "official_docs"

    // 2. 根据意图选择知识库
    List<String> targetKbIds;
    if ("personal_notes".equals(intent)) {
        targetKbIds = kbMapper.findPrivateKbsByUserId(userId)
            .stream().map(KnowledgeBase::getId).toList();
    } else {
        targetKbIds = kbMapper.findAllShared()
            .stream().map(KnowledgeBase::getId).toList();
    }

    // 3. 从目标知识库检索
    return retrieveContext(targetKbIds, query, 5, 200);
}
```

**优点**：更精准的结果
**缺点**：需要额外LLM调用

---

## 🔧 故障排查

### 问题 1：文件上传后检索不到内容

**原因**：文件未关联到知识库，或知识库权限不足

**排查**：
```sql
-- 检查文件是否关联到知识库
SELECT id, filename, kb_id, status FROM uploaded_files WHERE id = 'your_file_id';

-- 检查用户对知识库的访问权限
SELECT * FROM v_user_accessible_knowledge_bases
WHERE user_id = 'your_user_id' AND kb_id = 'your_kb_id';
```

**解决**：
```sql
-- 手动关联文件到知识库
UPDATE uploaded_files SET kb_id = 'kb_id' WHERE id = 'file_id';
```

### 问题 2：Redis中的chunks未关联到知识库

**原因**：旧数据迁移未完成

**排查**：
```bash
redis-cli

# 检查chunk数据
GET rag:chunk:your_chunk_id

# 检查知识库索引是否存在
ZCARD rag:kb:your_kb_id:chunks
```

**解决**：执行数据迁移脚本（见下方）

### 问题 3：删除知识库后Redis中仍有数据

**原因**：Redis清理逻辑未执行

**解决**：
```java
// 在 KnowledgeBaseService.deleteKnowledgeBase() 中添加
redisDocumentChunkRepository.deleteByKnowledgeBase(kbId);
```

---

## 📝 数据迁移脚本

### Redis 数据迁移

如果已有用户数据，需要为现有chunks添加 `kbId` 字段：

```java
@Component
@RequiredArgsConstructor
public class KnowledgeBaseDataMigrator {

    private final RedisDocumentChunkRepository redisRepository;
    private final FileMapper fileMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final ObjectMapper objectMapper;

    /**
     * 为现有chunks添加kbId字段
     */
    public void migrateExistingChunks() {
        log.info("开始迁移现有chunks到知识库...");

        // 1. 获取所有文件及其关联的知识库
        List<UploadedFile> files = fileMapper.findAll();

        for (UploadedFile file : files) {
            if (file.getKbId() == null) {
                log.warn("文件 {} 未关联知识库，跳过", file.getId());
                continue;
            }

            // 2. 获取文件的所有chunk IDs
            Set<String> chunkIds = stringRedisTemplate.opsForSet()
                .members("rag:file:" + file.getId() + ":chunks");

            if (chunkIds == null || chunkIds.isEmpty()) {
                continue;
            }

            // 3. 批量更新chunks
            List<DocumentChunk> chunks = new ArrayList<>();
            for (String chunkId : chunkIds) {
                String json = stringRedisTemplate.opsForValue()
                    .get("rag:chunk:" + chunkId);

                if (json != null) {
                    DocumentChunk chunk = objectMapper.readValue(json, DocumentChunk.class);
                    chunk.setKbId(file.getKbId());  // 添加kbId
                    chunks.add(chunk);
                }
            }

            // 4. 重新保存（会自动创建知识库索引）
            redisRepository.saveAll(chunks);

            log.info("已迁移文件 {} 的 {} 个chunks", file.getFilename(), chunks.size());
        }

        log.info("迁移完成！");
    }

    /**
     * 清理旧的用户级索引（可选）
     */
    public void cleanupOldUserIndices() {
        // 删除旧的 rag:user:{userId}:chunks 索引
        Set<String> keys = stringRedisTemplate.keys("rag:user:*:chunks");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
            log.info("已清理 {} 个旧的用户索引", keys.size());
        }
    }
}
```

**执行迁移**：
```java
@SpringBootTest
class MigrationTest {

    @Autowired
    private KnowledgeBaseDataMigrator migrator;

    @Test
    void runMigration() {
        migrator.migrateExistingChunks();
        migrator.cleanupOldUserIndices();
    }
}
```

---

## 🎯 最佳实践

### 1. 知识库命名规范

```
共享知识库：kb_shared_{topic}
私人知识库：kb_private_{userId}
临时知识库：kb_temp_{sessionId}
```

### 2. 权限分配策略

- **共享知识库**：默认所有用户 READER，少数管理员 ADMIN
- **私人知识库**：仅所有者 ADMIN，可授予他人 READER
- **临时知识库**：用完即删

### 3. 性能优化

```java
// 使用缓存减少数据库查询
@Cacheable(value = "user_accessible_kbs", key = "#userId")
public List<KnowledgeBase> getUserAccessibleKnowledgeBases(String userId) {
    return kbMapper.findAccessibleKnowledgeBases(userId);
}

// 定期清理无效知识库
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨2点
public void cleanupInactiveKnowledgeBases() {
    List<KnowledgeBase> inactive = kbMapper.findInactive();
    for (KnowledgeBase kb : inactive) {
        if (kb.getCreatedAt().isBefore(LocalDateTime.now().minusDays(30))) {
            deleteKnowledgeBase(kb.getId(), "system");
        }
    }
}
```

### 4. 监控指标

```java
// 统计知识库使用情况
public Map<String, Object> getKnowledgeBaseStats(String kbId) {
    return Map.of(
        "totalFiles", fileMapper.countByKbId(kbId),
        "totalChunks", getChunkCount(kbId),
        "lastUpdated", getLastUpdatedTime(kbId),
        "activeUsers", getActiveUserCount(kbId)
    );
}

private long getChunkCount(String kbId) {
    return stringRedisTemplate.opsForZSet()
        .zCard("rag:kb:" + kbId + ":chunks");
}
```

---

## 📚 下一步

1. **实现前端界面**
   - 知识库选择器组件
   - 知识库管理页面
   - 权限管理界面

2. **增强检索能力**
   - 实现混合检索（关键词 + 向量）
   - 添加重排序模型
   - 支持过滤条件（日期、文件类型等）

3. **添加高级功能**
   - 知识库导出/导入
   - 知识库版本控制
   - 知识图谱可视化

---

**完整设计文档**: [KNOWLEDGE_BASE_DESIGN.md](./KNOWLEDGE_BASE_DESIGN.md)
**数据库迁移脚本**: [migration_knowledge_bases.sql](./migration_knowledge_bases.sql)