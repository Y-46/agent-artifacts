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

## 2026-08-24 全盘扩展（D:/E:/F: + 构建产物深挖，实测再释放 16.9 GB）

### 新增可清项（Android 构建生态，重点！）

| 项 | 位置 | 实测回收 |
|---|---|---|
| AGP 构建产物 | `<工程>\app\build\intermediates`（中间产物 5.3 GB） | 5.3 GB |
| 重复 APK | `<工程>\app\build\outputs`（与任务 `OutPut\apk\` 完全重复，本机 2×1.1 GB） | 2.2 GB |
| 工程内 Gradle 缓存 | `<工程>\.gradle-home`（自定义 GRADLE_USER_HOME 时在工程内，caches+wrapper） | 1.5 GB |
| CMake 缓存 | `<工程>\app\.cxx` | 0.77 GB |
| pnpm store | `E:\.pnpm-store`（本项目 store 在盘根） | 0.89 GB |
| 模拟器组件 | `sdk\system-images`（3.58 GB）+ `sdk\emulator`（1.03 GB）——**不用模拟器可整删**，sdkmanager 可重装 | 4.6 GB |

**保留**：`sdk\ndk`、`platforms`、`build-tools`、`cmake`、`cmdline-tools`（编译必需）；`node_modules`（服务运行中）。

### 删除卡壳的解法（本机实测）

- `Remove-Item -Recurse -Force` 遇到深路径/占用会**静默失败**（SilentlyContinue 吞错），先 `Test-Path` 复查。
- 卡壳目录改用 .NET：`[System.IO.Directory]::Delete($path, $true)` —— 一次搞定 `.gradle`（Remove-Item 删不掉的）。
- 超长文件名（pnpm store 哈希文件名）用 `\\?\` 前缀 + `[System.IO.File]::Delete("\\?\" + $f.FullName)`。
- 仍删不掉的个别文件（如 pnpm store 被占用）可忽略：本机仅剩 5 个/30 MB，重启或下次 install 后可删。

### 其他经验

- 全盘图片扫描结论：用户区图片常仅几十张/几 MB（本机 26 张/1.3 MB），**无需专门清理**，除非发现重复大图。
- `SoftwareDistribution\Download` 目录 ACL 仅 SYSTEM，非管理员必失败（与 08-23 结论一致），走系统「磁盘清理」。
- `Get-ChildItem -Include` 在 `-Path "$d\*" -Recurse` 组合下会**静默返回空**（本机踩坑），改用 `Where-Object { $exts -contains $_.Extension.ToLower() }`。
- 任务归档 `Temp Work Document\<任务>\Temp Document\` 里常有一堆 `build-*.log`/`cmake-*.log`，确认任务完成后可批量删，保留 `*.md` 文档。

## 经验

- 清理前先 `Get-PSDrive C` 记录基线，事后对比释放量。
- PowerShell 字符串插值里 `"Code\$d: x"` 会把 `$d:` 解析成驱动器引用报错——用 `"Code ${d} x"` 或 `"Code $d 清理前 $t MB"`。
- 非管理员进程无法停 wuauserv 清 SoftwareDistribution，属正常，跳过即可。
- 大目录删除用后台任务 + `Test-Path` 复查，别在单个前台命令里干等。
