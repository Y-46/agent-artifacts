# DSH Web client 插件打包与传送机制（深度调研）

> 调研时间：2026-08 · 只读调研 · 结论经源码确认
> 适用于：调试"改了 DSH 前端代码但页面没变化"、开发/挂载 client 插件、理解 HMR

## 一、bundle 生成（构建期）

- 模板：`packages/client/tsdown.client.ts` 的 `clientBundle()` 预设；每个 client 包一个 `tsdown.config.ts` 调用它（如 `packages/client/ui-subagent/tsdown.config.ts`）。
- 产物形态：closure-factory 浏览器 bundle——`window.__ModuleLoader__.load({ id, factory })`（`packages/client/ui-subagent/lib/client.js` 首行即此格式）。
- 声明条件：包 `package.json` 的 `dsh.client.platform: "web"` + `exports["./client"] → "./lib/client.js"`。
- 插件**不进 Vite 图**（`apps/web/vite.config.ts` L156-161 注释明确）：Vite 只构建 shell 与静态链接库，插件以运行时 bundle 到达浏览器。

## 二、传送链路（运行期）

- **服务端注册**：`packages/client/modules/src/index.ts` 的 `ClientModuleRegistry`（node 半端）扫描 loader entries → `clientExportOf()` 取 `exports["./client"]` → 绝对路径 `…/lib/client.js` → 组成 `WebBootGraph`（每行 `url: /plugins/<id>/client.js?rev=<sha1>`，rev=文件内容 sha1 前 12 位）。
- **路由**：注册 `/plugins` 前缀路由 `serveBundle`——每次请求**实时 readFile 读盘**，`cache-control: no-cache`；`?rev=` 仅作缓存破坏，路由本身忽略它。
- **HTML 注入**：`bootInjections()` 经 webserver 的 `index-inject` 事件注入三样东西：
  1. 内联脚本 `window.__ModuleLoader__` 队列 facade（`mode:"queue"` + `create()` 引导）；
  2. 两个 parser-blocking 预加载（runtime + modules 的 client.js）；
  3. global `window.__DSH_BOOT__ = <完整插件图 JSON>`。
- **浏览器加载**：`packages/client/web/src/boot.ts`（`AppWebEntry.run`）读 `__DSH_BOOT__` → `moduleLoader.create()` → `packages/client/modules/src/client/system.ts` `defaultLoadBundle` 对每行**动态 `<script src="/plugins/<id>/client.js?rev=…">`** 拉取；`immediately` 标记行在 boot 阶段预取。

## 三、HMR

- `packages/client/hmr/src/index.ts`：每 500ms `statSync` 轮询各 graph 行的 `lib/client.js`（mtime/size）→ 变化 → `clientModules.rebuilt(id)`（重算 rev、重组图）→ `/plugins/events` SSE 广播 `rebuilt` frame → 浏览器重拉新 rev。
- `cordis.patch.yml` 注释：`client-hmr` 行常挂但空闲，直到 `pnpm run dev:web` 真的重写 bundle 才生效。

## 四、src 还是 lib（关键结论）

- 服务端**只读 lib 产物，绝不读 src**。
- src→lib：根 `pnpm run build` `scripts/build.ts`（tsdown clientBundle），或 `scripts/dev-web.ts` 三段（`tsc -b tsconfig.client.json --watch` → tsdown watch → `vite build --watch`）；dev-web 要求**先有一次完整 `pnpm run build`**，缺阶段会**静默显示旧产物**。

## 五、缓存与旧代码残留

- `.dsh-build/client-build-environment.json` = 构建记录（commit + 各产物 sha256），**运行时进程不读**（仅 `scripts/release/families.ts` 与测试消费）。
- 服务端唯一缓存：`ClientModuleRegistry.pkgMeta`（按包名缓存"是否 client 包 + clientPath"，**永不过期**）→ 新增/移除包的 `dsh.client` 声明**需重启服务**；bundle 内容变化只能经 HMR `rebuilt()` 进图。
- **无内容级缓存** → 旧代码残留真实成因 = `lib/` 产物过期（改了 src 没跑 build/watch）。

## 六、启动入口（对照）

`apps/cli` 的 `dsh web`（`--profile web`）→ `apps/cli/src/profile-boot.ts` `composeProfile` → `packages/bundle/web-app/cordis.patch.yml`（挂 modules、client-hmr、全部插件行如 ui-subagent L251-252）。

## 排查套路（结论直接可用）

1. 改 src → `build:lib:client` → `build:web` → 重启服务 → 强刷（先怀疑 lib 过期）。
2. 验证服务端产物：直接 curl `http://127.0.0.1:3080/plugins/@deepseek-ai/dsh-client-ui-subagent/client.js` 看内容是否为新代码（服务每次都读盘，看到的就是实际交付）。
3. 怀疑缓存：只可能来自 `pkgMeta`（增删声明场景，重启解决），不存在字节级缓存。
