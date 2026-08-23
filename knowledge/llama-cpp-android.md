---
name: llama-cpp-android
description: 在安卓原生 App（无 Android Studio、纯 Gradle 命令行）中集成 llama.cpp 本地离线推理：源码获取、NDK/CMake 交叉编译、JNI 流式封装（逐 token + 停止）、GGUF 模型接入与 APK 验证。做手机本地 LLM 聊天/推理应用时用。
---

# llama.cpp 安卓本地推理集成

## 触发条件

- 安卓 App 要在手机本地跑 LLM（GGUF 模型、离线推理、隐私优先）；
- 需要把 llama.cpp 编进 APK 并通过 JNI 调用；
- 无 Android Studio，纯 Gradle 命令行构建。

## 核心结论（先读）

1. **不存在可用的预编译官方 AAR**：Maven Central 只有桌面版 `io.gravitee.llama.cpp:llamaj.cpp`（JavaCPP，非安卓）；weaktoogeek/llama.cpp-android-java 仓库**不自带 .so/AAR**（需另 clone llama.cpp + 改其 CMakeLists 硬编码的 macOS 绝对路径）；官方仅 `examples/llama.android` 源码示例。→ **必须源码编译**，无捷径。
2. **源码编译产物是 6 个 .so 依赖链**：自写 JNI 库 + `libllama.so` + `libllama-common.so` + `libggml.so` + `libggml-cpu.so` + `libggml-base.so`，APK 打包必须齐全（缺依赖 → 装到手机 `UnsatisfiedLinkError`）。APK 总增约 10MB。
3. **新版 llama.cpp 关键差异**：common 目标已改名 `llama-common`（链接 `common` 会变成 `-lcommon` 链接失败）；`common` 不传播 include 目录需显式指定；ggml 在 `ggml/` 目录（树内，非子模块），`vendor/` 目录也是必须的；`LLAMA_LLGUIDANCE`（Rust）默认 OFF 无需 Rust 工具链；无 `.git` 也能配置（build-info 有兜底）。

## 标准流程

### 1. 环境（一次性）

```powershell
sdkmanager "ndk;27.2.12479018" "cmake;3.22.1" "cmake;3.30.5"   # AGP 8.9.1 兼容 NDK r27c
```

- JDK17（`JAVA_HOME`）、Android SDK、AGP 8.9.1 + Gradle 8.11.1（腾讯镜像）——复用 `pwa-android-apk` 技能的构建环境。
- 工程路径**纯 ASCII**；`add_subdirectory` 路径**含空格必须加引号**（否则 "incorrect number of arguments"）。

### 2. 获取 llama.cpp 源码（本机弱网方案）

- **首选 CDN 逐文件**：GitHub API tree 拿文件清单（`https://api.github.com/repos/ggml-org/llama.cpp/git/trees/master?recursive=1`）→ 过滤 `src/ include/ common/ cmake/ ggml/ vendor/`（**ggml 与 vendor 必须包含**，ggml 有 1300+ 文件）→ jsdelivr CDN 并行下载（8 路，脚本见 `scripts/fetch-repo-cdn.ps1`）。全量约 1600 文件 / 30MB，几分钟完成。
- 备选：`https://codeload.github.com/ggml-org/llama.cpp/tar.gz/refs/heads/master` + `curl -L -C -` 断点续传（本机 ~20KB/s，很慢）；gitee 镜像本机要求登录不可用。
- 下载后抽查关键文件（`src/llama.cpp`、`include/llama.h`、`common/common.h`、`ggml/CMakeLists.txt`）。

### 3. CMake 配置（app 模块）

`app/build.gradle`：

```groovy
android {
    ndkVersion "27.2.12479018"
    defaultConfig {
        ndk { abiFilters += ["arm64-v8a"] }
        externalNativeBuild {
            cmake {
                arguments "-DLLAMA_CURL=OFF", "-DLLAMA_BUILD_COMMON=ON",
                        "-DLLAMA_BUILD_EXAMPLES=OFF", "-DLLAMA_BUILD_TESTS=OFF",
                        "-DLLAMA_BUILD_SERVER=OFF", "-DLLAMA_BUILD_TOOLS=OFF",
                        "-DLLAMA_BUILD_BENCHMARKS=OFF",
                        "-DGGML_LLAMAFILE=OFF", "-DGGML_OPENMP=OFF",
                        "-DGGML_NATIVE=OFF",           // 交叉编译必须 OFF（禁 -march=native）
                        "-DGGML_CPU_ALL_VARIANTS=OFF", // 大幅加速
                        "-DGGML_BUILD_TESTS=OFF", "-DGGML_BUILD_EXAMPLES=OFF",
                        "-DGGML_HEXAGON=OFF", "-DGGML_METAL=OFF",
                        "-DGGML_CUDA=OFF", "-DGGML_VULKAN=OFF",
                        "-DCMAKE_BUILD_TYPE=Release"
            }
        }
    }
    externalNativeBuild {
        cmake { path "src/main/cpp/CMakeLists.txt"; version "3.22.1" }
    }
}
```

`app/src/main/cpp/CMakeLists.txt`：

```cmake
cmake_minimum_required(VERSION 3.22.1)
project("llama-android")
add_subdirectory("E:/your/path/llama.cpp" build-llama)   # 路径加引号！
add_library(llama-android SHARED llama-android.cpp)
target_link_libraries(llama-android llama llama-common android log)  # 新名字 llama-common
target_include_directories(llama-android PRIVATE
    "E:/your/path/llama.cpp/common"
    "E:/your/path/llama.cpp/include"
    "E:/your/path/llama.cpp/ggml/include")
```

### 4. JNI 层（C++）

参考实现：weaktogeek/llama.cpp-android-java 的 `llama-android.cpp`（Apache 2.0，可直接改编）。要点：

- **流式**：`completion_init`（`common_tokenize` + 首轮 `llama_decode`，返回 ncur=已用 token 数）→ 循环 `completion_loop`（`llama_sampler_sample` 单 token → EOG 判断 → 转文本 → `llama_decode` 下一轮）。
- **中文关键坑**：token 转字符串可能落在 UTF-8 字符中间（半个汉字），必须做**分片缓存拼接**（`is_valid_utf8` 校验不通过就缓存、下次拼上再返回，否则流式输出中文乱码）。
- **停止生成**：native 无需支持，Java 侧 stop 标志在每轮 `completion_loop` 返回后检查即可。
- **长度语义坑**：`n_len` 是「prompt+输出总 token 上限」而非输出上限（prompt 2000 token 时 n_len=1024 只出 0 token）；建议 n_len=2048、n_ctx=8192。
- 参数：`llama_context_params.n_ctx=8192`、`n_threads=max(1,min(8,cores-2))`、batch 512、采样器从 greedy 起步（温度后续加 `llama_sampler_init_temp`）。
- **KV cache 策略（MVP）**：每轮生成结束 `llama_memory_clear(llama_get_memory(ctx), true)` 清空，历史靠**完整重拼 prompt**（无多轮记忆但实现简单；长对话每轮变慢，进阶再做 cache 保留）。
- JNI 函数名跟随 Java 类包名（`Java_<包_类>_<方法>`，方法名中的 `_` 转 `_1`）；换包名时全量替换前缀即可。

### 5. Java 引擎封装

- 所有原生调用在**单 worker 线程**（llama.cpp 非线程安全）；回调经主线程 Handler 派发（UI 可直接更新）。
- 接口：`init(modelPath)`（异步加载）/ `chat(prompt, cb)` / `stop()` / `release()` / `isReady()`；`cb = onToken(String) / onDone(fullText) / onError(msg)`。
- **prompt 格式（DeepSeek-R1-Distill-Qwen 系列）**：ChatML，`<|im_start|>system\n…<|im_end|>\n<|im_start|>user\n…<|im_end|>\n…<|im_start|>assistant\n`；`common_tokenize` 的 `parse_special` 传 **true**（否则 im_start 等特殊 token 不解析）。
- R1 蒸馏模型会输出 `<think>…</think>` 思考块，MVP 原样展示即可。

### 6. 构建、验证与签名

- `gradlew assembleDebug` 首次 5~30 分钟（llama.cpp 全量交叉编译），增量秒级。
- 验证：`aapt2 dump badging` 看 `native-code: 'arm64-v8a'`；解包 APK 确认 **6 个 .so 齐全**。
- 签名：keytool 生成 keystore（注意用 `$env:JAVA_HOME\bin\keytool.exe`）+ `apksigner sign/verify`（流程复用 `pwa-android-apk` 技能）。
- 可选优化：首次构建成功后，把 intermediates 里 6 个 .so 拷到 `app/src/main/jniLibs/arm64-v8a/` 并去掉 `externalNativeBuild` → 后续构建回到纯 Gradle（不再依赖 NDK）。

## 模型（GGUF）

| 档位 | 文件（Q4_K_M） | 大小 | hf-mirror 链接前缀 |
| --- | --- | --- | --- |
| 1.5B | DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf | ~1.1GB | `https://hf-mirror.com/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/` |
| 7B | DeepSeek-R1-Distill-Qwen-7B-Q4_K_M.gguf | ~4.7GB | `https://hf-mirror.com/unsloth/DeepSeek-R1-Distill-Qwen-7B-GGUF/resolve/main/` |
| 14B | DeepSeek-R1-Distill-Qwen-14B-Q4_K_M.gguf | ~9GB | `https://hf-mirror.com/unsloth/DeepSeek-R1-Distill-Qwen-14B-GGUF/resolve/main/` |

全部 MIT；24GB 内存手机 7B 流畅（10~20 t/s）、14B <10 t/s。下载命令 `curl -L -C - -o 文件名 "<链接>"` 支持断点续传。R1-Distill 无 3B 档（官方只有 1.5/7/8/14/32/70B）。

## 踩坑记录（全部实战验证）

1. `add_subdirectory` 路径含空格不加引号 → "incorrect number of arguments"。
2. 链接 `common` → `-lcommon` 找不到 → 新版叫 `llama-common`。
3. `common.h` 找不到 → common 目标不传播 include 目录，显式加 3 条 `target_include_directories`。
4. 源码过滤漏掉 `ggml/`、`vendor/` → 配置/编译失败；GitHub tree 里 ggml 是普通 tree 条目（非子模块）。
5. `GGML_NATIVE` 不关 → 交叉编译报 `-march=native` 相关错误。
6. 主 .so 体积 0.05~0.2MB 是正常的（动态链 6 个库），别以为没编进去——务必核对 APK 内 6 个 .so。
7. 中文流式乱码 → UTF-8 分片缓存拼接（见 §4）。
8. 无 .git 源码树可正常配置（common/CMakeLists 对 build-info 有兜底），`fatal: not a git repository` 只是无害告警。

## 已落地参考工程

`E:\Work Document\AndroidBuild\LocalTavern`（本地酒馆 M1：JNI 改名 `Java_com_localtavern_app_llm_LlamaNative_*`、n_ctx=8192、n_len=2048、停止生成、模拟模式兜底、SAF 模型导入的完整改造样例）。

## 关联

- 构建环境/镜像/签名/APK 安装 → `pwa-android-apk`
- 弱网获取 GitHub 源码的通用脚本 → 本技能 `scripts/fetch-repo-cdn.ps1`
