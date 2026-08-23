# C 盘与产物清理清单（2026-08-23 实战验证）

> 触发：用户要求「清理 C 盘」或「清理无用产物」。

## 可安全清理（缓存类，可随时重建）

| 项 | 位置 | 命令/方法 | 实测回收 |
|---|---|---|---|
| 用户临时文件 | `%TEMP%`（AppData\Local\Temp） | 删除内容（正在使用的文件会跳过） | 807 MB |
| npm 缓存 | `AppData\Local\npm-cache` | 直接删目录（`npm cache clean --force` 受执行策略限制时直接删目录等价） | 722 MB |
| pnpm store | 全局 store | `pnpm.cmd store prune`（本机 pnpm 在 `AppData\Roaming\npm\pnpm.cmd`） | ~200 MB |
| Gradle 构建缓存 | `~\.gradle\caches` | 删除（下次 Android 构建重新下载依赖，首次会慢） | 779 MB |
| VS Code 缓存 | `AppData\Roaming\Code\{CachedExtensionVSIXs,Cache,CachedData,logs}` | 删除（**不碰 User/ 配置**） | ~400 MB |
| Edge 缓存 | `AppData\Local\Microsoft\Edge\User Data\Default\{Cache,Code Cache,GPUCache}` | 删除（**不碰 Login Data/History 等用户数据**） | ~476 MB |
| 回收站 | — | `Clear-RecycleBin` | 视情况 |

## 产物清理判定（本机工作区）

- **构建副本可删**：AGP/TWA 构建工程副本（如历史 `E:\Work Document\AndroidBuild`、`E:\Android\build`、`E:\Android\ds-usage`），前提是源码与 APK 已交付到 `Temp Work Document\<任务>\OutPut`。
- **签名密钥先备份**：构建副本里若含 `*.keystore`，删除前必须确认 `OutPut` 有备份；没有就先复制（教训：harness.keystore 曾只存在于构建副本中）。
- **交付物保留**：各任务 `OutPut\` 内的 APK/源码/文档/密钥备份。
- **0 字节遗留文件**：如 `repo-info.json`，直接删。

## 不碰清单（避免事故）

1. **Windows 系统目录**：WinSxS、System32、SoftwareDistribution、Installer（清理需 DISM/管理员，风险高，非必须不动）。
2. **用户软件数据**：WPS(kingsoft)、酷狗(KuGou8)、腾讯(Tencent)、Steam、OneDrive 同步缓存、claude CLI。
3. **开发环境**：VS Code 扩展（`.vscode\extensions`）、Android SDK（本机在 `E:\Android\sdk`）、pnpm（DSH 构建依赖）、DSH 仓库。
4. **会话日志/技能库/AGENTS.md**：跨会话记忆载体。

## 经验

- 清理前先 `Get-PSDrive C` 记录基线，事后对比释放量。
- PowerShell 字符串插值里 `"Code\$d: x"` 会把 `$d:` 解析成驱动器引用报错——用 `"Code ${d} x"` 或 `"Code $d 清理前 $t MB"`。
- 非管理员进程无法停 wuauserv 清 SoftwareDistribution，属正常，跳过即可。
