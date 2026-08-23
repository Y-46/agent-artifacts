# 小Y — 安卓本地 AI 聊天 App

完全本地运行的安卓 AI 聊天软件：llama.cpp 推理引擎编译进 APK，模型跑在手机本地（隐私优先），联网仅用于下载模型。

## 背景

用户需要一个本地 AI 聊天软件，需求访谈后确定为：安卓手机（16GB+ 内存）、基本离线（仅下载模型联网）、
Kotlin + Compose + llama.cpp（NDK 交叉编译）、内置 1.5B 模型 + 可下载 7B/14B + 知识库 RAG。

## 用法

- APK 安装后首次启动自动安装内置 1.5B 模型（约 1.1GB）
- 「模型」页下载/切换模型（断点续传）；「知识库」页导入 TXT/Markdown 并开启问答开关
- 聊天支持流式输出、停止生成、Markdown 渲染、多会话历史

## 前提

- 构建需 JDK17 + Android SDK（NDK 27.2.12479018 + CMake 3.22.1）
- llama.cpp 源码树（工程外引用，CMake add_subdirectory）
- 签名密钥 xiaoy.keystore

## 完整交付

- APK + 源码工程：`E:\Work Document\AndroidBuild\XiaoY\`
- 交付文档：`E:\Work Document\Temp Work Document\安卓本地AI聊天App\README.md`
- 需求分析：`E:\Work Document\Temp Work Document\安卓本地AI聊天App\Temp Document\需求分析.md`

## 关键踩坑（详见技术要点.md）

DSH 沙箱下 CMake/编译器管道挂起、Windows schannel TLS 故障、mikepenz 0.24 API 无默认参数、
Kotlin 局部函数不能调用 @Composable、KDoc 嵌套注释陷阱等。
