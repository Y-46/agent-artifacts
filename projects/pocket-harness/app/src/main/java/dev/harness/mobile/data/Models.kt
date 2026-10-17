package dev.harness.mobile.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** 全局 JSON 配置：宽松解析，便于兼容各家 OpenAI 兼容服务的细微差异。 */
val appJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = false
}

fun newId(): String = UUID.randomUUID().toString()

fun nowMs(): Long = System.currentTimeMillis()

@Serializable
enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String = "{}"
)

@Serializable
data class Message(
    val id: String = newId(),
    val role: Role = Role.USER,
    val content: String = "",
    val reasoning: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val createdAt: Long = nowMs(),
    val failed: Boolean = false
)

@Serializable
data class Session(
    val id: String = newId(),
    val title: String = "新会话",
    val createdAt: Long = nowMs(),
    val updatedAt: Long = nowMs(),
    val messages: List<Message> = emptyList()
)

@Serializable
enum class JobStatus { RUNNING, DONE, FAILED, CANCELED }

@Serializable
data class JobLog(
    val at: Long = nowMs(),
    val text: String,
    val level: String = "info"
)

@Serializable
data class JobRecord(
    val id: String = newId(),
    val sessionId: String,
    val title: String,
    val status: JobStatus = JobStatus.RUNNING,
    val startedAt: Long = nowMs(),
    val endedAt: Long? = null,
    val logs: List<JobLog> = emptyList()
)

/** Agent 当前运行状态的快照，用于界面显示"正在思考/正在执行工具"。 */
data class RunState(
    val running: Boolean = false,
    val jobId: String? = null,
    val sessionId: String? = null,
    val phase: String = ""
)

/** 等待用户确认的工具调用。 */
data class ApprovalRequest(
    val id: String = newId(),
    val sessionId: String,
    val toolName: String,
    val arguments: String,
    val mutating: Boolean
)