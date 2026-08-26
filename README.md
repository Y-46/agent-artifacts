# agent-artifacts — AI Agent 产物仓库

本仓库由 AI agent（DeepSeek Harness）自主管理，用于沉淀所有对话任务中**有价值的产物**：技能、经验、模板、工具脚本、任务成果。作用：跨对话复用、长期记忆、知识不因会话结束而丢失。

> 2026-08-26 按机主指示大换血：旧知识/旧产物已删除（见 git 历史）。

## 目录结构（约定）

| 目录 | 用途 |
|------|------|
| `knowledge/` | 可复用技能与经验文档（与本地技能库 `E:\Deep Seek Harness\skills\` 同步） |
| `projects/` | 任务交付产物（源码、补丁、报告样例） |
| `templates/` | 通用模板（README 模板、技能模板、任务组织模板） |
| `tools/` | 可复用工具脚本 |
| `LICENSE` | MIT 许可 |

## 管理约定（详见 knowledge/agent-artifacts.md）

1. 有价值即归档；2. 知识优先；3. 技能更新 → 同步 knowledge 副本；4. 中文 commit；5. 不收录凭据/私密信息。

## 内容索引

### knowledge/（技能与经验）

- `agent-artifacts.md` — 本仓库管理约定与本机网络/凭据环境
- `claude-code-subagent.md` — 子代理并行分发（>3 任务；原生子代理包装 Claude Code；浮层+状态块汇报）
- `end-of-session-cleanup.md` — 会话结束清理规则（只留目标产物）
- `dsh-global-agents.md` — DSH 全局指令机制（AGENTS.md；思考链中文规则）
- `dsh-subagent-monitor-inject.md` — 部署版注入"子代理活动"浮层方法（诊断与回滚）

### projects/（任务产物）

- `dsh-subagent-monitor/` — 子代理监视浮层（源码补丁 + 注入式应用说明）

### templates/（模板）

- `SKILL.md` / `CHANGELOG.md` / `task-README.md`

### tools/（工具脚本）

- `subagent-runner.ps1` — Claude Code 单任务执行器（DSH 子代理分发核心）
- `dsh-session-decompress.js` / `dsh-session-extract.js` — DSH 会话日志解压与脉络提取
