package dev.harness.mobile.core

import android.content.Context
import dev.harness.mobile.agent.AgentRunner
import dev.harness.mobile.agent.ApprovalGate
import dev.harness.mobile.agent.ToolRegistry
import dev.harness.mobile.data.DeviceStore
import dev.harness.mobile.data.JobRepository
import dev.harness.mobile.data.Paths
import dev.harness.mobile.data.Session
import dev.harness.mobile.data.SessionRepository
import dev.harness.mobile.data.WorkspaceStore
import dev.harness.mobile.net.OpenAiClient
import dev.harness.mobile.net.WebClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 极简手工依赖容器（不引 Hilt/Dagger，省构建复杂度）。 */
class AppContainer(val context: Context) {

    val prefs = Prefs(context)
    val sessions = SessionRepository(context)
    val jobs = JobRepository(context)

    val workspace = WorkspaceStore(context)
    val device = DeviceStore(context, prefs)
    val paths = Paths(workspace, device)

    val approvals = ApprovalGate()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    val llm = OpenAiClient(http)
    val web = WebClient(http)
    val tools = ToolRegistry(paths, web, jobs)
    val runner = AgentRunner(this)

    fun ensureSession(): Session = sessions.sessions.value.firstOrNull() ?: sessions.create()
}