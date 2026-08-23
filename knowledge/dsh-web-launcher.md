---
name: dsh-web-launcher
description: 启动或检查 DeepSeek Harness Web 服务环境：端口探测、拉起 dsh web 服务、等待就绪并打开浏览器；桌面入口为 E:\Work Document\启动器\start-dsh.cmd（双击）。
---

# DSH Web 服务启动器

## 触发条件

- 用户要求启动 DSH Web 服务 / 打开 Harness 界面 / 双击桌面启动程序；
- 服务未运行时需要恢复服务环境，或需要判断服务是否在运行。

## 启动器文件

| 文件 | 作用 |
|---|---|
| `E:\Work Document\启动器\start-dsh.cmd` | **主入口**：纯 cmd 自包含实现，仅依赖 cmd 内建 + netstat + where + start + ping，不依赖 PowerShell。支持 `start-dsh.cmd [port]`、`start-dsh.cmd check`（仅环境检查）、`start-dsh.cmd hidden`（后台服务+日志重定向，无任何窗口，供 VBS 调用） |
| `E:\Work Document\启动器\start-dsh-hidden.vbs` | **无窗口启动入口**：wscript 隐藏运行 `start-dsh.cmd hidden`；路径用 `WScript.ScriptFullName` 运行时解析（文件保持纯 ASCII，避免中文路径编码问题） |
| `E:\Work Document\启动器\stop-dsh.cmd` | **停止入口**（双击）：按端口找 PID 并 taskkill；`stop-dsh.cmd [port]`、`stop-dsh.cmd hidden`（静默无 pause） |
| `E:\Work Document\启动器\stop-dsh-hidden.vbs` | **无窗口停止入口**：隐藏运行 `stop-dsh.cmd hidden` |
| `E:\Work Document\启动器\start-dsh.ps1` | 高级版（UTF-8 带 BOM）：`-Port`、`-NoBrowser`、`-Dev`（dev:web 热更新）、`-Status`（查询状态）、`-Stop`（停止占用端口的进程）、`-Check`（环境检查）、`-Force`（端口被占用时仍打开浏览器）；所有动作写入 `logs\launcher.log`（超 512KB 自动轮转为 .old） |
| `C:\Users\y1954\Desktop\启动 DeepSeek Harness.lnk` | 桌面快捷方式 → `wscript.exe start-dsh-hidden.vbs`（**无窗口**） |
| `C:\Users\y1954\Desktop\停止 DeepSeek Harness.lnk` | 桌面快捷方式 → `wscript.exe stop-dsh-hidden.vbs`（无窗口停止） |
| `E:\Work Document\启动器\logs\` | 日志目录：`launcher.log`（ps1 动作日志）+ `server-<port>.log`（hidden 模式服务输出） |

## 启动流程

1. 环境检查：node 在 PATH（`where node`，缺失即报错退出）、仓库入口 `E:\Deep Seek Harness\apps\cli\src\bin.ts`、前端产物 `apps\web\dist\index.html`（缺失仅警告）、`DSH_HOME=C:\Users\y1954\.dsh`（缺失则创建）。`start-dsh.cmd check` / `start-dsh.ps1 -Check` 只做检查不启动服务。
2. 端口探测：解析 `netstat -ano` 输出，两段式过滤：`netstat -ano | findstr /C:":<port> " | findstr /C:"LISTENING"`（cmd 版）；ps1 版用正则 `":<port>\s+\S+\s+LISTENING\s+(\d+)"` 并提取 PID。
   - **不要用 Get-NetTCPConnection**：受限权限下返回空，会把已运行的服务误判为未运行并重复拉起（实测踩坑）。
   - findstr `/C:` 字面串带尾随空格（`:3080 `）可避免把 `:23080` 之类端口误判为 `:3080`；且 `findstr /R` 不支持 `\{0,4\}` 这类范围重复，端口校验用 `^[1-9][0-9]*$` + `set /a` 溢出检测。
3. 已在运行 → 校验 HTTP 200（ps1 版；cmd 版仅提示"若页面打不开说明端口被其他程序占用"）→ 直接 `start <url>` 打开浏览器并退出。
4. 未运行 → 启动服务（工作目录 = 仓库根），两种模式：
   - **窗口模式**（默认，调试用）：`start "DSH Web :<port>" cmd /k "title DSH Web :<port> & cd /d <repo> && set DSH_HOME=<dshHome> && node --import tsx/esm apps/cli/src/bin.ts web --no-open"`，关闭窗口即停止；
   - **hidden 模式**（`start-dsh.cmd hidden`，桌面快捷方式默认）：`start "" /b cmd /c "cd /d <repo> && set DSH_HOME=<dshHome> && node ... web --no-open" > "%LOG_DIR%\server-<port>.log" 2>&1`，后台无窗口，日志落盘，停止用 `stop-dsh.cmd` / `start-dsh.ps1 -Stop`。
   - `--no-open`：浏览器由启动器统一打开，避免重复弹窗；默认 host 127.0.0.1、port 3080（web-app bundle：`ctx.webStartup.port ?? 3080`）。
5. 等待就绪：cmd 版用 `ping -n 2 127.0.0.1`（≈1 秒）+ 端口轮询，最多 90 次；ps1 版用 `Invoke-WebRequest` 轮询，且每轮检查服务窗口进程 `HasExited`——窗口被关闭则提前报错退出，避免白等。
6. 就绪后打开浏览器；关闭服务窗口（标题 `DSH Web :<port>`）即停止服务。

## 关键经验（踩坑记录）

1. **编码**：含中文的 `.ps1` 必须为 UTF-8 **带 BOM**（EF BB BF），否则 Windows PowerShell 5.1 按 ANSI/GBK 解码导致路径乱码（"启动器"→"鍚姩鍣"）或脚本无法解析。write 工具写文件时在内容开头放 U+FEFF 字符即可保证 BOM。
2. **端口检测**：`Get-NetTCPConnection` 在受限权限下返回空 → 用 `netstat` 输出解析（cmd 与 ps1 一致）。
3. **进程隔离**：服务通过 `cmd /k` 独立窗口运行，启动器退出不影响服务；误启动的重复实例（绑定失败但进程存活）要清理（`Stop-Process`）。
4. **Start-Process 传参**：用 `-WorkingDirectory` 指定工作目录，避免命令行拼接带空格/引号的路径；仓库根 `E:\Deep Seek Harness` 无空格，cmd 版 `cd /d` 无需引号。
5. **可靠性优先**：双击入口用纯 cmd（系统组件），即使 powershell.exe 异常（曾出现 0xC0000142 进程初始化失败）也能工作；ps1 仅作高级选项。
6. **edit 工具会吃掉 BOM**：对带 BOM 的 UTF-8 文件用 edit 工具修改后，BOM 会丢失（文件头从 EF BB BF 变成普通字符），Windows PowerShell 5.1 再次按 ANSI 解码 → 乱码 → 解析错误。修复：pwsh 用 `[System.IO.File]::ReadAllText($p,[Text.Encoding]::UTF8)` + `WriteAllText($p,$c,(New-Object Text.UTF8Encoding $true))` 重写 BOM，或整文件用 write 重写（内容开头放 U+FEFF）。**含中文 ps1 每次修改后必须验证头三字节为 239,187,191**。
7. **PowerShell 变量名不区分大小写**：脚本参数 `[switch]$Status` 与结果变量 `$status` 是同一个变量；给带类型约束（[switch]）的参数变量赋对象会触发 `Cannot convert ... to SwitchParameter`（InvalidCastConstructorException）。结果变量命名要避开参数名（如 `$portInfo`）。
8. **端口 LISTENING ≠ DSH 服务**：ps1 版在端口被监听时先 `Invoke-WebRequest` 校验 HTTP 200 再判定"服务已运行"；非 200 说明端口被其他程序占用，列出 PID/进程名并中止（exit 2），可用 `-Force` 忽略、`-Stop` 清理占用进程、`-Port <其它端口>` 换端口。cmd 版保持纯内建，仅提示用户排查。
9. **cmd 批处理必须纯 ASCII**：cmd 按 ANSI/GBK 解码批处理文件，文件内出现 UTF-8 中文字节（如 `set "X=E:\Work Document\启动器\logs"`）会导致解码错位、**行首字符被吞**（`REPO`→`EPO`、`rem`→`m`）、整行被当命令执行。解决：中文字面量一律不写进 .cmd，路径用 `%~dp0` 运行时拼接（如 `set "LOG_DIR=%~dp0logs"`）。行尾统一 CRLF（write 工具写 LF，需用字节级方法转 CRLF：遍历字节在 0x0A 前插入 0x0D；**不要用字符串 `.Replace` 转换行尾**，本环境实测会把 LF 替换成乱码"150"/"0"）。
10. **VBS 同样要纯 ASCII**：wscript 按 ANSI 读 .vbs，含中文路径的字符串（`E:\Work Document\启动器\...`）会乱码。用 `WScript.ScriptFullName` + `FileSystemObject.GetParentFolderName` 运行时取目录再拼接命令。
11. **沙箱限制≠启动器 bug**：DSH 服务（tsx/esbuild）用 `child_process.spawn`（stdio pipe），在受限沙箱下报 `spawn EPERM` 启动失败。这是开发沙箱的限制，真实双击环境无此问题（用 `start-dsh.ps1 -Status` 或端口探测确认）。

## 故障排查

- 查询服务状态：`start-dsh.ps1 -Status`（端口 + HTTP 校验，区分 DSH 服务与其他占用者）。
- 端口被占用：`netstat -ano | findstr :3080` 找 PID；`start-dsh.ps1 -Stop` 或桌面「停止 DeepSeek Harness」一键结束占用进程后重试。
- 前端产物缺失：仓库根运行 `pnpm run build:web`。
- hidden 模式服务异常：查看 `logs\server-<port>.log`（服务输出）；ps1 动作日志在 `logs\launcher.log`。
- 窗口模式服务报错：查看服务窗口内日志（绑定失败、依赖缺失等）。

## 关联

- 服务端口与绑定细节：`packages/bundle/web-app/cordis.patch.yml`（webserver 行）、`packages/bundle/web-app/src/startup.ts`（flag 定义）。
- 任务文件组织见 `task-output-layout`；技能维护见 `skill-library-evolution`。
