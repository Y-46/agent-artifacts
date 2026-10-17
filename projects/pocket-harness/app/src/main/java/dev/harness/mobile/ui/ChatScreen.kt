package dev.harness.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.harness.mobile.core.AppContainer
import dev.harness.mobile.data.JobStatus
import dev.harness.mobile.data.Message
import dev.harness.mobile.data.Role
import dev.harness.mobile.data.appJson
import dev.harness.mobile.util.fmtRelative
import dev.harness.mobile.util.snippet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private val prettyJson = Json { prettyPrint = true }

@Composable
fun ChatScreen(
    container: AppContainer,
    sessionId: String,
    onOpenSessions: () -> Unit,
    onOpenJobs: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val sessions by container.sessions.sessions.collectAsState()
    val runState by container.runner.state.collectAsState()
    val pending by container.approvals.pending.collectAsState()
    val settings by container.prefs.settings.collectAsState()
    val jobs by container.jobs.jobs.collectAsState()

    val session = sessions.firstOrNull { it.id == sessionId }
    val messages = session?.messages.orEmpty()
    val runningHere = runState.running && runState.sessionId == sessionId
    val runningJobs = jobs.count { it.status == JobStatus.RUNNING }
    val hasKey = remember(settings) { container.prefs.hasApiKey() }

    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val tail = messages.lastOrNull()
    // 只在"贴着底部"时自动跟随，否则用户往上翻历史会被流式输出拽回去。
    LaunchedEffect(messages.size, tail?.content?.length ?: 0, tail?.reasoning?.length ?: 0) {
        if (messages.isEmpty()) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (lastVisible >= messages.lastIndex - 1) {
            runCatching { listState.animateScrollToItem(messages.lastIndex) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = session?.title ?: "Pocket Harness",
            subtitle = when {
                runningHere -> runState.phase
                messages.isNotEmpty() -> "${settings.model} · ${fmtRelative(session?.updatedAt ?: 0L)}"
                else -> settings.model
            },
            leading = {
                IconButton(onClick = onOpenSessions) {
                    Icon(Icons.Filled.Menu, contentDescription = "会话列表")
                }
            },
            actions = {
                if (runningJobs > 0) {
                    TextButton(onClick = onOpenJobs) {
                        Text("任务 $runningJobs", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    IconButton(onClick = onOpenJobs) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "任务")
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                }
            }
        )

        if (!hasKey) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { onOpenSettings() }
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = "还没配置模型接口",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "点这里填 Base URL、模型名和 API Key，否则消息发不出去。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        if (messages.isEmpty()) {
            EmptyChatHint(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    when (message.role) {
                        Role.USER -> UserBubble(message)
                        Role.ASSISTANT -> AssistantBubble(message)
                        Role.TOOL -> ToolResultCard(message)
                        Role.SYSTEM -> Unit
                    }
                }
                if (runningHere) {
                    item(key = "running-indicator") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text(
                                text = runState.phase.ifBlank { "处理中…" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("告诉 Agent 要做什么…") },
                    maxLines = 5,
                    shape = RoundedCornerShape(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (runningHere) {
                    FilledIconButton(
                        onClick = { container.runner.stop() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "停止")
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            val text = input.trim()
                            if (text.isNotEmpty()) {
                                container.runner.start(sessionId, text)
                                input = ""
                            }
                        },
                        enabled = input.isNotBlank(),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "发送")
                    }
                }
            }
        }
    }

    val approval = pending
    if (approval != null) {
        ApprovalDialog(
            toolName = approval.toolName,
            arguments = approval.arguments,
            mutating = approval.mutating,
            onApprove = { container.approvals.answer(true) },
            onReject = { container.approvals.answer(false) }
        )
    }
}

@Composable
private fun EmptyChatHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Pocket Harness", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "我是跑在你手机里的 agent：能读写文件、搜网页、跑多轮工具调用。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )
        HintLine("先让它读一下工作区的 README")
        HintLine("在 device/Download 里找出最大的几个文件")
        HintLine("把这段代码改对，然后告诉我改了什么")
        HintLine("搜一下 Android 16 的新特性，整理成 5 条")
        Text(
            text = "首次使用请到「设置」填 Base URL、模型名和 API Key。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 18.dp)
        )
    }
}

@Composable
private fun HintLine(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Text(
            text = "›  $text",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun UserBubble(message: Message) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AssistantBubble(message: Message) {
    var showReasoning by remember(message.id) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (message.reasoning.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(bottom = 6.dp).clickable { showReasoning = !showReasoning }
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        text = if (showReasoning) "🧠 思考过程（点击收起）" else "🧠 思考过程（点击展开）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showReasoning) {
                        Text(
                            text = message.reasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp).heightIn(max = 260.dp).verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
        if (message.toolCalls.isNotEmpty()) {
            message.toolCalls.forEach { call ->
                Text(
                    text = "→ ${call.name}(${snippet(call.arguments, 70)})",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
        if (message.content.isNotBlank()) {
            val failed = message.failed
            if (failed) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            } else {
                MarkdownText(message.content)
            }
        }
    }
}

@Composable
private fun ToolResultCard(message: Message) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    val content = message.content
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🔧", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = message.toolName ?: "工具",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 6.dp).weight(1f)
                )
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (expanded) content else snippet(content, 180),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = if (content.startsWith("错误")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = if (expanded) 400 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun ApprovalDialog(
    toolName: String,
    arguments: String,
    mutating: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text(if (mutating) "允许这次改动？" else "允许执行这个工具？") },
        text = {
            Column {
                Text(
                    text = toolName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        text = prettyArguments(arguments),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApprove) { Text(if (mutating) "允许修改" else "允许") }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("拒绝") }
        }
    )
}

private fun prettyArguments(raw: String): String = runCatching {
    val element: JsonElement = appJson.parseToJsonElement(raw.ifBlank { "{}" })
    prettyJson.encodeToString(JsonElement.serializer(), element)
}.getOrElse { raw }