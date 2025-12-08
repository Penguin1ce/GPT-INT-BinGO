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

### 🚀 消息队列与异步处理
- RabbitMQ 解耦非流式 AI 请求，避免主线程阻塞
- `ChatMessageQueueService` 将对话任务写入队列，后台监听器批量消费
- 支持并发调度与削峰填谷，提升整体吞吐量

### 📁 文件管理
- 文件上传（支持txt、md、pdf、docx）
- 文件列表查询
- 分页支持
- 文件大小限制（10MB）

### 📚 知识库检索
- 公共知识库（共享）+ 私人知识库（用户独享）+ 授权知识库联合检索
- 文件上传可指定目标知识库（默认落个人私人库）
- Redis 分库索引，支持按知识库ID召回与删除
- 提供命令行批量导入工具，将目录文件批量写入公共库

### 🔧 技术特性
- 统一响应格式
- 全局异常处理
- 参数验证
- 数据库事务管理
- 安全配置
- RAG向量知识库：基于Redis分用户存储
- RabbitMQ 消息队列：AI 请求异步写入/消费，提升吞吐

## 技术栈

- **Spring Boot 3.5.5** - 主框架
- **Spring Security** - 安全认证
- **MyBatis** - 数据库访问
- **MySQL** - 主数据库
- **Redis** - 缓存 + RAG向量存储
- **RabbitMQ** - 消息队列（解耦 + 削峰 + 异步处理）
- **JWT** - 令牌认证
- **Spring AI** - OpenAI集成
- **Lombok** - 代码简化

## 快速开始

### 环境要求
- Java 21
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

### 数据库准备
```bash
# 创建MySQL数据库与账号
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ragdemo DEFAULT CHARACTER SET utf8mb4; \
  CREATE USER IF NOT EXISTS 'ragdemo'@'%' IDENTIFIED BY 'password'; \
  GRANT ALL PRIVILEGES ON ragdemo.* TO 'ragdemo'@'%'; \
  FLUSH PRIVILEGES;"
```

### 配置文件
编辑 `src/main/resources/application.yaml`，修改以下配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ragdemo?useUnicode=true&characterEncoding=utf-8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false
    username: ragdemo
    password: password
  
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

### 批量导入公共知识库（命令行工具）
无需启动 Web 服务，使用 CLI 将目录下所有文件上传到公共知识库 `kb_shared_cpp_tutorial`：
```bash
mvn -q -DskipTests \
  -Dexec.mainClass=com.firefly.ragdemo.tool.BulkPublicKbUploader \
  -Dspring.profiles.active=dev \
  -Dspring.rabbitmq.listener.simple.auto-startup=false \
  -Dspring.rabbitmq.listener.direct.auto-startup=false \
  -Dspring.datasource.url="jdbc:mysql://127.0.0.1:3306/ragdemo?useUnicode=true&characterEncoding=utf-8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false" \
  -Dspring.datasource.username=ragdemo \
  -Dspring.datasource.password=password \
  exec:java \
  -Dexec.args="dir=/ABS/PATH/TO/public_files user=admin kb=kb_shared_cpp_tutorial waitSeconds=180 retryFailedOnce=true"
```
说明：
- `dir`：要导入的目录绝对路径；`user`：已有用户；`kb` 默认公共库，可按需更换。
- `waitSeconds`：轮询索引完成的超时时间（秒）；`retryFailedOnce`：失败时自动重试一次索引。

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

logs/               # 日志文件目录
├── app.log         # 应用运行日志
└── error.log       # 错误日志
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
- `OPENAI_BASE_URL`：OpenAI Base URL（可选，默认 https://api.bltcy.ai）
- `APP_JWT_SECRET`：JWT签名密钥（建议设置为强随机串）

# TODO 列表

## RAG 功能实现
- [x] 文档入库流程设计
  - [x] 文件解析器：txt、md、pdf、docx（基于Apache Tika 2.9.2）
  - [x] 元数据抽取（文件名、大小、MIME、时间等）
  - [x] 入库状态机：PROCESSING -> COMPLETED/FAILED
- [x] 文本切分（Chunking）
  - [x] 规则：按段落/长度切分（800字符，重叠100字符）
  - [ ] 语言/格式感知：Markdown/代码块保留（当前使用简单段落切分）
- [x] 向量化（Embeddings）
  - [x] 集成 OpenAI text-embedding-3-large
  - [x] 批量向量化（EmbeddingService支持批量处理）
  - [ ] 完善重试/限速机制
- [x] 向量库（Vector Store）
  - [x] 采用Redis作为向量存储（替代pgvector方案）
  - [x] 数据结构设计（user chunks ZSet、file chunks Set、chunk详情String）
  - [x] Docker支持（docker-compose.yml）
- [x] 检索（Retrieval）
  - [x] Top-k 召回（余弦相似度，可配置阈值与数量）
  - [x] 过滤器：按用户隔离（确保数据隔离）
  - [ ] （可选）重排/多路召回
- [x] 生成（Generation）
  - [x] 上下文注入（检索结果拼接，取最近20轮对话）
  - [x] 系统提示词：重庆大学大数据与软件学院 C++ 助教
  - [x] 输出结构化响应（含token使用统计）
- [x] 流式输出与来源引用
  - [x] SSE 流式增量输出（支持/ask接口stream参数）
  - [ ] 返回引用片段与来源文档信息（当前仅在prompt中包含检索片段）

## 文件管理
- [x] 文件上传 API
  - [x] 大小/类型校验（10MB，白名单：txt/md/pdf/docx）
  - [x] 存储路径与命名策略（UUID重命名）
  - [x] 触发解析->切分->向量化->入库异步任务（使用@Async + 事务后提交触发）
  - [x] SSE实时通知文件处理状态（/files/processing-stream）
- [x] 文件删除 API
  - [x] 权限校验（仅文件所有者可删除）
  - [x] 级联清理：Redis向量、磁盘文件、数据库记录
  - [ ] 审计日志（当前仅有基础日志）

## 知识库管理
- [x] 按文档删除（DELETE /files/{fileId}）
  - [x] 硬删策略（直接删除Redis和数据库记录）
  - [x] 清理对应的向量与元数据
- [ ] 知识库删除（按空间/用户范围）
  - [ ] 批量删除任务与进度回传
  - [ ] 资源回收与配额统计

## 基础设施与运维
- [x] 异步任务处理（解析/嵌入/入库）
  - [x] 使用Spring @Async线程池（ragIndexExecutor配置）
  - [x] 任务状态实时通知（SSE推送PROCESSING/COMPLETED/FAILED状态）
  - [ ] 失败重试机制完善（当前仅有基础异常处理）
- [x] 配置与密钥管理
  - [x] 使用环境变量（OPENAI_API_KEY、APP_JWT_SECRET等）
  - [x] README 增补运行说明
- [ ] 监控与可观测性
  - [x] 结构化日志（Slf4j + Lombok）
  - [ ] 健康检查端点
  - [ ] 业务指标监控（Prometheus/Grafana）

## 验收标准
- [x] 上传文档后，可在向量库检索到对应上下文（已实现Redis存储+检索）
- [x] 聊天时基于检索结果进行答案生成并可流式返回（已实现RAG+SSE流式）
- [x] 可删除指定文档与对应向量（DELETE /files/{fileId}已实现）
- [ ] 可删除知识库并清理全部关联数据（单文档删除已支持，批量知识库删除待实现）

## 待优化项
- [ ] 文本切分策略优化：支持Markdown/代码块感知切分
- [ ] OpenAI API调用增加重试和限流机制
- [ ] 异步任务失败自动重试（当前失败后需手动重新上传）
- [ ] 单元测试覆盖率提升（当前测试用例较少）
- [ ] 生产环境部署文档（Docker、K8s等）
- [ ] 聊天响应中返回引用来源文档信息（便于用户溯源） 

## TODO LIST

- [ ] 将向量数据库迁移到Redis Vector Library (RedisVL)
- [ ] 添加数据库的session列表查询
- [ ] 当前数据库的Document表不会和Redis同步
