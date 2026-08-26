---
name: claude-code-subagent
description: 当待处理任务超过3个且相互独立时，将子任务分发给本机 Claude Code CLI 子代理并行处理，并在对话界面（右下角浮层+消息状态块）持续汇报子代理处理的任务数与具体任务。
whenToUse: 当前待办任务列表（todo）中有超过3个相互独立的任务需要处理时；或用户明确要求使用子代理并行加速时。任务数不超过3个或任务之间存在依赖时不要分发。
metadata:
  modelInvocable: true
  userInvocable: true
---

# Claude Code 子代理分发规则（持久生效）

本技能定义本机子代理的接入方式，适用于**所有未来对话**。

## 目标
当待处理任务 > 3 个时，把相互独立的子任务分发给本机 Claude Code CLI 子代理并行执行，并在对话界面持续向用户汇报：**子代理处理的任务数**和**每个任务的具体内容/状态**。

## 分发条件
- ✅ 待办任务数量 **> 3** 且任务相互独立 → 分发
- ❌ 任务 ≤ 3 个 → 主代理自己处理，不分发
- ❌ 任务之间有依赖 → 串行处理，不分发

## 架构（实测验证，2026-08）
- **执行引擎**：Claude Code CLI（`C:\Users\y1954\AppData\Roaming\npm\claude.cmd`，DeepSeek 后端，headless）
- **可见性包装**：每个任务派生一个 **DSH 原生子代理**（subagent 工具）。DSH 会话库会记录这些原生子代理（origin='subagent'、running=true），右下角"子代理活动"浮层（已注入 dsh-client-ui-subagent 插件）据此实时显示。**Claude Code 外部进程对浮层不可见，因此必须走原生子代理包装，不要直接用后台 pwsh 跑 runner。**

## 操作步骤

### 1. 准备任务清单
把要分发的任务写入 `E:\Work Document\.agent-hub\tasks.json`（格式见 `tasks.example.json`，字段不可省略）：
```json
[
  { "id": "T1", "title": "简短任务标题（显示用）", "prompt": "自包含的任务提示词" },
  { "id": "T2", "title": "...", "prompt": "..." }
]
```
- `id` 唯一；`prompt` 必须**自包含**（Claude Code 看不到当前对话）

### 2. 为每个任务派生 DSH 原生子代理（最多 4 个并行，其余排队）
用 subagent 工具派生，每个子代理的 prompt 第一行写 **"任务id: 任务标题"**（这一行会显示在浮层中），正文固定为：
```
你的唯一任务：执行以下步骤并把结果原样返回，不要做任何其他事情。

1. 运行这条命令并等待其完成（阻塞）：
   powershell -NoProfile -ExecutionPolicy Bypass -File "E:\Work Document\.agent-hub\runner.ps1" -TaskId <id>

2. 读取文件 E:\Work Document\.agent-hub\reports\<id>.out.txt 的全部内容（UTF-8）。

3. 你的最终回复 = 该文件的原文内容，不要添加任何额外解释、前言或总结。
   若第1步失败，报告：退出码、以及 E:\Work Document\.agent-hub\reports\<id>.err.txt 的内容。
```
- 子代理默认后台运行；完成时会收到通知（含其最终回复=任务结果）
- runner.ps1 会自动从 tasks.json 提取该任务的 prompt 交给 Claude Code 执行，并写 reports/<id>.out.txt

### 3. 持续汇报（必须做）
子代理处理期间，**每一条消息**都要包含如下状态块：
```
📡 子代理运行中：X/Y 已完成
- [✅] T1: 标题
- [🔄] T2: 标题
- [⏳] T3: 标题
```
同时把每个子代理任务同步登记到 todo 列表（todo_write），保持对话界面可见。
（浮层由 DSH 自动显示：右下角"子代理活动"窗口，任务完成逐行消失。）

### 4. 收集结果
- 子代理的最终回复即结果（无需再读文件）；失败时排查 `E:\Work Document\.agent-hub\reports\<id>.err.txt`
- 把结果整理后回复用户，并更新 todo（完成/失败）
- 会话收尾时按 `end-of-session-cleanup` 技能清理运行时数据（`tasks.json`、`prompts\`、`reports\`、`status.json`、`summary.txt`）

## 基础设施
- 执行器：`E:\Work Document\.agent-hub\runner.ps1`（自动建目录、自动提取 prompt、写结果与状态标记）
- 子代理 CLI：`C:\Users\y1954\AppData\Roaming\npm\claude.cmd`（Claude Code 2.1.245，约 7-15 秒/任务）
- 后端：`https://api.deepseek.com/anthropic`，模型 `deepseek-v4-flash`（已登录）
- 每次调用消耗 DeepSeek API 额度，注意提示词与任务规模匹配


## 边界
- 只分发**独立、可并行**的任务；有依赖、需要连续上下文、或影响同一批文件的任务不要分发（避免写冲突）
- 涉及文件写入的任务给子代理指定明确的文件路径，避免与主代理操作同一文件
- 子代理不可用于需要实时交互的任务（headless 一次调用）
