# DSH 会话日志的存储与读取方法

> 场景：需要回顾某历史对话的内容（用户要求"查看 XX 对话里做了什么"）。
> 本机 DSH_HOME：`C:\Users\y1954\.dsh`。

## 1. 存储位置

- 会话日志：`C:\Users\y1954\.dsh\sessions\<工作区编码名>\<session-id>\session.jsonl.zstd`
  - 工作区编码示例：`E:\Work Document` → `--E-Work~0020Document--`
  - 子代理日志同目录（`session.1.jsonl.zstd` 等）
- 元数据：`C:\Users\y1954\.dsh\storages\workspace.json`（工作区→会话 id 映射）、`session_projcache.json`（会话投影：titles/todos/stats）
- **磁盘日志可能滞后**：DSH 服务进程内可能持有尚未落盘的会话（或切换到其他后端）。磁盘上没有 ≠ 会话不存在，先查服务。

## 2. 查会话列表（最可靠：走服务 API）

磁盘可能不全，直接用 Web 服务 API 列会话：

```
POST http://127.0.0.1:3080/api/session.list
Content-Type: application/json
{"type":"client-request","rpcId":"probe-1","method":"session.list","payload":{}}
```

响应 `result.value.items[]` 含 sessionId、updatedAt、running、projections.values.title 等。
注意：Invoke-WebRequest 默认按 Latin-1 显示中文标题乱码，需 UTF-8 解码或看英文/时间戳辅助判断。

## 3. 导出会话内容

```
GET http://127.0.0.1:3080/api/session.export?sessionId=<session-id>
```

返回 ZIP（含 `session.jsonl` + `media/`）。PowerShell 5.1 的 Expand-Archive 会因中文条目名报"路径格式不支持"，**用 `tar -xf`**（bsdtar）解压。

## 4. 解压 .jsonl.zstd（多帧 zstd）

DSH 用「多帧 concatenated zstd」容器（header 帧 + 追加帧，每帧独立校验）。Node 的
`zstdDecompressSync` 一次只解第一帧，必须按帧结构扫描后逐帧解压。现成工具：

```
node tools/dsh-session-decompress.js <session.jsonl.zstd> [out.jsonl]   # Node 22+
```

## 5. 提取对话脉络

```
node tools/dsh-session-extract.js <session.jsonl> --mode user > 用户消息.txt      # 仅真实用户输入
node tools/dsh-session-extract.js <session.jsonl> --mode assistant --min-turn 30  # 指定 turn 的助手文本
```

- 判断真实用户消息：`user/message` 中 `source.kind === 'user'`；插件注入的 system-reminder 也是 user/message，需排除。
- 助手文本在 `assistant/message` 的 `content[]` 里 `type === 'text'` 的条目；reasoning 块不包含执行叙述时可忽略。

## 6. 踩坑记录（2026-08-23 实测）

1. 磁盘 zstd 只到 8/21 但会话 8/23 仍活跃 → 磁盘滞后，以 `/api/session.list` 为准（本例"优化桌面启动程序"会话 33 turns 全量内容只有 API 导得出）。
2. 会话标题不更新：标题来自首条消息（"读取Work Document里的要求并实现"），实际任务链（启动器→PWA→APK→GitHub 仓库→子代理监视）靠 todos 与用户消息还原。
3. `scanZstdFrames` 的帧扫描逻辑照抄 DSH `packages/session/session-persistence-jsonl/src/zstd.ts`。
4. API 调用需完整 RPC 包络 `{"type":"client-request","rpcId","method","payload"}`，直接 POST 裸 JSON 会报 invalid client-request。
