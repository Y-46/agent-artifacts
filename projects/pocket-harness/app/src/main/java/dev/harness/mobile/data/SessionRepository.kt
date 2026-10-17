package dev.harness.mobile.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * 会话仓库：一个会话一个 JSON 文件，全部落在 App 私有目录，不会上传。
 * 写盘只在关键节点（首条消息、每轮回答结束、工具结果）发生，流式增量只改内存。
 */
class SessionRepository(context: Context) {

    private val dir: File = File(context.filesDir, "sessions").also { it.mkdirs() }

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".json") } ?: emptyArray()
        _sessions.value = files
            .mapNotNull { file ->
                runCatching { appJson.decodeFromString(Session.serializer(), file.readText()) }.getOrNull()
            }
            .sortedByDescending { it.updatedAt }
    }

    fun byId(id: String): Session? = _sessions.value.firstOrNull { it.id == id }

    @Synchronized
    fun create(title: String = "新会话"): Session {
        val session = Session(title = title)
        _sessions.update { list -> listOf(session) + list }
        persist(session.id)
        return session
    }

    @Synchronized
    fun delete(id: String) {
        _sessions.update { list -> list.filterNot { it.id == id } }
        File(dir, "$id.json").delete()
    }

    @Synchronized
    fun rename(id: String, title: String) {
        mutate(id) { it.copy(title = title.ifBlank { "新会话" }, updatedAt = nowMs()) }
        persist(id)
    }

    @Synchronized
    fun append(sessionId: String, message: Message, persist: Boolean = false) {
        mutate(sessionId) { it.copy(messages = it.messages + message, updatedAt = nowMs()) }
        if (persist) persist(sessionId)
    }

    @Synchronized
    fun patch(sessionId: String, messageId: String, persist: Boolean = false, block: (Message) -> Message) {
        mutate(sessionId) { session ->
            session.copy(messages = session.messages.map { if (it.id == messageId) block(it) else it })
        }
        if (persist) persist(sessionId)
    }

    @Synchronized
    fun replace(sessionId: String, message: Message, persist: Boolean = false) {
        mutate(sessionId) { session ->
            session.copy(messages = session.messages.map { if (it.id == message.id) message else it })
        }
        if (persist) persist(sessionId)
    }

    @Synchronized
    fun clear(sessionId: String) {
        mutate(sessionId) { it.copy(messages = emptyList(), updatedAt = nowMs()) }
        persist(sessionId)
    }

    @Synchronized
    fun touch(sessionId: String) {
        mutate(sessionId) { it.copy(updatedAt = nowMs()) }
        persist(sessionId)
    }

    @Synchronized
    fun setTitleIfDefault(sessionId: String, title: String) {
        val current = byId(sessionId) ?: return
        if (current.title != "新会话" || current.messages.count { it.role == Role.USER } > 1) return
        rename(sessionId, title.take(20))
    }

    @Synchronized
    fun persist(sessionId: String) {
        val session = byId(sessionId) ?: return
        val target = File(dir, "${session.id}.json")
        runCatching {
            val tmp = File(dir, "${session.id}.json.tmp")
            tmp.writeText(appJson.encodeToString(Session.serializer(), session))
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }
    }

    /** 取消/失败时，把没写完的空 assistant 占位消息删掉，避免留下空气泡。 */
    @Synchronized
    fun dropTrailingEmptyAssistant(sessionId: String) {
        mutate(sessionId) { session ->
            val last = session.messages.lastOrNull()
            if (last != null && last.role == Role.ASSISTANT && last.content.isBlank() && last.toolCalls.isEmpty()) {
                session.copy(messages = session.messages.dropLast(1))
            } else {
                session
            }
        }
    }
    private fun mutate(id: String, block: (Session) -> Session) {
        _sessions.update { list ->
            list.map { if (it.id == id) block(it) else it }.sortedByDescending { it.updatedAt }
        }
    }
}