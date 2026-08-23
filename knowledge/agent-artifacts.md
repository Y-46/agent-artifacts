---
name: agent-artifacts
description: agent 自有的公共产物仓库 Y-46/agent-artifacts：跨对话沉淀有价值产物（技能/经验/模板/工具/任务成果），任何对话任务收尾时有价值产物应归档于此。
---

# agent-artifacts 产物仓库（agent 自有）

## 仓库信息

- 地址：`https://github.com/Y-46/agent-artifacts`（公开）
- 本地工作副本：`E:\AgentWorkspace\agent-artifacts`
- 权限来源：git credential fill（GCM，`gho_` token，repo scope）；推送网络不稳时循环重试（每次间隔 8 秒，最多 6 次）

## 目录约定

| 目录 | 用途 |
|---|---|
| `knowledge/` | 可复用技能与经验（与本地技能库 `C:\Users\y1954\.agents\skills\*\SKILL.md` 同步，`.md` 后缀） |
| `projects/<任务名>/` | 任务交付产物（带简短 README：背景/用法/前提） |
| `templates/` | 通用模板 |
| `tools/` | 可复用脚本 |
| `README.md` | 索引与管理约定（有意义的目录变更要同步更新它） |

## 自律规则（每个对话任务收尾时）

1. **有价值即归档**：可复用结论/产物归档到对应目录；不确定价值时保守归档。
2. 知识优先入 `knowledge/`；任务成果入 `projects/<任务名>/` 并配说明。
3. commit message 用中文简述（如 `knowledge: 新增 XXX 技能`、`projects: 归档 XXX`）。
4. **不收录**：私密凭据、用户未公开的私有信息、无长期价值的一次性临时文件。
5. 推送失败 → 先在本地工作副本 commit 落盘，说明缘由，下次会话再补推（本地副本是唯一权威，勿丢失）。

## 联动

- 技能库进化见 `skill-library-evolution`；本地任务组织见 `task-output-layout`。
