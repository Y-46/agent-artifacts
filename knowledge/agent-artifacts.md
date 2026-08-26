---
name: agent-artifacts
description: agent 自有的公共产物仓库 Y-46/agent-artifacts：跨对话沉淀有价值产物（技能/经验/模板/工具/任务成果），任何对话任务收尾时有价值产物应归档于此。
---

# agent-artifacts 产物仓库（agent 自有）

## 仓库信息

- 地址：`https://github.com/Y-46/agent-artifacts`（公开，MIT License）
- 本地工作副本：`E:\AgentWorkspace\agent-artifacts`（唯一权威副本）
- 本机网络环境（2026-08-26 实测）：
  - 系统 Schannel TLS 故障（SEC_E_NO_CREDENTIALS）→ git 必须 `http.sslBackend=openssl`
  - github.com 本机 DNS 可能解析到不可达 IP → remote 用可达 IP 直连：`https://140.82.112.3/Y-46/agent-artifacts` + `http.extraHeader=Host: github.com` + `http.sslVerify=false`（local config 已配好）
  - GCM 凭据仅在提权（danger-full-access）下可静默读取（沙箱管道限制导致默认失败）

## 目录约定

| 目录 | 用途 |
|------|------|
| `knowledge/` | 可复用技能与经验（与本地技能库 `E:\Deep Seek Harness\skills\*\SKILL.md` 同步，`.md` 后缀） |
| `projects/<任务名>/` | 任务交付产物（带简短 README：背景/用法/前提） |
| `templates/` | 通用模板（SKILL.md / CHANGELOG.md / task-README.md） |
| `tools/` | 可复用脚本 |

## 自律规则（每个对话任务收尾时）

1. **有价值即归档**：可复用结论/产物归档到对应目录；不确定价值时保守归档。
2. 知识优先入 `knowledge/`；任务成果入 `projects/<任务名>/` 并配说明。
3. **技能更新 → 同步 knowledge 副本**：本地技能库某技能更新后，必须把最新 `SKILL.md` 复制到 `knowledge/<技能名>.md`，保持两处字节一致。
4. commit message 用中文简述（如 `knowledge: 新增 XXX 技能`）。
5. **不收录**：私密凭据、用户未公开的私有信息、无长期价值的一次性临时文件。
6. 推送失败 → 先在本地工作副本 commit 落盘，说明缘由，下次会话再补推（本地副本是唯一权威，勿丢失）。

## 历史

- 2026-08-26 应机主指示**大换血**：删除旧知识/旧产物（git 历史可恢复），仅保留本会话成果与通用资产。
