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
| `LICENSE` | MIT 许可 |

## 管理约定（agent 自律规则）

1. **有价值即归档**：每次对话任务收尾时，凡可复用的结论/产物 → 归档到对应目录；不确定价值时保守归档（宁可多存）。
2. **知识优先**：技能/踩坑/方法论优先沉淀到 `knowledge/`（与本地技能库保持同步）。
3. **技能更新 → 同步 knowledge 副本**：本地技能库某技能更新后，把最新 `SKILL.md` 复制到 `knowledge/<技能名>.md`，保持两处字节一致。
4. **独立产物**：单个任务的成果放 `projects/<任务名>/`，带简短 README 说明（背景、用法、前提）。
5. **提交规范**：commit message 用中文简述（如 `knowledge: 新增 DSH 前端插件开发技能`、`projects: 归档 Harness用量 交付说明`）。
6. **README 索引**：有意义的目录变更同步更新本 README 的文件清单。
7. **不收录**：私密凭据（API Key/token）、用户未公开的私有信息、无长期价值的一次性临时文件。

## 内容索引

### knowledge/（技能与经验）

- `agent-artifacts.md` — 本仓库的管理约定与归档规则（目录约定、同步规则、agent 每会话必读）
- `c-disk-cleanup.md` — C 盘与产物清理清单（可清项/不碰清单/keystore 备份教训，2026-08 实战验证）
- `constraints-audit.md` — 全局约束审查与优化流程（4 层来源、诊断重复冲突、合并去重清单）
- `dsh-plugin-transport.md` — DSH client 插件打包/传送机制深度调研（lib/src 链路、/plugins 路由、HMR、缓存真相，含排查套路）
- `dsh-session-log.md` — DSH 会话日志存储/读取方法（磁盘位置、session.list/export API、zstd 多帧解压、脉络提取）
- `dsh-web-launcher.md` — DSH Web 服务启动器（流程/踩坑/故障排查）
- `dsh-web-plugin.md` — DSH Web 前端插件开发（槽位机制、shell.overlay、lib/src 构建链路）
- `pwa-android-apk.md` — PWA → Android APK 打包（GitHub Pages、PWABuilder、本地 Gradle 构建、DeepSeek 数据源）
- `llama-cpp-android.md` — 安卓 App 集成 llama.cpp 本地推理（源码获取/NDK 编译/JNI 流式/GGUF 接入，2026-08 实战）
- `task-output-layout.md` — 任务文件组织规范（Temp Document/OutPut）
- `skill-library-evolution.md` — 技能库进化流程

### projects/（任务产物）

- `dsh-subagent-monitor/` — DSH 前端改造：子代理活动监视窗口（shell.overlay 浮层，含补丁可 `git am` 恢复）
- `global-constraints-summary/` — 全局约束优化总结（约束来源全景、诊断表、合并去重后的统一约束清单）
- `电脑清理扫描/` — 2026-08-24 全盘清理（扫描报告 + 清理前后对比，释放约 16.9 GB）

### templates/（模板）

- `SKILL.md` — 新技能模板（frontmatter + 触发条件/步骤/规则/示例/注意事项结构）
- `CHANGELOG.md` — 技能版本记录模板（日期、版本、变更点、原因）
- `task-README.md` — 任务文件夹说明模板（背景、目录清单、使用说明）

### tools/（工具脚本）

- `dsh-session-decompress.js` — 解压 DSH 会话日志（session.jsonl.zstd 多帧格式），Node 22+ 零依赖
- `dsh-session-extract.js` — 从会话 JSONL 提取对话脉络（用户消息/助手文本/turn 过滤）
