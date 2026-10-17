package dev.harness.mobile.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * 后台任务仓库：Agent 每跑一轮就是一个 Job，日志实时进内存、结束落盘。
 */
class JobRepository(context: Context) {

    private val file = File(context.filesDir, "jobs.json")
    private val serializer = ListSerializer(JobRecord.serializer())

    private val _jobs = MutableStateFlow<List<JobRecord>>(emptyList())
    val jobs: StateFlow<List<JobRecord>> = _jobs.asStateFlow()

    init {
        restore()
    }

    private fun restore() {
        val saved = runCatching {
            if (file.exists()) appJson.decodeFromString(serializer, file.readText()) else emptyList()
        }.getOrDefault(emptyList())
        // 上次被杀掉的进程留下的 RUNNING 任务标记为失败，避免一直在"运行中"。
        _jobs.value = saved
            .map { job ->
                if (job.status == JobStatus.RUNNING) {
                    job.copy(status = JobStatus.FAILED, endedAt = nowMs())
                } else {
                    job
                }
            }
            .take(60)
    }

    fun running(): List<JobRecord> = _jobs.value.filter { it.status == JobStatus.RUNNING }

    fun byId(id: String): JobRecord? = _jobs.value.firstOrNull { it.id == id }

    @Synchronized
    fun start(sessionId: String, title: String): JobRecord {
        val job = JobRecord(sessionId = sessionId, title = title.take(60).ifBlank { "任务" })
        _jobs.update { list -> (listOf(job) + list).take(60) }
        persist()
        return job
    }

    @Synchronized
    fun log(jobId: String, text: String, level: String = "info") {
        _jobs.update { list ->
            list.map { job ->
                if (job.id == jobId) job.copy(logs = (job.logs + JobLog(text = text, level = level)).takeLast(400))
                else job
            }
        }
    }

    @Synchronized
    fun finish(jobId: String, status: JobStatus) {
        _jobs.update { list ->
            list.map { job ->
                if (job.id == jobId && job.status == JobStatus.RUNNING) {
                    job.copy(status = status, endedAt = nowMs())
                } else {
                    job
                }
            }
        }
        persist()
    }

    @Synchronized
    fun clearFinished() {
        _jobs.update { list -> list.filter { it.status == JobStatus.RUNNING } }
        persist()
    }

    private fun persist() {
        runCatching { file.writeText(appJson.encodeToString(serializer, _jobs.value)) }
    }
}