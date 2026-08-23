# dsh-subagent-monitor — 子代理活动监视窗口（DSH 前端改造）

## 背景

用户需求："在我调用子代理时，能看见子代理在做什么"。方案：在 DeepSeek Harness Web UI
注册一个应用级浮层窗口（`shell.overlay` 槽位），实时列出所有**运行中**的子代理会话
（任务名 + 父会话 + 运行状态动画），运行结束自动消失。

## 产物

- **补丁**：`subagent-activity-monitor.patch`（git format-patch，可 `git am` 恢复）
- **对应 DSH 仓库本地 commit**：`10ebe24`（`E:\Deep Seek Harness`，master，作者 y1954，4 文件 +182 行）
- **已生效**：`lib` 编译产物 + 服务重启 + 浏览器强刷验收通过（"看到了"）

## 文件清单（相对于 DSH 仓库根）

```
packages/client/ui-subagent/src/client/SubagentActivityMonitor.tsx       [新增] 浮层组件
packages/client/ui-subagent/src/client/SubagentActivityMonitor.module.css [新增] 样式
packages/client/ui-subagent/src/client/index.ts                            [+12] 注册 shell.overlay
packages/client/ui-subagent/src/client/locales.ts                          [+4]  中英文案
```

## 恢复方法

```powershell
cd E:\Deep Seek Harness
git am "E:\AgentWorkspace\agent-artifacts\projects\dsh-subagent-monitor\subagent-activity-monitor.patch"
pnpm run build:lib:client && pnpm run build:web   # 然后重启服务 + 强刷
```

## 设计要点（复用）

- 挂 `shell.overlay`（DSH 官方预留的应用级浮层 list 槽，additive、点击穿透）
- 数据：`useSessions`（GlobalStandardProps）→ `SessionListState` 过滤 `origin==='subagent' && running`
- 注册：`ctx.slots.inject('shell.overlay', () => ctx.slots.register({name, id, locale}, Component))`
- 详见技能 `dsh-web-plugin` 与仓库 `knowledge/dsh-plugin-transport.md`（构建/加载机制）
