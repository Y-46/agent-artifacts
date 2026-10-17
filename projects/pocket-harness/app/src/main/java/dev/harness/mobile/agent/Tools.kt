package dev.harness.mobile.agent

import dev.harness.mobile.data.JobRepository
import dev.harness.mobile.data.Paths
import dev.harness.mobile.net.WebClient
import dev.harness.mobile.util.fmtSize
import dev.harness.mobile.util.fmtTime
import dev.harness.mobile.util.snippet
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val PATH_HINT =
    "路径规则：相对路径或 workspace/ 前缀 = App 专属工作区；device/ 前缀 = 用户授权的手机真实文件夹。"

class ToolSpec(
    val name: String,
    val description: String,
    val schema: JsonObject,
    val mutating: Boolean = false,
    val run: suspend (JsonObject) -> String
)

/**
 * 与桌面版 harness 对齐的最小工具集：文件读写/搜索、网页搜索/抓取、任务查询。
 */
class ToolRegistry(
    private val paths: Paths,
    private val web: WebClient,
    private val jobs: JobRepository
) {

    private val registry: List<ToolSpec> = listOf(
        ToolSpec(
            name = "fs_list",
            description = "列出目录内容。path 省略时列出工作区根目录。$PATH_HINT",
            schema = schema(
                required = emptyList(),
                props = listOf(prop("path", "string", "要列出的目录路径，可省略"))
            )
        ) { args ->
            guarded {
                val path = args.text("path") ?: ""
                val entries = paths.list(path)
                if (entries.isEmpty()) {
                    "目录为空：${path.ifBlank { "workspace" }}"
                } else {
                    buildString {
                        appendLine("${path.ifBlank { "workspace" }} 共 ${entries.size} 项：")
                        entries.take(300).forEach { entry ->
                            append(if (entry.isDir) "📁 " else "📄 ")
                            append(entry.name)
                            if (!entry.isDir) append("  ${fmtSize(entry.size)}")
                            appendLine()
                        }
                        if (entries.size > 300) append("… 另有 ${entries.size - 300} 项")
                    }
                }
            }
        },
        ToolSpec(
            name = "fs_read",
            description = "读取一个文本文件的内容。$PATH_HINT",
            schema = schema(
                required = listOf("path"),
                props = listOf(
                    prop("path", "string", "文件路径，例如 src/main.py 或 device/Download/a.txt"),
                    prop("max_chars", "integer", "最多读取多少字符，默认 20000")
                )
            )
        ) { args ->
            guarded {
                val path = args.text("path") ?: throw IllegalArgumentException("缺少 path")
                val limit = args.number("max_chars", 20000).coerceIn(1000, 200_000)
                paths.read(path, limit)
            }
        },
        ToolSpec(
            name = "fs_write",
            description = "把内容整体写入一个文件（覆盖写，父目录不存在会自动创建）。新建或修改代码/文档都用它。$PATH_HINT",
            schema = schema(
                required = listOf("path", "content"),
                props = listOf(
                    prop("path", "string", "目标文件路径"),
                    prop("content", "string", "完整文件内容")
                )
            ),
            mutating = true
        ) { args ->
            guarded {
                val path = args.text("path") ?: throw IllegalArgumentException("缺少 path")
                val content = args.text("content") ?: ""
                paths.write(path, content)
                "已写入 $path（${content.length} 字符，约 ${fmtSize(content.toByteArray(Charsets.UTF_8).size.toLong())}）"
            }
        },
        ToolSpec(
            name = "fs_mkdir",
            description = "创建目录（已存在则忽略）。$PATH_HINT",
            schema = schema(
                required = listOf("path"),
                props = listOf(prop("path", "string", "目录路径"))
            ),
            mutating = true
        ) { args ->
            guarded {
                val path = args.text("path") ?: throw IllegalArgumentException("缺少 path")
                paths.mkdir(path)
                "已创建目录 $path"
            }
        },
        ToolSpec(
            name = "fs_delete",
            description = "删除文件或目录（目录会递归删除，不可恢复）。$PATH_HINT",
            schema = schema(
                required = listOf("path"),
                props = listOf(prop("path", "string", "要删除的路径"))
            ),
            mutating = true
        ) { args ->
            guarded {
                val path = args.text("path") ?: throw IllegalArgumentException("缺少 path")
                paths.delete(path)
                "已删除 $path"
            }
        },
        ToolSpec(
            name = "fs_search",
            description = "在文件内容里按关键字搜索（不区分大小写），返回 路径:行号: 内容。$PATH_HINT",
            schema = schema(
                required = listOf("query"),
                props = listOf(
                    prop("query", "string", "要搜索的字面文本"),
                    prop("path", "string", "搜索范围，省略则在所选根目录下整体搜索")
                )
            )
        ) { args ->
            guarded {
                val query = args.text("query") ?: throw IllegalArgumentException("缺少 query")
                val path = args.text("path") ?: ""
                val hits = paths.search(query, path)
                if (hits.isEmpty()) {
                    "没有找到匹配：$query"
                } else {
                    buildString {
                        appendLine("匹配 ${hits.size} 处（$query）：")
                        hits.forEach { hit -> appendLine("${hit.path}:${hit.line}: ${hit.text}") }
                    }
                }
            }
        },
        ToolSpec(
            name = "web_search",
            description = "用 DuckDuckGo 搜索网页，返回标题、链接和摘要。需要正文时再用 web_fetch 抓取。",
            schema = schema(
                required = listOf("query"),
                props = listOf(prop("query", "string", "搜索关键词"))
            )
        ) { args ->
            guarded {
                val query = args.text("query") ?: throw IllegalArgumentException("缺少 query")
                val results = web.search(query, 8)
                if (results.isEmpty()) {
                    "没有搜到结果，换个关键词试试。"
                } else {
                    buildString {
                        appendLine("搜索结果（$query）：")
                        results.forEachIndexed { index, result ->
                            appendLine("${index + 1}. ${result.title}")
                            appendLine("   ${result.url}")
                            if (result.snippet.isNotBlank()) appendLine("   ${result.snippet}")
                        }
                        append("需要细节就用 web_fetch 抓最相关的链接。")
                    }
                }
            }
        },
        ToolSpec(
            name = "web_fetch",
            description = "抓取一个网页并转成纯文本（去掉脚本和标签），适合读文档、README、文章。",
            schema = schema(
                required = listOf("url"),
                props = listOf(
                    prop("url", "string", "完整 URL，可省略 https://"),
                    prop("max_chars", "integer", "最多返回多少字符，默认 12000")
                )
            )
        ) { args ->
            guarded {
                val url = args.text("url") ?: throw IllegalArgumentException("缺少 url")
                val limit = args.number("max_chars", 12000).coerceIn(1000, 100_000)
                web.fetch(url, limit)
            }
        },
        ToolSpec(
            name = "jobs_list",
            description = "查看本 App 最近的后台任务（每次对话轮次就是一个任务）及其状态，用于回答\"跑到哪了\"。",
            schema = schema(required = emptyList(), props = emptyList())
        ) { _ ->
            guarded {
                val recent = jobs.jobs.value.take(10)
                if (recent.isEmpty()) {
                    "还没有任务记录。"
                } else {
                    buildString {
                        recent.forEach { job ->
                            append("- [${job.status.name}] ${job.title} · ${fmtTime(job.startedAt)}")
                            val ended = job.endedAt
                            if (ended != null) append(" · 耗时 ${(ended - job.startedAt) / 1000}s")
                            appendLine()
                        }
                    }
                }
            }
        }
    )

    private val byName: Map<String, ToolSpec> = registry.associateBy { it.name }

    fun find(name: String): ToolSpec? = byName[name]

    fun names(): List<String> = registry.map { it.name }

    /** 转成 OpenAI function-calling 的 tools 数组。 */
    fun payload(): JsonArray = buildJsonArray {
        registry.forEach { tool ->
            add(
                buildJsonObject {
                    put("type", "function")
                    put(
                        "function",
                        buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.schema)
                        }
                    )
                }
            )
        }
    }

    private fun guarded(block: () -> String): String = try {
        block().ifBlank { "（执行完成，无输出）" }
    } catch (e: Exception) {
        "错误：${e.message ?: e.javaClass.simpleName}"
    }
}

private fun prop(name: String, type: String, description: String): Triple<String, String, String> =
    Triple(name, type, description)

private fun schema(
    required: List<String>,
    props: List<Triple<String, String, String>>
): JsonObject = buildJsonObject {
    put("type", "object")
    put(
        "properties",
        buildJsonObject {
            props.forEach { (name, type, description) ->
                put(
                    name,
                    buildJsonObject {
                        put("type", type)
                        put("description", description)
                    }
                )
            }
        }
    )
    if (required.isNotEmpty()) {
        put("required", buildJsonArray { required.forEach { add(it) } })
    }
}

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

private fun JsonObject.number(key: String, fallback: Int): Int =
    text(key)?.trim()?.toDoubleOrNull()?.toInt() ?: fallback