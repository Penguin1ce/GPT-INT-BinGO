# 共享知识库 + 私人知识库设计方案

## 📋 目录
1. [设计概述](#设计概述)
2. [数据库设计](#数据库设计)
3. [Redis 键结构](#redis-键结构)
4. [代码实现](#代码实现)
5. [API 设计](#api-设计)
6. [检索策略](#检索策略)
7. [迁移步骤](#迁移步骤)

---

## 设计概述

### 核心概念

**知识库类型**：
- **SHARED（共享知识库）**：所有用户可读，管理员可管理
  - 示例：C++教程官方文档、常见问题库
  - 访问控制：默认所有用户可读

- **PRIVATE（私人知识库）**：用户专属，仅所有者可访问
  - 示例：用户个人笔记、学习资料
  - 访问控制：仅所有者和被授权用户

### 架构优势

✅ **数据隔离**：不同知识库的向量数据完全隔离
✅ **灵活权限**：支持多级权限控制（ADMIN/WRITER/READER）
✅ **高效检索**：支持跨知识库联合检索或单库检索
✅ **向后兼容**：可平滑迁移现有用户数据

---

## 数据库设计

### 1. 知识库表 (knowledge_bases)

```sql
CREATE TABLE IF NOT EXISTS knowledge_bases (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    type VARCHAR(20) NOT NULL,  -- 'SHARED' 或 'PRIVATE'
    owner_id VARCHAR(64),       -- SHARED为NULL，PRIVATE为用户ID
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_kb_owner FOREIGN KEY (owner_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_kb_type CHECK (type IN ('SHARED', 'PRIVATE')),
    CONSTRAINT chk_kb_owner CHECK (
        (type = 'SHARED' AND owner_id IS NULL) OR
        (type = 'PRIVATE' AND owner_id IS NOT NULL)
    )
);

CREATE INDEX idx_kb_owner ON knowledge_bases(owner_id);
CREATE INDEX idx_kb_type ON knowledge_bases(type);
CREATE INDEX idx_kb_active ON knowledge_bases(is_active);
```

**字段说明**：
- `type = SHARED` 时 `owner_id` 必须为 NULL
- `type = PRIVATE` 时 `owner_id` 必须指向用户
- `is_active` 用于软删除

### 2. 知识库访问权限表 (user_knowledge_base_access)

```sql
CREATE TABLE IF NOT EXISTS user_knowledge_base_access (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    kb_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) DEFAULT 'READER',  -- 'ADMIN', 'WRITER', 'READER'
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(64),  -- 授权者ID

    CONSTRAINT fk_access_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_access_kb FOREIGN KEY (kb_id)
        REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    CONSTRAINT chk_access_role CHECK (role IN ('ADMIN', 'WRITER', 'READER')),
    UNIQUE(user_id, kb_id)
);

CREATE INDEX idx_access_user ON user_knowledge_base_access(user_id);
CREATE INDEX idx_access_kb ON user_knowledge_base_access(kb_id);
```

**权限说明**：
- **ADMIN**：可管理知识库（增删文件、修改权限）
- **WRITER**：可上传文件到知识库
- **READER**：仅可检索知识库内容

**特殊规则**：
- 共享知识库：无权限记录 = 所有用户默认 READER 权限
- 私人知识库：无权限记录 = 无访问权限（除所有者外）

### 3. 修改现有表

```sql
-- uploaded_files 添加知识库关联
ALTER TABLE uploaded_files
    ADD COLUMN kb_id VARCHAR(64);

ALTER TABLE uploaded_files
    ADD CONSTRAINT fk_file_kb
        FOREIGN KEY (kb_id) REFERENCES knowledge_bases(id)
        ON DELETE CASCADE;

CREATE INDEX idx_files_kb ON uploaded_files(kb_id);

-- document_chunks 添加知识库关联（虽然当前未使用此表）
ALTER TABLE document_chunks
    ADD COLUMN kb_id VARCHAR(64);
```

---

## Redis 键结构

### 原有结构（仅支持按用户隔离）
```
rag:user:{userId}:chunks          → ZSet: 用户的所有chunk ID
rag:file:{fileId}:chunks          → Set: 文件的chunk ID集合
rag:chunk:{chunkId}               → String: chunk JSON数据
```

### 新结构（支持知识库隔离）

```
# 1. 知识库维度索引
rag:kb:{kbId}:chunks              → ZSet {chunkId => createdTimestamp}
                                    存储某知识库的所有chunk，按时间排序

# 2. 用户访问缓存
rag:user:{userId}:readable_kbs    → Set {kbId1, kbId2, ...}
                                    用户可读的知识库ID列表（含私人+共享）

# 3. 文件关联
rag:file:{fileId}:chunks          → Set {chunkId1, chunkId2, ...}
                                    (保持不变，用于删除文件时清理)
rag:file:{fileId}:kb              → String {kbId}
                                    文件所属知识库ID

# 4. Chunk数据
rag:chunk:{chunkId}               → JSON {
                                      "id": "xxx",
                                      "userId": "xxx",
                                      "fileId": "xxx",
                                      "kbId": "xxx",        ← 新增字段
                                      "content": "...",
                                      "embeddingJson": "[...]",
                                      "createdAt": "..."
                                    }

# 5. 知识库元数据缓存（可选，TTL=1小时）
rag:kb:{kbId}:meta                → JSON {
                                      "id": "xxx",
                                      "name": "C++教程",
                                      "type": "SHARED",
                                      "ownerId": null
                                    }

# 6. 共享知识库列表缓存（可选，TTL=10分钟）
rag:shared_kbs                    → Set {kbId1, kbId2, ...}
                                    所有活跃的共享知识库ID
```

### 键设计原则

1. **数据隔离**：每个知识库的向量数据完全独立
2. **快速查询**：通过缓存减少数据库查询
3. **批量操作**：使用 Pipeline 优化性能
4. **级联删除**：删除知识库时清理所有关联数据

---

## 代码实现

### 1. 新增 Entity 类

#### KnowledgeBase.java
```java
package com.firefly.ragdemo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeBase {
    private String id;
    private String name;
    private String description;
    private KnowledgeBaseType type;
    private String ownerId;  // PRIVATE类型时有值
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### KnowledgeBaseType.java
```java
package com.firefly.ragdemo.entity;

public enum KnowledgeBaseType {
    SHARED,   // 共享知识库
    PRIVATE   // 私人知识库
}
```

#### UserKnowledgeBaseAccess.java
```java
package com.firefly.ragdemo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserKnowledgeBaseAccess {
    private String id;
    private String userId;
    private String kbId;
    private AccessRole role;
    private LocalDateTime grantedAt;
    private String grantedBy;
}
```

#### AccessRole.java
```java
package com.firefly.ragdemo.entity;

public enum AccessRole {
    ADMIN,   // 管理员
    WRITER,  // 写入者
    READER   // 读取者
}
```

#### 修改 DocumentChunk.java
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunk {
    private String id;
    private String userId;
    private String fileId;
    private String kbId;          // ← 新增字段
    private Integer chunkIndex;
    private String content;
    private String embeddingJson;
    private LocalDateTime createdAt;
}
```

### 2. Repository 层

#### RedisDocumentChunkRepository.java 关键修改

```java
@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisDocumentChunkRepository {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KB_CHUNKS_PREFIX = "rag:kb:";
    private static final String USER_READABLE_KBS_PREFIX = "rag:user:";
    private static final String FILE_CHUNKS_PREFIX = "rag:file:";
    private static final String FILE_KB_PREFIX = "rag:file:";
    private static final String CHUNK_PREFIX = "rag:chunk:";

    /**
     * 批量保存chunks到指定知识库
     */
    public void saveAll(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (DocumentChunk chunk : chunks) {
                if (chunk == null || chunk.getId() == null) continue;

                String serialized = serialize(chunk);
                byte[] chunkKeyBytes = chunkKey(chunk.getId()).getBytes();
                byte[] serializedBytes = serialized.getBytes();

                // 1. 保存chunk内容
                connection.set(chunkKeyBytes, serializedBytes);

                // 2. 添加到知识库的ZSet（新逻辑）
                if (chunk.getKbId() != null) {
                    double score = chunk.getCreatedAt() != null
                        ? chunk.getCreatedAt().toEpochSecond(ZoneOffset.UTC)
                        : System.currentTimeMillis() / 1000.0;
                    byte[] kbChunksKeyBytes = kbChunksKey(chunk.getKbId()).getBytes();
                    byte[] chunkIdBytes = chunk.getId().getBytes();
                    connection.zAdd(kbChunksKeyBytes, score, chunkIdBytes);
                }

                // 3. 添加到文件的Set
                if (chunk.getFileId() != null) {
                    byte[] fileChunksKeyBytes = fileChunksKey(chunk.getFileId()).getBytes();
                    byte[] chunkIdBytes = chunk.getId().getBytes();
                    connection.sAdd(fileChunksKeyBytes, chunkIdBytes);

                    // 记录文件所属知识库
                    if (chunk.getKbId() != null) {
                        byte[] fileKbKeyBytes = fileKbKey(chunk.getFileId()).getBytes();
                        byte[] kbIdBytes = chunk.getKbId().getBytes();
                        connection.set(fileKbKeyBytes, kbIdBytes);
                    }
                }
            }
            return null;
        });

        log.debug("保存{}个chunks到知识库", chunks.size());
    }

    /**
     * 根据知识库ID列表检索chunks
     * @param kbIds 知识库ID列表
     * @param candidateLimit 每个知识库的候选数量限制
     */
    public List<DocumentChunk> findByKnowledgeBases(List<String> kbIds, int candidateLimit) {
        if (kbIds == null || kbIds.isEmpty()) {
            return Collections.emptyList();
        }

        int limit = candidateLimit > 0 ? candidateLimit : 200;
        List<DocumentChunk> allChunks = new ArrayList<>();

        for (String kbId : kbIds) {
            // 获取知识库的chunk IDs
            Set<String> chunkIds = stringRedisTemplate.opsForZSet()
                .reverseRange(kbChunksKey(kbId), 0, limit - 1);

            if (chunkIds == null || chunkIds.isEmpty()) {
                continue;
            }

            // 批量获取chunk内容
            List<Object> results = stringRedisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    for (String chunkId : chunkIds) {
                        connection.get(chunkKey(chunkId).getBytes());
                    }
                    return null;
                }
            );

            // 反序列化
            for (Object result : results) {
                if (result == null) continue;
                DocumentChunk chunk = deserialize(result.toString());
                if (chunk != null && kbId.equals(chunk.getKbId())) {
                    allChunks.add(chunk);
                }
            }
        }

        return allChunks;
    }

    /**
     * 删除知识库的所有chunks
     */
    public void deleteByKnowledgeBase(String kbId) {
        if (kbId == null || kbId.isBlank()) {
            return;
        }

        // 获取知识库的所有chunk IDs
        Set<String> chunkIds = stringRedisTemplate.opsForZSet()
            .range(kbChunksKey(kbId), 0, -1);

        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }

        // 批量删除
        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String chunkId : chunkIds) {
                connection.del(chunkKey(chunkId).getBytes());
            }
            // 删除知识库索引
            connection.del(kbChunksKey(kbId).getBytes());
            return null;
        });

        log.info("已删除知识库{}的{}个chunks", kbId, chunkIds.size());
    }

    /**
     * 删除文件的chunks（需要同时从知识库索引中移除）
     */
    public void deleteByFileIdAndKnowledgeBase(String fileId, String kbId) {
        Set<String> chunkIds = stringRedisTemplate.opsForSet()
            .members(fileChunksKey(fileId));

        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }

        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String chunkId : chunkIds) {
                // 删除chunk内容
                connection.del(chunkKey(chunkId).getBytes());

                // 从知识库ZSet中移除
                if (kbId != null) {
                    connection.zRem(
                        kbChunksKey(kbId).getBytes(),
                        chunkId.getBytes()
                    );
                }
            }
            // 删除文件索引
            connection.del(fileChunksKey(fileId).getBytes());
            connection.del(fileKbKey(fileId).getBytes());
            return null;
        });

        log.info("已删除文件{}的{}个chunks", fileId, chunkIds.size());
    }

    // 键生成方法
    private String kbChunksKey(String kbId) {
        return KB_CHUNKS_PREFIX + kbId + ":chunks";
    }

    private String fileChunksKey(String fileId) {
        return FILE_CHUNKS_PREFIX + fileId + ":chunks";
    }

    private String fileKbKey(String fileId) {
        return FILE_KB_PREFIX + fileId + ":kb";
    }

    private String chunkKey(String chunkId) {
        return CHUNK_PREFIX + chunkId;
    }

    // 序列化方法（省略，与原代码相同）
}
```

#### KnowledgeBaseMapper.java（MyBatis）

```java
package com.firefly.ragdemo.mapper;

import com.firefly.ragdemo.entity.KnowledgeBase;
import com.firefly.ragdemo.entity.UserKnowledgeBaseAccess;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface KnowledgeBaseMapper {

    // 知识库CRUD
    void insertKnowledgeBase(KnowledgeBase kb);
    KnowledgeBase findById(String id);
    List<KnowledgeBase> findByOwnerId(String ownerId);
    List<KnowledgeBase> findAllShared();
    void updateKnowledgeBase(KnowledgeBase kb);
    void deleteById(String id);

    // 权限管理
    void grantAccess(UserKnowledgeBaseAccess access);
    void revokeAccess(@Param("userId") String userId, @Param("kbId") String kbId);
    UserKnowledgeBaseAccess findAccess(@Param("userId") String userId, @Param("kbId") String kbId);
    List<KnowledgeBase> findAccessibleKnowledgeBases(String userId);
}
```

### 3. Service 层

#### KnowledgeBaseService.java

```java
package com.firefly.ragdemo.service;

import com.firefly.ragdemo.entity.KnowledgeBase;
import com.firefly.ragdemo.entity.AccessRole;
import java.util.List;

public interface KnowledgeBaseService {

    // 创建知识库
    KnowledgeBase createSharedKnowledgeBase(String name, String description);
    KnowledgeBase createPrivateKnowledgeBase(String userId, String name, String description);

    // 查询知识库
    List<KnowledgeBase> getUserAccessibleKnowledgeBases(String userId);
    KnowledgeBase getKnowledgeBaseById(String kbId);

    // 权限管理
    void grantAccess(String kbId, String userId, AccessRole role, String grantedBy);
    void revokeAccess(String kbId, String userId);
    boolean checkAccess(String userId, String kbId, AccessRole minimumRole);

    // 删除知识库
    void deleteKnowledgeBase(String kbId, String requestUserId);
}
```

#### RagRetrievalService.java 修改

```java
public interface RagRetrievalService {

    /**
     * 从指定知识库检索上下文
     */
    List<String> retrieveContext(
        List<String> kbIds,  // 支持多知识库联合检索
        String query,
        int topK,
        int candidateLimit
    );

    /**
     * 从用户可访问的所有知识库检索（保持向后兼容）
     */
    List<String> retrieveContextForUser(
        String userId,
        String query,
        int topK,
        int candidateLimit
    );
}
```

### 4. Controller 层

#### KnowledgeBaseController.java

```java
package com.firefly.ragdemo.controller;

import com.firefly.ragdemo.dto.ApiResponse;
import com.firefly.ragdemo.dto.CreateKnowledgeBaseRequest;
import com.firefly.ragdemo.entity.KnowledgeBase;
import com.firefly.ragdemo.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建私人知识库
     */
    @PostMapping("/private")
    public ApiResponse<KnowledgeBase> createPrivateKnowledgeBase(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestBody CreateKnowledgeBaseRequest request
    ) {
        String userId = userDetails.getUsername();
        KnowledgeBase kb = knowledgeBaseService.createPrivateKnowledgeBase(
            userId, request.getName(), request.getDescription()
        );
        return ApiResponse.success(kb);
    }

    /**
     * 创建共享知识库（需要管理员权限）
     */
    @PostMapping("/shared")
    public ApiResponse<KnowledgeBase> createSharedKnowledgeBase(
        @RequestBody CreateKnowledgeBaseRequest request
    ) {
        KnowledgeBase kb = knowledgeBaseService.createSharedKnowledgeBase(
            request.getName(), request.getDescription()
        );
        return ApiResponse.success(kb);
    }

    /**
     * 获取当前用户可访问的知识库列表
     */
    @GetMapping
    public ApiResponse<List<KnowledgeBase>> getAccessibleKnowledgeBases(
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        String userId = userDetails.getUsername();
        List<KnowledgeBase> kbs = knowledgeBaseService
            .getUserAccessibleKnowledgeBases(userId);
        return ApiResponse.success(kbs);
    }

    /**
     * 授予用户访问权限（需要ADMIN权限）
     */
    @PostMapping("/{kbId}/access")
    public ApiResponse<Void> grantAccess(
        @PathVariable String kbId,
        @RequestParam String targetUserId,
        @RequestParam String role,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        String grantedBy = userDetails.getUsername();
        knowledgeBaseService.grantAccess(
            kbId, targetUserId, AccessRole.valueOf(role), grantedBy
        );
        return ApiResponse.success(null);
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/{kbId}")
    public ApiResponse<Void> deleteKnowledgeBase(
        @PathVariable String kbId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        String userId = userDetails.getUsername();
        knowledgeBaseService.deleteKnowledgeBase(kbId, userId);
        return ApiResponse.success(null);
    }
}
```

---

## API 设计

### 新增 REST 接口

```
# 知识库管理
POST   /api/knowledge-bases/private        创建私人知识库
POST   /api/knowledge-bases/shared         创建共享知识库（管理员）
GET    /api/knowledge-bases                获取可访问的知识库列表
GET    /api/knowledge-bases/{kbId}         获取知识库详情
DELETE /api/knowledge-bases/{kbId}         删除知识库

# 权限管理
POST   /api/knowledge-bases/{kbId}/access  授予访问权限
DELETE /api/knowledge-bases/{kbId}/access/{userId}  撤销访问权限

# 文件上传（修改）
POST   /api/files/upload?kbId={kbId}       上传文件到指定知识库

# 聊天（修改）
POST   /api/chat?kbIds=kb1,kb2,kb3          支持指定知识库列表检索
```

### 请求/响应示例

#### 创建私人知识库
```json
POST /api/knowledge-bases/private
{
  "name": "我的学习笔记",
  "description": "个人C++学习资料"
}

Response:
{
  "success": true,
  "data": {
    "id": "kb_xxx",
    "name": "我的学习笔记",
    "type": "PRIVATE",
    "ownerId": "user_123",
    "createdAt": "2025-01-10T10:00:00"
  }
}
```

#### 获取可访问的知识库列表
```json
GET /api/knowledge-bases

Response:
{
  "success": true,
  "data": [
    {
      "id": "kb_shared_1",
      "name": "C++官方教程",
      "type": "SHARED",
      "ownerId": null
    },
    {
      "id": "kb_private_user123",
      "name": "我的学习笔记",
      "type": "PRIVATE",
      "ownerId": "user_123"
    }
  ]
}
```

---

## 检索策略

### 1. 多知识库联合检索

```java
public List<String> retrieveContext(List<String> kbIds, String query, int topK) {
    // 1. 生成查询向量
    List<Double> queryEmbedding = embeddingService.embed(query);

    // 2. 从多个知识库获取候选chunks
    List<DocumentChunk> candidates = redisRepository
        .findByKnowledgeBases(kbIds, topK * 4);

    // 3. 计算相似度并排序
    List<ScoredChunk> scored = candidates.stream()
        .map(chunk -> new ScoredChunk(
            chunk.getContent(),
            cosineSimilarity(queryEmbedding, chunk.getEmbeddingJson())
        ))
        .sorted(Comparator.comparing(ScoredChunk::score).reversed())
        .limit(topK)
        .toList();

    return scored.stream()
        .map(ScoredChunk::content)
        .toList();
}
```

### 2. 检索优先级策略

**方案A：平等检索**
- 从每个知识库取相同数量的候选chunks
- 优点：保证各知识库都有代表
- 适用场景：共享库+私人库联合检索

**方案B：加权检索**
- 私人知识库权重更高（例如2:1）
- 优点：优先使用用户专属数据
- 适用场景：个性化学习助手

**方案C：动态策略**
- 根据查询意图智能选择知识库
- 优点：更精准的结果
- 实现：通过查询分类或用户配置

### 3. 实现示例（平等检索）

```java
public List<DocumentChunk> findByKnowledgeBases(List<String> kbIds, int candidateLimit) {
    int perKbLimit = candidateLimit / kbIds.size();  // 平均分配

    List<DocumentChunk> allChunks = new ArrayList<>();
    for (String kbId : kbIds) {
        Set<String> chunkIds = stringRedisTemplate.opsForZSet()
            .reverseRange(kbChunksKey(kbId), 0, perKbLimit - 1);

        // ... 批量获取chunk内容
        allChunks.addAll(fetchedChunks);
    }
    return allChunks;
}
```

---

## 迁移步骤

### 阶段一：数据库准备

```bash
# 1. 执行数据库迁移SQL（MySQL 8+）
mysql -h 127.0.0.1 -P 3306 -u ragdemo -p ragdemo < migration_knowledge_bases.sql

# 2. 创建默认共享知识库（如需要额外示例数据）
INSERT INTO knowledge_bases (id, name, description, type, owner_id, is_active)
VALUES ('kb_shared_default', 'C++教学官方知识库', '重庆大学C++课程官方资料', 'SHARED', NULL, TRUE);
```

### 阶段二：迁移现有数据

```java
// 为所有现有用户创建默认私人知识库
public void migrateExistingUsers() {
    List<User> users = userMapper.findAll();

    for (User user : users) {
        // 1. 创建私人知识库
        KnowledgeBase kb = KnowledgeBase.builder()
            .id("kb_private_" + user.getId())
            .name(user.getUsername() + "的知识库")
            .type(KnowledgeBaseType.PRIVATE)
            .ownerId(user.getId())
            .isActive(true)
            .build();
        kbMapper.insertKnowledgeBase(kb);

        // 2. 将用户现有文件关联到新知识库
        fileMapper.updateKnowledgeBaseByUserId(user.getId(), kb.getId());

        // 3. 更新Redis中的chunk数据
        List<DocumentChunk> chunks = redisRepository.findByUser(user.getId(), 10000);
        for (DocumentChunk chunk : chunks) {
            chunk.setKbId(kb.getId());
        }
        redisRepository.saveAll(chunks);  // 重新保存以更新kbId
    }
}
```

### 阶段三：代码部署

1. 部署新代码（包含向后兼容逻辑）
2. 验证现有功能正常
3. 逐步切换到新API

### 阶段四：前端适配

1. 在文件上传界面添加知识库选择器
2. 在聊天界面支持知识库筛选
3. 添加知识库管理页面

---

## 总结

### 核心优势

✅ **数据隔离清晰**：共享/私人知识库完全独立
✅ **权限控制灵活**：支持细粒度访问控制
✅ **检索性能高效**：基于Redis ZSet实现O(log N)查询
✅ **扩展性强**：可轻松添加组织级知识库、团队知识库等

### 下一步建议

1. **实现知识库统计**：文件数、chunk数、最后更新时间
2. **添加知识库标签**：支持按学科、课程分类
3. **实现知识库导出**：支持备份和迁移
4. **增强权限系统**：支持角色继承、临时授权等
5. **监控和告警**：知识库使用统计、异常检测

### 技术债务

- 当前 `document_chunks` MySQL 表未使用，可考虑删除
- Redis 缓存过期策略需细化（避免内存泄漏）
- 大规模知识库（>10万chunks）需考虑分片策略
