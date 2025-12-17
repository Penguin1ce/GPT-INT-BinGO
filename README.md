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
- 支持多种AI模型（OpenAI兼容API、Ollama本地模型）
- 流式和非流式响应
- 多轮对话支持
- 令牌使用统计

### 🤖 AI模型架构
- **AIModelFactory**：工厂模式实现AI模型的创建和管理，支持动态注册多种AI模型（OpenAI、Ollama等）
- **AIHelperManager**：单例模式管理用户-会话-AIHelper的映射关系，实现实例缓存和生命周期控制
- **可扩展设计**：工厂使用Map存储创建者函数，确保扩展性和解耦

### 🚀 消息队列与异步处理
- RabbitMQ 异步处理数据库操作（聊天记录持久化、文档分块同步）
- **AI对话直接调用**：用户对话请求不经过消息队列，直接调用AI服务
- **对话后异步持久化**：对话完成后通过消息队列异步保存聊天记录到数据库

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
- RabbitMQ 消息队列：数据库操作异步处理

## 技术栈

- **Spring Boot 3.5.5** - 主框架
- **Spring Security** - 安全认证
- **MyBatis** - 数据库访问
- **MySQL** - 主数据库
- **Redis** - 缓存 + RAG向量存储
- **RabbitMQ** - 消息队列（聊天记录持久化、文档同步）
- **JWT** - 令牌认证
- **Spring AI** - OpenAI集成
- **Lombok** - 代码简化

## 架构设计

### AI模型层

```
┌─────────────────────────────────────────────────────────────┐
│                      ChatController                         │
│  (直接调用ChatService，对话后异步发送记录到消息队列)              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       ChatService                           │
│              (使用AIHelperManager获取AIHelper)               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    AIHelperManager                          │
│  (单例模式，管理用户-会话-AIHelper映射，定时清理过期实例)          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      AIModelFactory                         │
│  (工厂模式，Map存储创建者函数，支持动态注册模型类型)                │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │ OpenAICompatible│  │   OllamaModel   │  │  自定义模型   │  │
│  │      Model      │  │                 │  │             │  │
│  └─────────────────┘  └─────────────────┘  └─────────────┘  │
└─────────────────────────────────────────────────────────────┘
```


### 消息队列用途

| 队列                | 用途           | 说明                           |
| ------------------- | -------------- | ------------------------------ |
| chat.session.queue  | 聊天记录持久化 | 对话完成后异步保存到MySQL      |
| kb.chunk.sync.queue | 文档分块同步   | 将Redis中的文档分块同步到MySQL |

**注意**：AI对话请求直接调用服务，不经过消息队列，确保低延迟响应。

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

# 初始化表结构与默认知识库
mysql -h 127.0.0.1 -P 3306 -u ragdemo -p ragdemo < src/main/resources/mapper/schema.sql
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
无需启动 Web 服务，使用 CLI 将目录下所有文件上传到公共知识库：

**方式一：使用脚本（推荐）**
```bash
# 使用默认参数（用户admin，知识库kb_shared_cpp_tutorial）
./bulk-upload.sh /path/to/public_files

# 自定义参数
./bulk-upload.sh /path/to/public_files admin kb_custom 300 true
```

**方式二：直接使用Maven命令**
```bash
mvn -q -DskipTests clean compile exec:java \
  -Dexec.mainClass=com.firefly.ragdemo.tool.BulkPublicKbUploader \
  -Dspring.profiles.active=dev \
  -Dexec.args="dir=/ABS/PATH/TO/public_files user=admin kb=kb_shared_cpp_tutorial waitSeconds=180 retryFailedOnce=true"
```

参数说明：
- `dir`：要导入的目录绝对路径（必填）
- `user`：已有用户名（默认：admin）
- `kb`：目标知识库ID（默认：kb_shared_cpp_tutorial）
- `waitSeconds`：轮询索引完成的超时时间（秒，默认：180）
- `retryFailedOnce`：失败时自动重试一次索引（默认：true）

## RAG 知识库存储

- 文档分块与向量全部写入Redis，由 `RedisDocumentChunkRepository` 维护；
- Key 约定：
  - `rag:user:{userId}:chunks`：ZSet，按创建时间降序维护用户可见的chunk id；
  - `rag:kb:{kbId}:chunks`：ZSet，按知识库维护chunk id；
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
- `GET /chat/sessions` - 获取会话列表
- `GET /chat/sessions/{sessionId}/messages` - 获取会话历史

### 文件相关
- `POST /upload` - 文件上传
- `GET /files` - 获取文件列表
- `DELETE /files/{fileId}` - 删除文件

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
├── ai/              # AI模型层
│   ├── AIModel.java           # AI模型接口
│   ├── AIModelConfig.java     # 模型配置
│   ├── AIModelFactory.java    # 工厂类
│   ├── AIHelper.java          # AI助手
│   ├── AIHelperManager.java   # 助手管理器
│   └── impl/
│       ├── OpenAICompatibleModel.java  # OpenAI兼容实现
│       └── OllamaModel.java            # Ollama实现
├── config/          # 配置类
├── controller/      # 控制器
├── DTO/            # 请求数据传输对象
├── entity/         # 实体类
├── exception/      # 异常处理
├── mapper/         # MyBatis接口
├── messaging/      # 消息队列
│   ├── ChatHistoryQueueProducer.java   # 聊天记录生产者
│   ├── ChatHistoryQueueListener.java   # 聊天记录消费者
│   ├── DocumentChunkSyncProducer.java  # 文档同步生产者
│   └── DocumentChunkSyncListener.java  # 文档同步消费者
├── repository/     # Redis向量存储
├── secutiry/       # 安全相关
├── service/        # 业务服务
├── tool/           # 命令行工具
├── util/           # 工具类
├── VO/             # 响应数据传输对象
└── RaGdemoApplication.java

logs/               # 日志文件目录
├── app.log         # 应用运行日志
└── error.log       # 错误日志
```

## 数据库表结构

### users - 用户表
- id (VARCHAR(64), PK)
- username (VARCHAR(50), UNIQUE, NOT NULL)
- email (VARCHAR(100), UNIQUE, NOT NULL)
- password_hash (VARCHAR(255), NOT NULL)
- created_at (TIMESTAMP, 默认当前时间)
- updated_at (TIMESTAMP, 自动更新)
- is_active (TINYINT(1), 默认1)
- last_login (TIMESTAMP, 可为空)

### refresh_tokens - 刷新令牌表
- id (VARCHAR(64), PK)
- user_id (VARCHAR(64), FK → users.id, CASCADE删除)
- token (VARCHAR(500), NOT NULL)
- expires_at (TIMESTAMP, NOT NULL)
- created_at (TIMESTAMP, 默认当前时间)
- is_revoked (TINYINT(1), 默认0)
- 索引：idx_refresh_tokens_user (user_id)

### knowledge_bases - 知识库表
- id (VARCHAR(64), PK)
- name (VARCHAR(100), NOT NULL)
- description (TEXT)
- type (VARCHAR(20), NOT NULL, CHECK: 'SHARED' | 'PRIVATE')
- owner_id (VARCHAR(64), FK → users.id, CASCADE删除)
- is_active (TINYINT(1), 默认1)
- created_at (TIMESTAMP, 默认当前时间)
- updated_at (TIMESTAMP, 自动更新)
- 约束：SHARED类型owner_id必须为NULL，PRIVATE类型owner_id必须非NULL
- 索引：idx_kb_owner, idx_kb_type, idx_kb_active, idx_kb_created

### user_knowledge_base_access - 用户知识库访问权限表
- id (VARCHAR(64), PK)
- user_id (VARCHAR(64), FK → users.id, CASCADE删除)
- kb_id (VARCHAR(64), FK → knowledge_bases.id, CASCADE删除)
- role (VARCHAR(20), 默认'READER', CHECK: 'ADMIN' | 'WRITER' | 'READER')
- granted_at (TIMESTAMP, 默认当前时间)
- granted_by (VARCHAR(64))
- 唯一约束：uk_user_kb (user_id, kb_id)
- 索引：idx_access_user, idx_access_kb, idx_access_granted

### uploaded_files - 上传文件表
- id (VARCHAR(64), PK)
- user_id (VARCHAR(64), FK → users.id, CASCADE删除)
- filename (TEXT, NOT NULL)
- file_path (TEXT, NOT NULL)
- file_size (BIGINT)
- file_type (VARCHAR(32))
- upload_time (TIMESTAMP, 默认当前时间)
- status (VARCHAR(32), 默认'PROCESSING', CHECK: 'PROCESSING' | 'COMPLETED' | 'FAILED')
- kb_id (VARCHAR(64), FK → knowledge_bases.id, CASCADE删除)
- 索引：idx_uploaded_files_user, idx_uploaded_files_kb

### document_chunks - 文档分块表
- id (VARCHAR(64), PK)
- user_id (VARCHAR(64), FK → users.id, CASCADE删除)
- file_id (VARCHAR(64), FK → uploaded_files.id, CASCADE删除)
- kb_id (VARCHAR(64), FK → knowledge_bases.id, CASCADE删除)
- chunk_index (INT, NOT NULL)
- content (TEXT)
- embedding (JSON)
- created_at (TIMESTAMP, 默认当前时间)
- 索引：idx_document_chunks_user, idx_document_chunks_file, idx_document_chunks_kb
- 说明：主要向量数据存储在Redis，此表用于消息队列异步同步备份

### chat_sessions - 聊天会话表
- id (VARCHAR(64), PK)
- user_id (VARCHAR(64), FK → users.id, CASCADE删除)
- title (VARCHAR(255))
- first_message (TEXT)
- model (VARCHAR(100))
- message_count (INT, 默认0)
- last_message_at (TIMESTAMP, 可为空)
- created_at (TIMESTAMP, 默认当前时间)
- updated_at (TIMESTAMP, 自动更新)
- 索引：idx_chat_session_user, idx_chat_session_last

### chat_messages - 聊天消息表
- id (VARCHAR(64), PK)
- session_id (VARCHAR(64), FK → chat_sessions.id, CASCADE删除)
- user_id (VARCHAR(64), FK → users.id, CASCADE删除)
- role (VARCHAR(20), NOT NULL, 'user' | 'assistant' | 'system')
- content (MEDIUMTEXT)
- seq (INT, NOT NULL, 消息序号)
- model (VARCHAR(100))
- created_at (TIMESTAMP, 默认当前时间)
- 索引：idx_chat_msg_session (session_id, seq), idx_chat_msg_user

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
8. [x] AI模型工厂模式
9. [x] 会话管理器单例模式

## 许可证

GPL v3

## 环境变量

在运行前，请通过环境变量提供敏感配置（不要把密钥写入仓库）：

- `OPENAI_API_KEY`：OpenAI API密钥（必填）
- `OPENAI_BASE_URL`：OpenAI Base URL（可选，默认 https://api.bltcy.ai）
- `APP_JWT_SECRET`：JWT签名密钥（建议设置为强随机串）

## 扩展AI模型

### 注册新的模型类型

```java
// 在AIModelFactory中注册自定义模型
aiModelFactory.register("custom", config -> new CustomAIModel(config));
```

### 使用Ollama本地模型

1. 安装并启动Ollama服务
2. 配置模型：
```yaml
# application-dev.yaml
app:
  ai:
    ollama:
      base-url: http://localhost:11434
      model: llama3.2
```

## TODO LIST

- [ ] 将向量数据库迁移到Redis Vector Library (RedisVL)
- [x] 添加数据库的session列表查询
- [x] 当前数据库的Document表和Redis同步删除
- [x] AI模型工厂模式重构
- [x] 消息队列简化（对话直接调用，仅数据库操作异步）
