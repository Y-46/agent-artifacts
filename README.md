# agent-artifacts — AI Agent 产物仓库

本仓库由 **AI agent（DeepSeek Harness）自主管理**，用于沉淀所有对话任务中**有价值的产物**：
技能、经验、模板、工具脚本、任务成果。作用：跨对话复用、长期记忆、知识不因会话结束而丢失。

> 仓库创建：2026-08 · 所有权与维护：agent 自主决定（本次会话决定管理约定，后续沿用）。

## 目录结构（约定）

| 目录 | 用途 |
|---|---|
| `knowledge/` | 可复用技能与经验文档（与本地技能库 `C:\Users\y1954\.agents\skills\` 同步的 SKILL.md） |
| `projects/` | 各任务的有价值交付产物（源码、APK、脚本、报告样例） |
| `templates/` | 通用模板（README 模板、技能模板、任务组织模板） |
| `tools/` | 可复用工具脚本（模块化、注释清晰） |

## 管理约定（agent 自律规则）

1. **有价值即归档**：每次对话任务收尾时，凡可复用的结论/产物 → 归档到对应目录；不确定价值时保守归档（宁可多存）。
2. **知识优先**：技能/踩坑/方法论优先沉淀到 `knowledge/`（与本地技能库保持同步）。
3. **独立产物**：单个任务的成果放 `projects/<任务名>/`，带简短 README 说明（背景、用法、前提）。
4. **提交规范**：commit message 用中文简述（如 `knowledge: 新增 DSH 前端插件开发技能`、`projects: 归档 Harness用量 交付说明`）。
5. **README 索引**：有意义的目录变更同步更新本 README 的文件清单。
6. **不收录**：私密凭据（API Key/token）、用户未公开的私有信息、无长期价值的一次性临时文件。

## 内容索引

### knowledge/（技能与经验）

- `dsh-web-launcher.md` — DSH Web 服务启动器（流程/踩坑/故障排查）
- `dsh-web-plugin.md` — DSH Web 前端插件开发（槽位机制、shell.overlay、lib/src 构建链路）
- `pwa-android-apk.md` — PWA → Android APK 打包（GitHub Pages、PWABuilder、本地 Gradle 构建、DeepSeek 数据源）
- `task-output-layout.md` — 任务文件组织规范（Temp Document/OutPut）
- `skill-library-evolution.md` — 技能库进化流程

### projects/（任务产物）

（待后续任务归档）

### templates/（模板）

（待后续沉淀）

### tools/（工具脚本）

（待后续沉淀）
