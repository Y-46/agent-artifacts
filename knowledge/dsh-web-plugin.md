---
name: dsh-web-plugin
description: 在 DeepSeek Harness（DSH）源码里开发 Web 前端插件/UI 组件：槽位（slot）注册机制、shell.overlay 浮层层、客户端数据（useSessions/SessionListState）、以及最关键的 lib/src 构建链路与验证方法。
---

# DSH Web 前端插件开发

## 触发条件

- 需要给 DSH Web GUI（apps/web / packages/client/*）增加界面组件、浮层、面板、自定义槽位内容；
- 修改 DSH client 包源码后构建产物"没变化"、无法验证改动。

## 核心架构（20 秒版）

1. **槽位（Slot）体系**：`packages/client/ui-slots` 的 SlotRegistry。槽位契约由拥有包通过 `declare module '@deepseek-ai/dsh-client-ui-slots' { interface SlotMap { 'xxx': {kind, scope} } }` 声明；注册用 `ctx.slots.inject(slot, () => ctx.slots.register({name, id, locale, inject}, Component))`。
2. **shell.overlay 是官方预留的空浮层槽**（ui-layout 声明，`{kind:'list', scope:'root'}`，additive，层本身 click-through、组件用 CSS `pointer-events:auto` 收回来）。AppFrame.tsx 渲染它。**应用级浮层（右侧活动窗口/徽章/提示）优先注册到这里**，勿注册 'root'（那是 single 槽，会替换整页）。
3. **组件 props**：`PropsRuntime<'slot.key'> & PropsLocale<typeof NS>`。`useSessions` 等 hooks 由 GlobalStandardProps 注入（root 作用域自动获得）；数据 `SessionListState{ids,byId,current,phase,subagentsByParent,jobsBySession}`，`SessionSummary{displayTitle,origin?('subagent'),parentId?,running,updatedAt}`，`indexSubagentDescendants(byId)` 统计子代理后裔。
4. **样式**：`.module.css` + `import css from './X.module.css'`；组件不 import cordis（纪律）。

## ⚠️ 构建链路（最大的坑，必读）

- 包 `package.json` 的 `exports` 指向 **`./lib/*`（编译产物），不是 src**。Vite 打包走 exports → 用 lib。
- **只改 src 不生效**：必须 `pnpm run build:lib:client`（=`tsc -b tsconfig.client.json && tsdown --env.DSH_BUILD_FACE client`，全量数分钟），再 `pnpm run build:web`（Vite，几秒）。
- **client 插件不进 dist**：ui-subagent 这类插件由 Web 服务端按 manifest（`packages/bundle/web-app/cordis.patch.yml` 的 row + `package.json` 依赖）运行时加载，apps/web/dist 里搜不到是**正常**的，别误判。
- **传送链路（实测确认）**：浏览器拿服务端注入的 `window.__DSH_BOOT__` 清单 → 动态 `<script src="/plugins/<id>/client.js?rev=<sha1>">` 拉取（`packages/client/modules` 的 `ClientModuleRegistry`）；服务端**每次实时 readFile 读盘**、`cache-control: no-cache`，无内容级缓存。`.dsh-build/` 只是构建记录（发布校验用），运行时**不读**。
- **两个"静默旧产物"的真正来源**：
  1. `lib/client.js` 过期（改了 src 没跑 build/watch）——最常见；
  2. `pkgMeta` 按包名缓存「是否 client 包 + clientPath」（永不过期）：**新增/移除某包的 `dsh.client` 声明需重启服务**；bundle 内容变化只能经 HMR `rebuilt()` 进图。
- **dev:web 的 watch（HMR）**：`scripts/dev-web.ts` 三段联动（`tsc -b --watch` → tsdown watch 重写 `lib` → `vite build --watch` 重写 dist），**且要求先有一次完整 `pnpm run build`**，缺阶段会**静默显示旧产物**（不报错！）——"改了却没生效"要优先怀疑这条。
- **验证步骤**：改 src → `build:lib:client` → `build:web` → **重启 dsh web 服务**（启动器 -Stop + start-dsh.cmd hidden）→ 浏览器 **Ctrl+F5 强刷**。改一次源 = 这几步都得走。
- 单包快速类型检查：`node_modules\.bin\tsc.cmd --noEmit -p packages/client/ui-subagent/tsconfig.json`。
- pnpm 在 `C:\Users\y1954\AppData\Roaming\npm\pnpm.cmd`（不是 node_modules\.bin）；npm/npx 的 .ps1 shim 会被执行策略拦，用 .cmd。

## 槽位跨包类型合并

在包 A 里注册包 B 声明的槽位，类型检查会报 `argument '"xxx"' is not assignable to parameter of type ...`——因为 **A 的编译单元看不到 B 的 SlotMap 合并**。修法：在 A 的入口加**空类型导入** `import type {} from '@deepseek-ai/dsh-client-<B>/client'`（现有包都这么做）。

## 最小改造清单（以"现有包内加浮层组件"为例）

1. 新文件：`packages/client/ui-xxx/src/client/MyPanel.tsx` + `MyPanel.module.css`（组件：`PropsRuntime<'shell.overlay'> & PropsLocale<typeof NS>`）
2. `locales.ts`：zh（源 truth）/en 加 key，`NS` 复用
3. 入口 `src/client/index.ts`：import 空类型挂载 B 的 client + `ctx.slots.inject('shell.overlay', () => ctx.slots.register({ name:'shell.overlay', id:'my-panel', locale: NS }, MyPanel))`
4. 包已在 `cordis.patch.yml` 清单 → **零清单改动**；若新建包则还要加 patch row + package.json 依赖
5. 全流程：tsc 检查 → build:lib:client → build:web → 重启服务 → 强刷

## 调试/验证技巧

- 先用只读调研（子代理）摸清框架契约再动手，别盲改（本技能的所有机制来自一次实测调研）。
- UI 改动可用"调用子代理"做活体演示（组件显示 running 子代理 → 窗口出现）。
- 服务端插件加载链路若需深查：搜 `client-plugin` / bundle 生成点（`packages/bundle/web-app`、`packages/host/*`、`packages/client/web`）；**完整机制文档（含文件路径/代码摘录）已归档：agent-artifacts 仓库 `knowledge/dsh-plugin-transport.md`**。
- 快捷验证服务端实际交付：`curl http://127.0.0.1:3080/plugins/@deepseek-ai/dsh-client-ui-subagent/client.js`（服务每次读盘，看到即实际交付）。

## 关联

- 本机服务环境与启动/重建：`dsh-web-launcher`；PWA/Android 打包：`pwa-android-apk`；技能维护见 `skill-library-evolution`。
- 参考实现：`packages/client/ui-subagent/src/client/SubagentActivityMonitor.tsx`（DSH 源码内的子代理活动监视窗口）。
