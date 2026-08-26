---
name: dsh-global-agents
description: DSH 用户全局指令机制：$DSH_HOME/AGENTS.md 的加载、动态同步与内容约定；本机已配置"思考链中文"等全局规则。
---

# DSH 全局指令机制（AGENTS.md）

## 机制（dsh-agent-instructions 插件）

- 用户全局指令文件：`$DSH_HOME/AGENTS.md`（本机 DSH_HOME = `E:\Deep Seek Harness`）
- 加载时机：每个会话开始前注入（user 角色 system-reminder 包络）；**文件变更会动态同步进活动会话**（实测：写入后立即收到注入提醒）
- 候选名：`AGENTS.md` / `CLAUDE.md`；局部覆盖：`AGENTS.local.md` / `CLAUDE.local.md`
- 项目级：从项目根（含 .git 的最近祖先，否则 cwd）到 cwd 的每级目录都可放 AGENTS.md，越具体优先级越高
- 渲染受 maxBytes 预算约束，超限按从宽到窄截断

## 本机已配置（2026-08-26）

| 规则 | 内容 |
|------|------|
| 思考链语言 | 所有对话的 thinking/reasoning 必须用简体中文；术语/代码/路径保留原文；最终回复跟随用户语言 |
| 技能索引 | 任务 >3 分发按 claude-code-subagent；收尾清理按 end-of-session-cleanup（勿重复创建） |

## 本机网络环境踩坑（实测）

- 系统 Schannel TLS 故障（SEC_E_NO_CREDENTIALS）：curl.exe 与 git 默认后端全部失败；Node.js（OpenSSL）与 git `http.sslBackend=openssl` 可用
- github.com 本机 DNS 可能解析到不可达 IP（如 20.205.243.166），可达 IP 如 140.82.112.3 / api.github.com(20.205.243.168)；git 可用 `-c http.extraHeader="Host: github.com"` 直连好 IP 绕过
- GCM 凭据助手在沙箱内因管道限制崩溃；提权（danger-full-access）下可静默取 GitHub PAT
