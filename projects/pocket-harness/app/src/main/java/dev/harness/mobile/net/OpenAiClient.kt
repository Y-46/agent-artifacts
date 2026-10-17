package dev.harness.mobile.net

import dev.harness.mobile.data.Message
import dev.harness.mobile.data.Role
import dev.harness.mobile.data.ToolCall
import dev.harness.mobile.data.appJson
import dev.harness.mobile.data.newId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class ReasoningDelta(val text: String) : StreamEvent
    data class ToolCalls(val calls: List<ToolCall>) : StreamEvent
    data class Completed(val finishReason: String?) : StreamEvent
    data class Failed(val message: String) : StreamEvent
}

/**
 * 极简 OpenAI 兼容流式客户端：`/chat/completions` + SSE + function calling。
 * 兼容 DeepSeek / OpenAI / Kimi / 自建 vLLM / Ollama 的 OpenAI 端点。
 */
class OpenAiClient(private val baseHttp: OkHttpClient) {

    fun stream(
        baseUrl: String,
        apiKey: String,
        model: String,
        temperature: Double,
        maxTokens: Int,
        messages: List<Message>,
        tools: JsonArray?
    ): Flow<StreamEvent> = flow {
        val request = Request.Builder()
            .url(endpoint(baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(payload(model, temperature, maxTokens, messages, tools).toString().toRequestBody(JSON_MEDIA))
            .build()

        val client = baseHttp.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        val call = client.newCall(request)
        val onCancel = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    emit(StreamEvent.Failed("HTTP ${response.code} ${response.message}\n${body.take(1200)}"))
                    return@use
                }
                val contentType = response.header("Content-Type").orEmpty()
                val source = response.body?.source()
                if (source == null) {
                    emit(StreamEvent.Failed("响应体为空"))
                    return@use
                }
                if (!contentType.contains("event-stream", ignoreCase = true)) {
                    // 有些网关忽略 stream=true，直接吐整包 JSON，这里兜底解析。
                    for (event in parseFullBody(source.readUtf8())) emit(event)
                    return@use
                }
                val builders = LinkedHashMap<Int, CallBuilder>()
                var finish: String? = null
                while (currentCoroutineContext().isActive) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty() || !line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    val chunk = runCatching { appJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                    val choice = chunk["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue
                    finishReason(choice)?.let { finish = it }
                    val delta = choice["delta"]?.jsonObject ?: continue
                    val text = delta.string("content")
                    if (!text.isNullOrEmpty()) emit(StreamEvent.TextDelta(text))
                    val reasoning = delta.string("reasoning_content") ?: delta.string("reasoning")
                    if (!reasoning.isNullOrEmpty()) emit(StreamEvent.ReasoningDelta(reasoning))
                    delta["tool_calls"]?.jsonArray?.forEach { element ->
                        val toolCall = element.jsonObject
                        val index = toolCall["index"]?.let {
                            runCatching { it.jsonPrimitive.content.toInt() }.getOrNull()
                        } ?: 0
                        val builder = builders.getOrPut(index) { CallBuilder() }
                        toolCall.string("id")?.takeIf { it.isNotBlank() }?.let { id ->
                            if (builder.id.isEmpty()) builder.id = id
                        }
                        val function = toolCall["function"]?.jsonObject
                        function?.string("name")?.takeIf { it.isNotBlank() }?.let { builder.name = it }
                        function?.string("arguments")?.let { builder.arguments.append(it) }
                    }
                }
                if (builders.isNotEmpty()) {
                    val calls = builders.entries.sortedBy { it.key }.map { entry ->
                        val builder = entry.value
                        ToolCall(
                            id = builder.id.ifBlank { "call_" + newId().take(8) },
                            name = builder.name,
                            arguments = builder.arguments.toString().ifBlank { "{}" }
                        )
                    }
                    emit(StreamEvent.ToolCalls(calls))
                }
                emit(StreamEvent.Completed(finish))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (io: java.io.IOException) {
            if (currentCoroutineContext().isActive) {
                emit(StreamEvent.Failed(io.message ?: "网络连接失败"))
            }
        } catch (other: Exception) {
            emit(StreamEvent.Failed(other.message ?: other.javaClass.simpleName))
        } finally {
            onCancel?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    private fun endpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return when {
            trimmed.isEmpty() -> "https://api.openai.com/v1/chat/completions"
            trimmed.endsWith("/chat/completions") -> trimmed
            else -> "$trimmed/chat/completions"
        }
    }

    private fun payload(
        model: String,
        temperature: Double,
        maxTokens: Int,
        messages: List<Message>,
        tools: JsonArray?
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("stream", true)
        put("temperature", temperature)
        put("max_tokens", maxTokens)
        put("messages", buildJsonArray {
            for (message in messages) {
                add(
                    when (message.role) {
                        Role.SYSTEM -> buildJsonObject {
                            put("role", "system")
                            put("content", message.content)
                        }

                        Role.USER -> buildJsonObject {
                            put("role", "user")
                            put("content", message.content)
                        }

                        Role.TOOL -> buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", message.toolCallId ?: "")
                            put("content", message.content)
                        }

                        Role.ASSISTANT -> buildJsonObject {
                            put("role", "assistant")
                            put("content", message.content)
                            if (message.toolCalls.isNotEmpty()) {
                                put("tool_calls", buildJsonArray {
                                    for (call in message.toolCalls) {
                                        add(buildJsonObject {
                                            put("id", call.id)
                                            put("type", "function")
                                            put("function", buildJsonObject {
                                                put("name", call.name)
                                                put("arguments", call.arguments.ifBlank { "{}" })
                                            })
                                        })
                                    }
                                })
                            }
                        }
                    }
                )
            }
        })
        if (tools != null && tools.isNotEmpty()) put("tools", tools)
    }

    private fun parseFullBody(text: String): List<StreamEvent> {
        val chunk = runCatching { appJson.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return listOf(StreamEvent.Failed("无法解析响应：${text.take(600)}"))
        val choice = chunk["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return listOf(StreamEvent.Failed("响应里没有 choices：${text.take(600)}"))
        val events = mutableListOf<StreamEvent>()
        val message = choice["message"]?.jsonObject
        val content = message?.string("content").orEmpty()
        if (content.isNotEmpty()) events.add(StreamEvent.TextDelta(content))
        val reasoning = message?.string("reasoning_content").orEmpty()
        if (reasoning.isNotEmpty()) events.add(StreamEvent.ReasoningDelta(reasoning))
        val calls = message?.get("tool_calls")?.jsonArray?.mapNotNull { element ->
            val toolCall = element.jsonObject
            val function = toolCall["function"]?.jsonObject ?: return@mapNotNull null
            val name = function.string("name") ?: return@mapNotNull null
            ToolCall(
                id = toolCall.string("id") ?: ("call_" + newId().take(8)),
                name = name,
                arguments = function.string("arguments") ?: "{}"
            )
        }.orEmpty()
        if (calls.isNotEmpty()) events.add(StreamEvent.ToolCalls(calls))
        events.add(StreamEvent.Completed(finishReason(choice)))
        return events
    }

    private fun finishReason(choice: JsonObject): String? =
        (choice["finish_reason"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private class CallBuilder {
        var id: String = ""
        var name: String = ""
        val arguments: StringBuilder = StringBuilder()
    }
}