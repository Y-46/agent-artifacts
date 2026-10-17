# Pocket Harness

跑在安卓手机里的独立 agent harness —— 把桌面版"会调工具的 AI"塞进一个能装进兜里的 App。

- **完全独立运行**：不依赖电脑、不依赖本项目的服务端；手机直连你自己填的 OpenAI 兼容接口。
- **真的能干活**：不只会聊天，能读写文件、搜网页、抓正文，多轮工具调用直到把事做完。
- **看得见的过程**：工具调用卡片 + 审批弹窗 + 实时任务日志。
- **数据不出手机**：会话、日志、API Key 全在 App 私有目录，Key 用 Android Keystore 加密。

## 拿到 APK 的三种方式

### 1. GitHub Actions（推荐，电脑上零安装）

```bash
cd pocket-harness
git init && git add -A && git commit -m "init pocket harness"
# 在 GitHub 建一个空仓库，然后：
git remote add origin https://github.com/<你的账号>/pocket-harness.git
git push -u origin main
```

push 之后进仓库的 **Actions → Build APK**，跑完在页面底部 **Artifacts** 下载
`pocket-harness-debug-apk.zip`，解压得到 `app-debug.apk`，发到手机安装即可。

### 2. 本机 Android Studio

用 Android Studio 打开 `pocket-harness` 目录（Gradle 8.13 / AGP 8.11.1 / JDK 17），
同步后点 Run，或 `Build → Build App Bundle(s) / APK(s) → Build APK(s)`。

> 工程没有带 `gradle/wrapper/gradle-wrapper.jar`（那是二进制文件），
> Android Studio 导入时会提示生成 wrapper，点同意即可；或者手动执行一次
> `gradle wrapper --gradle-version 8.13`。

### 3. 命令行

```bash
# 需要 JDK 17 + Android SDK（ANDROID_HOME 指向 SDK 根目录）
gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

安装：`adb install -r app/build/outputs/apk/debug/app-debug.apk`

## 首次配置

打开 App → 底部 **设置**：

| 字段 | 说明 | 例子 |
| --- | --- | --- |
| Base URL | OpenAI 兼容地址，末尾不用带 `/chat/completions` | `https://api.deepseek.com/v1` |
| 模型名 | 服务商支持的模型 | `deepseek-chat` |
| API Key | 只存本机，Keystore 加密 | `sk-…` |
| 温度 / max_tokens / 最大轮数 | 保守默认值即可 | `0.6 / 4096 / 16` |
| 审批模式 | 全都问 / 只问写删 / 全部自动 | 建议先「只问写删」 |

内置三个一键预设：DeepSeek、OpenAI、本地自建（vLLM / Ollama，走局域网时允许 http 明文）。

保存后回「对话」，先发一句「列一下工作区里有什么」，验证能不能正常跑工具。

## 它能干什么

| 能力 | 在手机上的样子 |
| --- | --- |
| 流式对话 | 逐字输出，Markdown + 代码高亮块（代码块可一键复制） |
| 工具调用 | 每轮把模型的工具请求变成实际动作，结果做成可展开卡片 |
| 审批 | 写文件 / 删文件 / 建目录前弹窗，参数以 JSON 展示，允许或拒绝 |
| 会话 | 多会话列表、切换、重命名、删除，全部落盘 |
| 文件 | 浏览工作区与手机真实目录，文本/代码/图片预览，长按删除 |
| 任务 | 每次跑一輪就是一个 Job，实时日志、状态、耗时、可取消 |

### 内置工具

| 工具 | 作用 | 是否需要审批 |
| --- | --- | --- |
| `fs_list` | 列目录 | 否 |
| `fs_read` | 读文本文件 | 否 |
| `fs_search` | 按关键字搜文件内容 | 否 |
| `web_search` | DuckDuckGo 搜索 | 否 |
| `web_fetch` | 抓网页转纯文本 | 否 |
| `fs_write` | 写文件（覆盖） | 是 |
| `fs_mkdir` | 建目录 | 是 |
| `fs_delete` | 删文件/目录 | 是 |
| `jobs_list` | 查最近任务状态 | 否 |

### 路径规则

```text
notes.md              -> 应用工作区（App 私有目录）
workspace/src/a.kt    -> 同上，带前缀写法
device/Download/a.txt -> 用户授权的真实目录（需先在设置里授权一次）
```

工作区不需要任何权限，装完即用。要碰真实文件，去 **设置 → 工作目录 → 选择目录**，
用系统文件选择器挑一个文件夹（比如 Download 或某个项目目录），授权后 Agent 就能用
`device/` 前缀读写它。

## 试几条指令

```text
列一下工作区里有什么，挑一个文件读给我看
写一个 Python 脚本，把 image 目录下的图片按拍摄时间重命名
把 README.md 改短一点，保留所有代码示例
搜一下 Android 16 有哪些新特性，整理成 5 条中文要点
在 device/Download 里找出最大的 5 个文件
```

## 工程结构

```text
app/src/main/java/dev/harness/mobile/
├── MainActivity.kt          单 Activity 入口 + 通知权限
├── HarnessApp.kt            Application，持有依赖容器
├── core/
│   ├── AppContainer.kt      手工 DI：HTTP / 仓库 / 工具 / Runner
│   └── Prefs.kt             Settings、Android Keystore 加密、系统提示词
├── data/
│   ├── Models.kt            Session / Message / ToolCall / Job / RunState
│   ├── SessionRepository.kt 会话 JSON 落盘 + StateFlow
│   ├── JobRepository.kt     任务日志（内存实时 + 结束落盘）
│   └── Files.kt             WorkspaceStore / DeviceStore(SAF) / Paths 门面
├── net/
│   ├── OpenAiClient.kt      /chat/completions SSE + tool_calls 增量拼接
│   └── WebClient.kt         DuckDuckGo 搜索 + HTML 转正文
├── agent/
│   ├── AgentRunner.kt       主循环：流式→工具→审批→执行→继续
│   ├── Tools.kt             9 个工具的 schema 与实现
│   └── ApprovalGate.kt      挂起等待用户点头
├── service/AgentService.kt  前台服务，切后台不断
└── ui/
    ├── AppRoot.kt           底部四页 + 会话抽屉
    ├── ChatScreen.kt        对话流、工具卡、审批弹窗、输入栏
    ├── FilesScreen.kt       文件浏览与预览
    ├── JobsScreen.kt        任务列表与日志
    ├── SettingsScreen.kt    全部配置
    ├── SessionsScreen.kt    会话管理
    ├── Markdown.kt          轻量 Markdown 渲染
    └── Common.kt            头部栏、卡片、状态药丸等
```

技术栈：Kotlin + Jetpack Compose(Material3) + OkHttp + kotlinx.serialization。
**没有** Room/Hilt/Retrofit —— 会话和日志用单文件 JSON，依赖容器手写，构建面尽量小。

## 安全与隐私

- API Key 用 `AndroidKeyStore` 里的 AES/GCM 密钥加密后落盘，不写明文。
- 会话、任务日志、工作区文件都在 App 私有目录，其他 App 读不到；卸载即全部清除。
- 除模型接口和 `web_search` / `web_fetch` 外没有任何外发请求，没有统计、没有账号体系。
- 授权真实目录后，Agent 只在你选中的那棵子树里有权限，系统层面兜底。
- 审批默认「只问写删」；不想被问就切「全都问」，想全自动就切「全部自动」（自担风险）。

## 已知限制

- **没有 shell**：安卓沙箱里跑不了真命令。要跑命令得配 Termux / Shizuku，这一版刻意没做。
- **搜索依赖 DuckDuckGo HTML 版**，偶尔会被反爬挡；此时让 Agent 直接用 `web_fetch` 抓已知链接。
- **一次只跑一个任务**：Runner 是单例，同一时间只有一个会话在跑。
- **后台受系统调度限制**：前台服务能让它活得更久，但省电策略仍可能杀掉长任务；日志会保留，不会丢。
- **大体量上下文**：超过 `max_tokens` 后历史会报错，建议定期开新会话。

## 构建状态

- 已在本机实测编译通过：`gradle assembleDebug` → `app-debug.apk`（约 10.7 MB，约 22 秒增量构建）
- 产物校验：`package dev.harness.mobile`、`minSdk 26`、`targetSdk 36`（Android 16）、debug 签名，可直接安装
- 工具链：Gradle 8.13 / AGP 8.11.1 / Kotlin 2.1.21 / JDK 17+ / build-tools 36.0.0
- 踩过的坑（写给以后的自己）：
  - Kotlin 的 `Sequence` 没有 `limit()`，要用 `take()`
  - OkHttp 4.x 的 `ResponseBody` 没有 `peekBody()`，要自己用 `source()` + `okio.Buffer` 限长读取
  - `material-icons-core` 不在 Compose BOM 里，必须显式写版本
