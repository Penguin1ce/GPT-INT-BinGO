# RAG Demo 后端API规格文档

## 基础信息

**基础URL**: `http://localhost:8000`  
**协议**: HTTP/HTTPS  
**数据格式**: JSON  
**字符编码**: UTF-8

## 认证机制

大部分API需要JWT令牌认证，在请求头中添加：
```
Authorization: Bearer <access_token>
```

## 统一响应格式

### 成功响应
```json
{
  "success": true,
  "message": "操作成功",
  "data": {},
  "code": 200
}
```

### 错误响应
```json
{
  "success": false,
  "message": "错误信息",
  "code": 400,
  "errors": [
    {
      "field": "字段名",
      "message": "具体错误信息"
    }
  ]
}
```

## HTTP状态码说明

| 状态码 | 含义       | 使用场景          |
| ------ | ---------- | ----------------- |
| 200    | 成功       | 正常请求成功      |
| 201    | 创建成功   | 用户注册成功      |
| 400    | 请求错误   | 参数验证失败      |
| 401    | 未认证     | Token无效或过期   |
| 403    | 权限不足   | 用户被禁用        |
| 404    | 资源不存在 | 用户不存在        |
| 409    | 冲突       | 用户名/邮箱已存在 |
| 500    | 服务器错误 | 内部错误          |

---

# API接口详细说明

## 1. 认证相关接口

### 1.1 用户注册

**接口地址**: `POST /auth/register`  
**需要认证**: ❌

**请求参数**:
```json
{
  "username": "testuser",
  "email": "test@example.com", 
  "password": "password123"
}
```

**参数验证规则**:
- `username`: 必填，3-20个字符，只允许字母、数字、下划线
- `email`: 必填，有效邮箱格式
- `password`: 必填，6-20个字符

**成功响应** (201):
```json
{
  "success": true,
  "message": "注册成功",
  "data": {
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "username": "testuser",
      "email": "test@example.com",
      "createdAt": "2024-01-01T00:00:00"
    }
  },
  "code": 201
}
```

**错误响应**:
```json
{
  "success": false,
  "message": "用户名已存在",
  "code": 409
}
```

---

### 1.2 用户登录

**接口地址**: `POST /auth/login`  
**需要认证**: ❌

**请求参数**:
```json
{
  "username": "testuser",
  "password": "password123"
}
```

**参数验证规则**:
- `username`: 必填，用户名
- `password`: 必填，密码

**成功响应** (200):
```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "username": "testuser",
      "email": "test@example.com",
      "createdAt": "2024-01-01T00:00:00",
      "lastLogin": "2024-01-02T10:30:00"
    }
  },
  "code": 200
}
```

**JWT Token载荷**:
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "testuser",
  "iat": 1640995200,
  "exp": 1641081600
}
```

---

### 1.3 刷新令牌

**接口地址**: `POST /auth/refresh`  
**需要认证**: ❌

**请求参数**:
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**参数验证规则**:
- `refreshToken`: 必填，有效的刷新令牌

**成功响应** (200):
```json
{
  "success": true,
  "message": "Token刷新成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  },
  "code": 200
}
```

---

### 1.4 用户登出

**接口地址**: `POST /auth/logout`  
**需要认证**: ✅

**请求头**:
```
Authorization: Bearer <access_token>
```

**请求参数** (可选):
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**成功响应** (200):
```json
{
  "success": true,
  "message": "登出成功",
  "code": 200
}
```

---

### 1.5 获取用户信息

**接口地址**: `GET /auth/profile`  
**需要认证**: ✅

**请求头**:
```
Authorization: Bearer <access_token>
```

**成功响应** (200):
```json
{
  "success": true,
  "message": "获取用户信息成功",
  "data": {
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "username": "testuser",
      "email": "test@example.com",
      "createdAt": "2024-01-01T00:00:00",
      "lastLogin": "2024-01-02T10:30:00"
    }
  },
  "code": 200
}
```

---

## 2. GPT对话接口

### 2.1 AI对话

**接口地址**: `POST /ask`  
**需要认证**: ✅

**请求头**:
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求参数**:
```json
{
  "model": "gpt-4o",
  "messages": [
    {
      "role": "user",
      "content": "请解释什么是人工智能"
    },
    {
      "role": "assistant",
      "content": "人工智能(AI)是计算机科学的一个分支..."
    },
    {
      "role": "user", 
      "content": "AI的应用场景有哪些？"
    }
  ],
  "stream": false,
  "langid": "zh"
}
```

**参数说明**:
- `model`: 必填，AI模型名称（如：gpt-4o, gpt-3.5-turbo）
- `messages`: 必填，消息历史数组
  - `role`: 消息角色（"user"用户, "assistant"助手）
  - `content`: 消息内容
- `stream`: 可选，是否流式响应（默认false）
- `langid`: 可选，语言标识

**非流式响应** (200):
```json
{
  "success": true,
  "message": "对话完成",
  "data": {
    "response": "AI的应用场景非常广泛，包括：\n1. 自然语言处理...",
    "usage": {
      "promptTokens": 25,
      "completionTokens": 150,
      "totalTokens": 175
    }
  },
  "code": 200
}
```

**流式响应** (stream=true):
```
Content-Type: text/plain
Cache-Control: no-cache
Connection: keep-alive

data: {"message": {"content": "AI的"}}

data: {"message": {"content": "应用"}}

data: {"message": {"content": "场景"}}

...
```

---

## 3. 文件管理接口

### 3.1 文件上传

**接口地址**: `POST /upload`  
**需要认证**: ✅

**请求头**:
```
Authorization: Bearer <access_token>
Content-Type: multipart/form-data
```

**请求参数**:
```
file: <二进制文件数据>
```

**支持的文件类型**:
- `.txt` - 纯文本文件
- `.md` - Markdown文件
- `.pdf` - PDF文档  
- `.docx` - Word文档

**文件限制**:
- 最大文件大小: 10MB
- 单次只能上传一个文件

**成功响应** (200):
```json
{
  "success": true,
  "message": "文件上传成功，开始处理",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "filename": "document.pdf",
    "fileSize": 1024000,
    "fileType": "pdf", 
    "uploadTime": "2024-01-02T10:30:00",
    "status": "PROCESSING"
  },
  "code": 200
}
```

**文件状态说明**:
- `PROCESSING`: 文件处理中
- `COMPLETED`: 处理完成
- `FAILED`: 处理失败

---

### 3.2 获取文件列表

**接口地址**: `GET /files`  
**需要认证**: ✅

**请求头**:
```
Authorization: Bearer <access_token>
```

**查询参数**:
- `page`: 页码，默认1
- `limit`: 每页数量，默认10

**示例请求**:
```
GET /files?page=1&limit=10
```

**成功响应** (200):
```json
{
  "success": true,
  "message": "获取文件列表成功",
  "data": {
    "files": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440001",
        "filename": "document.pdf",
        "fileSize": 1024000,
        "fileType": "pdf",
        "uploadTime": "2024-01-02T10:30:00",
        "status": "COMPLETED"
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 10,
      "total": 1,
      "totalPages": 1
    }
  },
  "code": 200
}
```

---

## 4. 错误处理

### 4.1 参数验证错误

**HTTP状态码**: 400

```json
{
  "success": false,
  "message": "参数验证失败",
  "code": 400,
  "errors": [
    {
      "field": "username",
      "message": "用户名长度必须在3-20个字符之间"
    },
    {
      "field": "email", 
      "message": "邮箱格式不正确"
    }
  ]
}
```

### 4.2 认证错误

**HTTP状态码**: 401

```json
{
  "success": false,
  "message": "Token已过期，请重新登录",
  "code": 401
}
```

### 4.3 权限错误

**HTTP状态码**: 403

```json
{
  "success": false,
  "message": "账户已被禁用",
  "code": 403
}
```

### 4.4 资源冲突

**HTTP状态码**: 409

```json
{
  "success": false,
  "message": "用户名已存在",
  "code": 409
}
```

### 4.5 服务器错误

**HTTP状态码**: 500

```json
{
  "success": false,
  "message": "服务器内部错误",
  "code": 500
}
```

---

## 5. 使用示例

### 5.1 用户注册和登录流程

```bash
# 1. 用户注册
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123"
  }'

# 2. 用户登录
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser", 
    "password": "password123"
  }'

# 3. 使用获得的token进行API调用
curl -X GET http://localhost:8000/auth/profile \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### 5.2 AI对话示例

```bash
# 普通对话
curl -X POST http://localhost:8000/ask \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {
        "role": "user",
        "content": "你好，请介绍一下你自己"
      }
    ],
    "stream": false
  }'

# 流式对话
curl -X POST http://localhost:8000/ask \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o", 
    "messages": [
      {
        "role": "user",
        "content": "写一首关于春天的诗"
      }
    ],
    "stream": true
  }'
```

### 5.3 文件上传示例

```bash
# 上传文件
curl -X POST http://localhost:8000/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@document.pdf"

# 获取文件列表
curl -X GET http://localhost:8000/files?page=1&limit=10 \
  -H "Authorization: Bearer <token>"
```

---

## 6. 安全注意事项

### 6.1 令牌管理
- Access Token有效期：24小时
- Refresh Token有效期：7天
- 令牌过期后需重新获取

### 6.2 请求限制
- 单个文件最大10MB
- 支持的文件类型有限制
- API调用频率限制（可配置）

### 6.3 数据安全
- 所有密码使用BCrypt加密存储
- 敏感信息不在日志中记录
- 建议生产环境使用HTTPS

---

## 7. 开发环境配置

### 7.1 必需环境
- Java 21+
- PostgreSQL 12+
- Redis 6.0+
- Maven 3.6+

### 7.2 配置文件示例
```yaml
# application.yaml
server:
  port: 8000

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ragdemo
    username: postgres
    password: your-password
  
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: qwen3
      embedding:
        options:
          model: bge-m3

app:
  jwt:
    secret: your-jwt-secret
    access-token-expiration: 86400
    refresh-token-expiration: 604800
```

---

## 8. 版本信息

- **API版本**: v1.0
- **文档版本**: 2024-01-01  
- **Spring Boot版本**: 3.5.5
- **支持的Ollama模型**: gpt-oss（可根据需要扩展）

---

*此文档由RAG Demo项目自动生成，如有疑问请联系开发团队。*
