package dev.harness.mobile.agent

import dev.harness.mobile.core.AppContainer
import dev.harness.mobile.core.ApprovalMode
import dev.harness.mobile.core.Settings
import dev.harness.mobile.data.ApprovalRequest
import dev.harness.mobile.data.JobStatus
import dev.harness.mobile.data.Message
import dev.harness.mobile.data.Role
import dev.harness.mobile.data.RunState
import dev.harness.mobile.data.ToolCall
import dev.harness.mobile.data.appJson
import dev.harness.mobile.net.StreamEvent
import dev.harness.mobile.service.AgentService
import dev.harness.mobile.util.snippet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Agent 主循环：请求模型 -> 流式输出 -> 解析工具调用 -> 审批 -> 执行工具 -> 继续。
 * 循环跑在独立 IO 作用域里，配合前台服务，App 切到后台也不会断。
 */
class AgentRunner(private val c: AppContainer) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    private var activeJob: Job? = null

    val isRunning: Boolean get() = activeJob?.isActive == true

    fun start(sessionId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isRunning) return
        activeJob = scope.launch { runTurn(sessionId, trimmed) }
    }

    fun stop() {
        activeJob?.cancel()
        c.approvals.cancelAll()
    }

    private suspend fun runTurn(sessionId: String, userText: String) {
        val settings = c.prefs.value
        val apiKey = c.prefs.apiKey()
        val job = c.jobs.start(sessionId, userText)
        _state.value = RunState(running = true, jobId = job.id, sessionId = sessionId, phase = "准备中")
        AgentService.start(c.context)
        var status = JobStatus.DONE
        try {
            if (apiKey.isBlank()) {
                throw IllegalStateException("还没有配置 API Key，请到「设置」填写 Base URL、模型名和 Key。")
            }
            if (settings.baseUrl.isBlank() || settings.model.isBlank()) {
                throw IllegalStateException("模型接口没配全：Base URL 或模型名为空。")
            }
            c.jobs.log(job.id, "会话开始：${snippet(userText, 80)}")
            c.sessions.append(sessionId, Message(role = Role.USER, content = userText), persist = true)
            c.sessions.setTitleIfDefault(sessionId, userText)

            val maxRounds = settings.maxIterations.coerceIn(1, 40)
            for (round in 1..maxRounds) {
                currentCoroutineContext().ensureActive()
                _state.value = _state.value.copy(phase = "思考中 · 第 $round 轮")
                c.jobs.log(job.id, "第 $round 轮：请求 ${settings.model}")

                val placeholder = Message(role = Role.ASSISTANT)
                c.sessions.append(sessionId, placeholder)
                val calls = streamRound(sessionId, placeholder.id, settings, apiKey, job.id)

                if (calls.isEmpty()) {
                    c.jobs.log(job.id, "回答完成（第 $round 轮）")
                    break
                }
                c.jobs.log(job.id, "模型请求 ${calls.size} 个工具调用")
                for (call in calls) {
                    currentCoroutineContext().ensureActive()
                    executeTool(sessionId, job.id, call)
                }
            }
        } catch (cancelled: CancellationException) {
            status = JobStatus.CANCELED
            c.jobs.log(job.id, "任务已取消", "warn")
        } catch (e: Exception) {
            status = JobStatus.FAILED
            val message = e.message ?: e.javaClass.simpleName
            c.jobs.log(job.id, "失败：$message", "error")
            c.sessions.append(
                sessionId,
                Message(role = Role.ASSISTANT, content = "❌ 运行失败：$message", failed = true),
                persist = true
            )
        } finally {
            c.approvals.cancelAll()
            c.sessions.dropTrailingEmptyAssistant(sessionId)
            c.sessions.persist(sessionId)
            c.jobs.finish(job.id, status)
            _state.value = RunState()
            AgentService.stop(c.context)
        }
    }

    private suspend fun streamRound(
        sessionId: String,
        placeholderId: String,
        settings: Settings,
        apiKey: String,
        jobId: String
    ): List<ToolCall> {
        val text = StringBuilder()
        val reasoning = StringBuilder()
        var calls: List<ToolCall> = emptyList()
        var failure: String? = null

        val history = history(sessionId, settings.systemPrompt)
        c.llm.stream(
            baseUrl = settings.baseUrl,
            apiKey = apiKey,
            model = settings.model,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            messages = history,
            tools = c.tools.payload()
        ).collect { event ->
            when (event) {
                is StreamEvent.TextDelta -> {
                    text.append(event.text)
                    c.sessions.patch(sessionId, placeholderId) {
                        it.copy(content = text.toString(), reasoning = reasoning.toString())
                    }
                }

                is StreamEvent.ReasoningDelta -> {
                    reasoning.append(event.text)
                    c.sessions.patch(sessionId, placeholderId) {
                        it.copy(reasoning = reasoning.toString())
                    }
                }

                is StreamEvent.ToolCalls -> calls = event.calls

                is StreamEvent.Failed -> failure = event.message

                is StreamEvent.Completed -> Unit
            }
        }

        val error = failure
        if (error != null && text.isEmpty() && calls.isEmpty()) {
            throw IllegalStateException(error)
        }
        c.sessions.patch(sessionId, placeholderId, persist = true) {
            it.copy(content = text.toString(), reasoning = reasoning.toString(), toolCalls = calls)
        }
        return calls
    }

    private suspend fun executeTool(sessionId: String, jobId: String, call: ToolCall) {
        val spec = c.tools.find(call.name)
        if (spec == null) {
            c.jobs.log(jobId, "未知工具：${call.name}", "error")
            c.sessions.append(
                sessionId,
                Message(
                    role = Role.TOOL,
                    toolCallId = call.id,
                    toolName = call.name,
                    content = "错误：没有名为 ${call.name} 的工具。可用工具：${c.tools.names().joinToString()}"
                ),
                persist = true
            )
            return
        }

        val arguments = runCatching {
            appJson.parseToJsonElement(call.arguments.ifBlank { "{}" }).jsonObject
        }.getOrElse { buildJsonObject { } }

        val needApproval = when (c.prefs.value.approvalMode) {
            ApprovalMode.AUTO -> false
            ApprovalMode.SMART -> spec.mutating
            ApprovalMode.ALWAYS_ASK -> true
        }

        if (needApproval) {
            _state.value = _state.value.copy(phase = "等待确认：${spec.name}")
            c.jobs.log(jobId, "等待用户授权：${spec.name}", "warn")
            val approved = c.approvals.request(
                ApprovalRequest(
                    sessionId = sessionId,
                    toolName = spec.name,
                    arguments = call.arguments,
                    mutating = spec.mutating
                )
            )
            if (!approved) {
                c.jobs.log(jobId, "用户拒绝：${spec.name}", "warn")
                c.sessions.append(
                    sessionId,
                    Message(
                        role = Role.TOOL,
                        toolCallId = call.id,
                        toolName = spec.name,
                        content = "用户拒绝了这次工具调用。请先用一两句话说明为什么需要这么做，或者换一种不需要授权的方式继续。"
                    ),
                    persist = true
                )
                return
            }
        }

        _state.value = _state.value.copy(phase = "执行中：${spec.name}")
        c.jobs.log(jobId, "执行 ${spec.name}(${snippet(call.arguments, 160)})")

        val startedAt = System.currentTimeMillis()
        val result = try {
            spec.run(arguments)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            "错误：${e.message ?: e.javaClass.simpleName}"
        }
        val cost = System.currentTimeMillis() - startedAt
        val limit = c.prefs.value.outputCharLimit.coerceIn(2000, 200_000)
        val clipped = if (result.length > limit) result.take(limit) + "\n…[工具输出过长，已截断]" else result
        val failed = result.startsWith("错误")
        c.jobs.log(
            jobId,
            "完成 ${spec.name} · ${cost}ms · ${result.length} 字符" + if (failed) " ❌" else "",
            if (failed) "error" else "info"
        )
        c.sessions.append(
            sessionId,
            Message(role = Role.TOOL, toolCallId = call.id, toolName = spec.name, content = clipped),
            persist = true
        )
    }

    private fun history(sessionId: String, systemPrompt: String): List<Message> {
        val session = c.sessions.byId(sessionId) ?: return emptyList()
        val out = mutableListOf<Message>()
        if (systemPrompt.isNotBlank()) {
            out.add(Message(role = Role.SYSTEM, content = systemPrompt))
        }
        session.messages.forEach { message ->
            when (message.role) {
                Role.ASSISTANT ->
                    if (message.content.isNotBlank() || message.toolCalls.isNotEmpty()) out.add(message)

                Role.TOOL ->
                    if (message.content.isNotBlank()) out.add(message)

                else -> out.add(message)
            }
        }
        return out
    }
}