package dev.harness.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.harness.mobile.core.AppContainer
import dev.harness.mobile.data.JobRecord
import dev.harness.mobile.data.JobStatus
import dev.harness.mobile.util.fmtDuration
import dev.harness.mobile.util.fmtTime

@Composable
fun JobsScreen(container: AppContainer) {
    val jobs by container.jobs.jobs.collectAsState()
    var detailId by remember { mutableStateOf<String?>(null) }
    val detail = jobs.firstOrNull { it.id == detailId }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = if (detail == null) "任务" else "任务日志",
            subtitle = if (detail == null) {
                "${jobs.count { it.status == JobStatus.RUNNING }} 个运行中 · 共 ${jobs.size} 条记录"
            } else {
                "${detail.title} · ${detail.status.name}"
            },
            leading = if (detail == null) {
                null
            } else {
                {
                    IconButton(onClick = { detailId = null }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            },
            actions = {
                if (detail == null && jobs.any { it.status != JobStatus.RUNNING }) {
                    TextButton(onClick = { container.jobs.clearFinished() }) { Text("清理已结束") }
                }
                if (detail != null && detail.status == JobStatus.RUNNING) {
                    TextButton(onClick = { container.runner.stop() }) { Text("取消任务") }
                }
            }
        )

        if (jobs.isEmpty()) {
            EmptyHint(text = "还没有任务。每次让 Agent 干活都会生成一条记录。", modifier = Modifier.weight(1f))
        } else if (detail != null) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
            ) {
                items(detail.logs) { log ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = fmtTime(log.at),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(52.dp)
                        )
                        Text(
                            text = log.text,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (log.level) {
                                "error" -> MaterialTheme.colorScheme.error
                                "warn" -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(jobs, key = { it.id }) { job ->
                    JobRow(job) { detailId = job.id }
                }
            }
        }
    }
}

@Composable
private fun JobRow(job: JobRecord, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                JobStatusPill(job.status)
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = fmtTime(job.startedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val ended = job.endedAt
                Text(
                    text = if (ended != null) "耗时 ${fmtDuration(ended - job.startedAt)}" else "进行中",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${job.logs.size} 条日志",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}