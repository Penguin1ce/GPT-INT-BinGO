# RAG Demo - GPT-INT 后端API

基于Spring Boot 3.5实现的RAG (Retrieval-Augmented Generation) 系统后端API，严格按照GPT-INT后端API接口规范实现。

## 功能特性

### 🔐 认证系统
- 用户注册与登录
- JWT令牌认证
- 刷新令牌机制
- 用户登出
- 用户信息获取

### 💬 GPT对话
- 支持GPT-4o等多种模型
- 流式和非流式响应
- 多轮对话支持
- 令牌使用统计

### 📁 文件管理
- 文件上传（支持txt、md、pdf、docx）
- 文件列表查询
- 分页支持
- 文件大小限制（10MB）

### 🔧 技术特性
- 统一响应格式
- 全局异常处理
- 参数验证
- 数据库事务管理
- 安全配置
- RAG向量知识库：基于Redis分用户存储

## 技术栈

- **Spring Boot 3.5.5** - 主框架
- **Spring Security** - 安全认证
- **Spring Data JPA** - 数据库操作
- **PostgreSQL** - 主数据库
- **Redis** - 缓存 + RAG向量存储
- **JWT** - 令牌认证
- **Spring AI** - OpenAI集成
- **Lombok** - 代码简化

## 快速开始

### 环境要求
- Java 21
- Maven 3.6+
- PostgreSQL 12+
- Redis 6.0+

### 数据库准备
```bash
# 创建PostgreSQL数据库
createdb ragdemo
```

### 配置文件
编辑 `src/main/resources/application.yaml`，修改以下配置：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ragdemo
    username: your-db-username
    password: your-db-password
  
  data:
    redis:
      host: localhost
      port: 6379
  
  ai:
    openai:
      api-key: your-openai-api-key
      base-url: https://api.openai.com

app:
  jwt:
    secret: your-secret-key
```

### 运行项目
```bash
# 编译项目
mvn clean compile

# 运行项目
mvn spring-boot:run
```

项目将在 `http://localhost:8000` 启动。

## RAG 知识库存储

- 文档分块与向量全部写入Redis，由 `RedisDocumentChunkRepository` 维护；
- Key 约定：
  - `rag:user:{userId}:chunks`：ZSet，按创建时间降序维护用户可见的chunk id；
  - `rag:file:{fileId}:chunks`：Set，记录某个文件关联的chunk id，支持按文档级别清理；
  - `rag:chunk:{chunkId}`：Value，存放`DocumentChunk`序列化后的JSON（含文本、embedding等）。
- 检索时会从对应用户的ZSet中抽取候选片段，落盘到内存后计算余弦相似度，确保不同用户之间知识隔离；
- 删除某个文档会同时清空其文件集合、chunk实体以及用户索引，保证Redis里不残留旧向量。

## API接口

### 认证相关
- `POST /auth/register` - 用户注册
- `POST /auth/login` - 用户登录
- `POST /auth/refresh` - 刷新令牌
- `POST /auth/logout` - 用户登出
- `GET /auth/profile` - 获取用户信息

### 对话相关
- `POST /ask` - GPT对话（支持流式响应）

### 文件相关
- `POST /upload` - 文件上传
- `GET /files` - 获取文件列表

## 统一响应格式

所有API响应都遵循以下格式：

```json
{
  "success": true,
  "message": "操作成功",
  "data": {},
  "code": 200
}
```

错误响应：
```json
{
  "success": false,
  "message": "错误信息",
  "code": 400,
  "errors": [
    {
      "field": "字段名",
      "message": "错误详情"
    }
  ]
}
```

## 项目结构

```
src/main/java/com/firefly/ragdemo/
├── config/          # 配置类
├── controller/      # 控制器
├── DTO/            # 请求数据传输对象
├── entity/         # 实体类
├── exception/      # 异常处理
├── mapper/         # MyBatis接口
├── repository/     # Redis向量存储
├── secutiry/       # 安全相关
├── service/        # 业务服务
├── VO/             # 响应数据传输对象
└── RaGdemoApplication.java
```

## 数据库表结构

### users - 用户表
- id (VARCHAR(36), PK)
- username (VARCHAR(50), UNIQUE)
- email (VARCHAR(100), UNIQUE)
- password_hash (VARCHAR(255))
- created_at (TIMESTAMP)
- updated_at (TIMESTAMP)
- is_active (BOOLEAN)
- last_login (TIMESTAMP)

### refresh_tokens - 刷新令牌表
- id (VARCHAR(36), PK)
- user_id (VARCHAR(36), FK)
- token (VARCHAR(500))
- expires_at (TIMESTAMP)
- created_at (TIMESTAMP)
- is_revoked (BOOLEAN)

### uploaded_files - 上传文件表
- id (VARCHAR(36), PK)
- user_id (VARCHAR(36), FK)
- filename (VARCHAR(255))
- file_path (VARCHAR(500))
- file_size (BIGINT)
- file_type (VARCHAR(50))
- upload_time (TIMESTAMP)
- status (ENUM: 'PROCESSING', 'COMPLETED', 'FAILED')

## 安全特性

- 密码使用BCrypt加密
- JWT令牌认证
- CORS配置
- 请求参数验证
- 文件类型和大小限制
- SQL注入防护

## 开发说明

本项目严格按照 `GPT-INT后端API接口规范.md` 实现，包含：

1. [x] 完整的用户认证系统
2. [x] JWT令牌机制
3. [x] GPT对话接口（流式/非流式）
4. [x] 文件上传管理
5. [x] 统一错误处理
6. [x] 参数验证
7. [x] 数据库设计

## 许可证

GPL v3

## 环境变量

在运行前，请通过环境变量提供敏感配置（不要把密钥写入仓库）：

- `OPENAI_API_KEY`：OpenAI API密钥（必填）
- `OPENAI_BASE_URL`：OpenAI Base URL（可选，默认 https://api.csun.site）
- `APP_JWT_SECRET`：JWT签名密钥（建议设置为强随机串）

# TODO 列表

## RAG 功能实现
- [ ] 文档入库流程设计
  - [ ] 文件解析器：txt、md、pdf、docx
  - [ ] 元数据抽取（文件名、大小、MIME、作者、时间等）
  - [ ] 入库状态机：PENDING -> PROCESSING -> COMPLETED/FAILED
- [ ] 文本切分（Chunking）
  - [ ] 规则：按段落/标点/长度（如 500-1000 tokens）
  - [ ] 语言/格式感知：Markdown/代码块保留
- [ ] 向量化（Embeddings）
  - [ ] 集成 OpenAI/可替代模型
  - [ ] 批量向量化与重试/限速
- [ ] 向量库（Vector Store）
  - [ ] 选择 pgvector（或替代方案）
  - [ ] 数据表/索引设计（文档、块、向量、元数据）
  - [ ] 迁移脚本与本地docker支持
- [ ] 检索（Retrieval）
  - [ ] Top-k 召回（按相似度阈值）
  - [ ] 过滤器：按用户/文档/标签
  - [ ] （可选）重排/多路召回
- [ ] 生成（Generation）
  - [ ] 上下文注入（含去重/截断）
  - [x] 系统提示词：重庆大学大数据与软件学院 C++ 助教
  - [x] 输出结构化响应（含使用统计）
- [ ] 流式输出与来源引用
  - [x] SSE 流式增量输出
  - [ ] 返回引用片段与来源文档信息

## 文件管理
- [x] 文件上传 API
  - [x] 大小/类型校验（10MB，白名单）
  - [x] 存储路径与命名策略（去重/分片可选）
  - [ ] 触发解析->切分->向量化->入库异步任务
- [ ] 文件删除 API
  - [ ] 权限校验（仅文件所有者）
  - [ ] 级联清理：块与向量、元数据
  - [ ] 幂等与审计日志

## 知识库管理
- [x] 按文档删除
  - [ ] 软删/硬删策略
  - [ ] 清理对应的向量与元数据
- [ ] 知识库删除（按空间/用户范围）
  - [ ] 批量删除任务与进度回传
  - [ ] 资源回收与配额统计

## 基础设施与运维
- [ ] 异步任务处理（解析/嵌入/入库）
  - [ ] 队列/线程池与失败重试
  - [ ] 任务状态查询接口
- [x] 配置与密钥管理
  - [x] 使用环境变量（不提交密钥）
  - [x] README 增补运行说明
- [ ] 监控与可观测性（可选）
  - [ ] 健康检查/指标/日志字段化

## 验收标准（摘选）
- [ ] 上传文档后，可在向量库检索到对应上下文
- [ ] 聊天时基于检索结果进行答案生成并可流式返回
- [ ] 可删除指定文档与对应向量
- [ ] 可删除知识库并清理全部关联数据 
