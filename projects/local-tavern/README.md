# local-tavern（本地酒馆）任务归档说明

> 归档日期：2026-08-24。本目录仅存说明文档；完整产物在本地任务文件夹。

## 背景

安卓手机上的纯文本本地离线 AI 聊天 App：以角色卡为核心组织会话，llama.cpp（GGUF）本地推理，数据不出手机。个人自用（24GB 内存旗舰机，安卓优先）。

## 交付物（本地位置）

- APK：`E:\Work Document\Temp Work Document\DeepSeek本地聊天App\OutPut\本地酒馆-v0.1.0.apk`（已签名，14.4MB，arm64-v8a）
- 需求基线：`...\DeepSeek本地聊天App\OutPut\功能需求文档-PRD.md`
- 问答纪要：`...\DeepSeek本地聊天App\Temp Document\需求问答纪要.md`
- M2 设计文档：`...\DeepSeek本地聊天App\Temp Document\design\`（角色卡格式/摘要导出/模型下载器）
- 调研备忘录：`...\DeepSeek本地聊天App\Temp Document\research\`（llama.cpp 集成路线、GGUF 模型链接表）
- 工程源码：`E:\Work Document\AndroidBuild\LocalTavern`（纯 Gradle 命令行构建）
- llama.cpp 源码：`E:\Work Document\AndroidBuild\llama.cpp`

## 关键经验（已沉淀技能）

- `llama-cpp-android`（knowledge/ 同步）：安卓 llama.cpp 集成全链路——无预编译 AAR 必须源码编译、CDN 弱网拉源码、NDK/CMake 参数、JNI 流式 UTF-8 分片、common→llama-common 等 8 条实战坑。
- `pwa-android-apk` v1.3 更新：PowerShell 5.1 脚本六坑 + GitHub 弱网 CDN 逐文件并行下载方案。

## 复用要点（后续 M2 开发前提）

1. 构建：`E:\Work Document\AndroidBuild\LocalTavern` 下 `gradlew.bat assembleRelease`；签名 keystore 在 `E:\Work Document\AndroidBuild\localtavern.keystore`（alias/pass=localtavern）。
2. 模型：hf-mirror 直链（DeepSeek-R1-Distill-Qwen 1.5B/7B/14B Q4_K_M），App 内 SAF 导入。
3. 数据：纯本地；M1 无多轮 KV 记忆（每轮重拼 ChatML prompt），M2 按 design 文档实现角色卡/多会话/摘要继承/下载器。
