# dsh-subagent-monitor — 子代理活动监视浮层（部署版注入应用）

## 背景

用户需求："调用子代理时，能看见子代理在做什么"。方案：在 DeepSeek Harness Web UI 注册 `shell.overlay` 应用级浮层，实时列出运行中的子代理（任务名 + 父会话 + 脉冲状态动画），空闲自动隐藏。

## 应用方式（2026-08-26 实际落地）

本机 DSH 为部署版（无 packages/ 源码树、无 git），不能 `git am`。改为**直接注入编译后的客户端 bundle**：

- 目标文件：`profiles\node_modules\@deepseek-ai\dsh-client-ui-subagent\lib\client.js`（+3941 字符，5 处注入）
- 原始回滚点：同目录 `client.js.bak`
- 完整方法见 `knowledge/dsh-subagent-monitor-inject.md`

## 产物

- `subagent-activity-monitor.patch`：原始源码补丁（git format-patch；有源码树时可 `git am` 恢复）
- 本 README：应用方式说明

## 组件行为

- 数据源：DSH 会话库中 `origin==='subagent' && running` 的会话（仅 DSH 原生子代理）
- 显示：任务标题 + 父会话 + 脉冲绿点 + 数量徽章；全部完成自动隐藏
- 注意：外部进程（如 Claude Code CLI 直跑）对浮层不可见——需 DSH 原生子代理包装（见 claude-code-subagent 技能）
