package dev.harness.mobile.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.harness.mobile.core.AppContainer
import dev.harness.mobile.data.FileEntry
import dev.harness.mobile.data.isImagePath
import dev.harness.mobile.data.isTextPath
import dev.harness.mobile.util.fmtRelative
import dev.harness.mobile.util.fmtSize

private fun buildPath(root: String, rel: String): String {
    val base = if (root == "device") "device" else "workspace"
    return if (rel.isBlank()) base else "$base/$rel"
}

private fun joinRel(base: String, name: String): String =
    if (base.isBlank()) name else "$base/$name"

private fun parentOf(rel: String): String {
    val index = rel.lastIndexOf('/')
    return if (index < 0) "" else rel.substring(0, index)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(container: AppContainer) {
    var root by rememberSaveable { mutableStateOf("workspace") }
    var rel by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }
    var createKind by remember { mutableStateOf<String?>(null) }
    var createName by remember { mutableStateOf("") }

    val deviceConnected = container.device.connected()

    LaunchedEffect(root, rel, refresh, deviceConnected) {
        error = null
        val target = buildPath(root, rel)
        entries = runCatching { container.paths.list(target) }
            .onFailure { error = it.message ?: "读取失败" }
            .getOrDefault(emptyList())
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            container.device.connect(uri)
            root = "device"
            rel = ""
            refresh++
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "文件",
            subtitle = if (root == "device") {
                "设备 · ${container.device.label()}"
            } else {
                "应用工作区 · 私有目录"
            },
            actions = {
                IconButton(onClick = {
                    createKind = "file"
                    createName = ""
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "新建文件")
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChoiceChip("工作区", root == "workspace") {
                root = "workspace"
                rel = ""
            }
            ChoiceChip("设备文件夹", root == "device") {
                root = "device"
                rel = ""
            }
            Spacer(modifier = Modifier.width(4.dp))
            if (!deviceConnected) {
                TextButton(onClick = { picker.launch(null) }) { Text("授权目录") }
            } else {
                TextButton(onClick = {
                    container.device.disconnect()
                    root = "workspace"
                    rel = ""
                    refresh++
                }) { Text("断开") }
            }
        }

        if (rel.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clickable { rel = parentOf(rel) }
            ) {
                Text(
                    text = "⬆  ${root}/${rel}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                )
            }
        }

        val currentError = error
        if (root == "device" && !deviceConnected) {
            EmptyHint(
                text = "还没有授权真实文件夹。\n点上面的「授权目录」，选一个你想让 Agent 读写的目录（比如 Download 或某个项目文件夹）。",
                modifier = Modifier.weight(1f)
            )
        } else if (currentError != null) {
            EmptyHint(text = "读取失败：$currentError", modifier = Modifier.weight(1f))
        } else if (entries.isEmpty()) {
            EmptyHint(text = "这个目录是空的", modifier = Modifier.weight(1f))
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(entries, key = { it.rel }) { entry ->
                    val full = buildPath(root, entry.rel)
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .combinedClickable(
                                onClick = {
                                    if (entry.isDir) rel = entry.rel else preview = full
                                },
                                onLongClick = { deleteTarget = entry }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (entry.isDir) "📁" else iconFor(entry.name),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (entry.isDir) {
                                        "目录 · ${fmtRelative(entry.modified)}"
                                    } else {
                                        "${fmtSize(entry.size)} · ${fmtRelative(entry.modified)}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (entry.isDir) {
                                Text(text = "›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    val creating = createKind
    if (creating != null) {
        AlertDialog(
            onDismissRequest = { createKind = null },
            title = { Text("新建文件") },
            text = {
                Column {
                    Text(
                        text = "会创建在 ${root}/${rel.ifBlank { "" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createName = it },
                        singleLine = true,
                        placeholder = { Text("例如 notes.md") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = createName.trim()
                    if (name.isNotEmpty()) {
                        runCatching {
                            container.paths.write(buildPath(root, joinRel(rel, name)), "")
                            refresh++
                        }
                    }
                    createKind = null
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { createKind = null }) { Text("取消") }
            }
        )
    }

    val deleting = deleteTarget
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${deleting.name}」？") },
            text = { Text(if (deleting.isDir) "目录会连同里面的文件一起删除，不可恢复。" else "删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        container.paths.delete(buildPath(root, deleting.rel))
                        refresh++
                    }.onFailure { error = it.message }
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    val previewPath = preview
    if (previewPath != null) {
        FilePreview(container, previewPath) { preview = null }
    }
}

private fun iconFor(name: String): String = when {
    isImagePath(name) -> "🖼"
    name.endsWith(".md") -> "📝"
    name.endsWith(".json") || name.endsWith(".yml") || name.endsWith(".yaml") -> "🧾"
    name.endsWith(".kt") || name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".js") -> "🧑‍💻"
    else -> "📄"
}

@Composable
private fun FilePreview(container: AppContainer, path: String, onClose: () -> Unit) {
    val name = path.substringAfterLast('/')
    var text by remember(path) { mutableStateOf<String?>(null) }
    var bitmap by remember(path) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var message by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        if (isImagePath(name)) {
            val bytes = container.paths.bytes(path)
            bitmap = if (bytes == null) {
                null
            } else {
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
            }
            if (bitmap == null) message = "图片读取失败，或文件太大（超过 12MB）"
        } else if (isTextPath(name)) {
            text = runCatching { container.paths.read(path, 300_000) }
                .getOrElse { "读取失败：${it.message}" }
        } else {
            message = "这个类型暂不支持预览（可以借给 Agent 处理）"
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            title = name,
            subtitle = "${fmtSize(container.paths.size(path))} · $path",
            leading = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }
        )
        val image = bitmap
        val content = text
        when {
            image != null -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                Image(
                    bitmap = image,
                    contentDescription = name,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            content != null -> Text(
                text = content,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp)
            )

            else -> EmptyHint(text = message ?: "加载中…", modifier = Modifier.weight(1f))
        }
    }
}