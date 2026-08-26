---
name: dsh-subagent-monitor-inject
description: 在无源码树的 DSH 部署版中，直接注入编译后的客户端插件 bundle 添加"子代理活动"浮层；含注入方法、诊断与回滚。
---

# DSH 部署版子代理浮层注入（实测验证 2026-08-26）

## 背景

需求：对话界面实时看到运行中的子代理（任务名+父会话）。官方路径是改源码 `packages/client/ui-subagent/src/client/`，但本机 DSH 是部署版：只有编译产物，无 packages/ 源码树、无 git。

## 关键机制

- 客户端插件 bundle：`E:\Deep Seek Harness\profiles\node_modules\@deepseek-ai\dsh-client-ui-subagent\lib\client.js`（`window.__ModuleLoader__.load({id, factory})` 格式）
- **服务端每次实时 readFile 读盘、cache-control: no-cache、无内容级缓存** → 改文件后浏览器刷新即生效，无需重启服务（pkgMeta 缓存只影响"是否 client 包"判定，改内容不受影响）
- 交付验证：`http://127.0.0.1:8080/plugins/@deepseek-ai/dsh-client-ui-subagent/client.js`（看到即实际交付）
- `shell.overlay` 槽位由 `@deepseek-ai/dsh-client-ui-layout` 声明并渲染（AppFrame 内 `renderSlot("shell.overlay")`，kind:'list', scope:'root'）

## 注入步骤（已验证）

1. 备份 `client.js` → `client.js.bak`（回滚点）
2. Node 脚本做 5 处字符串替换（每处断言锚点存在，缺锚点即失败）：
   a. zh/en 词库追加 `monitor.title` / `monitor.parent`
   b. 注入 CSS（仿照 bundle 内现有 style 标签注入模式）
   c. `function apply(ctx)` 前插入 `SubagentActivityMonitor` 组件（JSX 手写为 jsx/jsxs 调用）
   d. apply() 内注册 `ctx.slots.inject("shell.overlay", () => ctx.slots.register({name:"shell.overlay", id:"subagent-activity-monitor", locale:NS}, SubagentActivityMonitor))`
3. `node --check` 语法校验 + HTTP 交付验证 + 浏览器 Ctrl+F5

## 组件数据源要点（关键踩坑）

- 浮层只显示 **DSH 原生子代理会话**（SessionListState 中 origin==='subagent' && running）
- **外部进程（Claude Code CLI 直接后台跑）对浮层不可见** → 每个任务必须派生 DSH 原生子代理包装执行（见 claude-code-subagent 技能）
- 诊断方法：组件/注册处临时加 console.log → 用户 F12 控制台反馈（registering/render 日志确认链路）

## 回滚

`Copy-Item client.js.bak client.js`（原始版 41KB）
