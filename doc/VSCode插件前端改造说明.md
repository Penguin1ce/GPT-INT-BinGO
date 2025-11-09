# VS Code 插件前端改造说明

后端已经在文件上传→RAG 处理→Redis 写入这一流程中加入了实时通知能力，VS Code 插件需要完成以下配合工作以接收“处理成功”提示。

## 1. 建立 SSE 连接
- 在用户完成登录并拿到 `accessToken` 后，创建一个以 `Authorization: Bearer <token>` 头发起的 SSE 请求：
  - `GET /files/processing-stream`
  - `Accept: text/event-stream`
- 连接建立后会收到一次 `event: connected`，数据内容为 `"listening"`，表示订阅成功。
- 插件需要在扩展激活时发起连接，并在窗口/工作区关闭或用户登出时主动断开。

## 2. 处理文件完成事件
- 后端在 RAG 训练完成、向量写入 Redis 且状态更新为 `COMPLETED` 或 `FAILED` 后，会推送 `event: file-processing`。
- 事件数据结构：
```json
{
  "fileId": "38e8...",
  "filename": "demo.pdf",
  "status": "COMPLETED", // 或 FAILED
  "message": "文件向量生成并写入Redis成功",
  "timestamp": "2024-01-01T12:00:00.000"
}
```
- 插件需根据 `status`：
  - `COMPLETED`: 在 VS Code 通知中心或插件 UI 中提示“训练成功”，并刷新文件列表或索引状态。
  - `FAILED`: 提示具体失败信息，引导用户重试。

## 3. 与上传流程的衔接
- 上传接口 `/upload` 会立即返回 `fileId` 和初始状态 `PROCESSING`，插件应缓存这些任务并等待 SSE 推送。
- 若 30 秒内未收到对应任务的 `COMPLETED/FAILED`，可回退到轮询 `GET /files` 手动刷新，以防插件与 SSE 连接断开。

## 4. 异常处理建议
1. SSE 断线：实现指数退避重连，最多重试 5 次；超出后提示用户检查网络。
2. Token 过期：SSE 会收到 401 响应，插件应触发刷新 Token 或重新登录。
3. VS Code 侧 UI：为待处理文件展示“处理中”状态标签，确保用户在训练完成前不会重复上传相同文件。

完成以上改造后，插件即可在 RAG 向量写入 Redis 时获得即时成功提示，提升用户体验。
