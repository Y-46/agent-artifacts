package dev.harness.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.harness.mobile.core.AppContainer
import dev.harness.mobile.core.ApprovalMode
import dev.harness.mobile.util.fmtSize

@Composable
fun SettingsScreen(container: AppContainer) {
    val settings by container.prefs.settings.collectAsState()

    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var model by remember(settings.model) { mutableStateOf(settings.model) }
    var maxTokens by remember(settings.maxTokens) { mutableStateOf(settings.maxTokens.toString()) }
    var maxIterations by remember(settings.maxIterations) { mutableStateOf(settings.maxIterations.toString()) }
    var systemPrompt by remember(settings.systemPrompt) { mutableStateOf(settings.systemPrompt) }
    var temperature by remember(settings.temperature) { mutableStateOf(settings.temperature.toFloat()) }
    var apiKeyInput by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(container.prefs.hasApiKey()) }
    var hint by remember { mutableStateOf<String?>(null) }

    fun persist() {
        container.prefs.update { current ->
            current.copy(
                baseUrl = baseUrl.trim(),
                model = model.trim(),
                temperature = temperature.toDouble(),
                maxTokens = maxTokens.toIntOrNull()?.coerceIn(256, 65536) ?: current.maxTokens,
                maxIterations = maxIterations.toIntOrNull()?.coerceIn(1, 40) ?: current.maxIterations,
                systemPrompt = systemPrompt
            )
        }
        if (apiKeyInput.isNotBlank()) {
            container.prefs.setApiKey(apiKeyInput.trim())
            hasKey = true
            apiKeyInput = ""
        }
        hint = "已保存"
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) container.device.connect(uri)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "设置",
            subtitle = "全部数据只存在这台手机上",
            actions = {
                TextButton(onClick = { persist() }) { Text("保存") }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            SectionCard(title = "模型接口（OpenAI 兼容）") {
                Text(
                    text = "填任意 OpenAI 兼容服务的地址，例如 DeepSeek、OpenAI、Kimi、vLLM、Ollama。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChoiceChip("DeepSeek", settings.baseUrl.contains("deepseek")) {
                        baseUrl = "https://api.deepseek.com/v1"
                        model = "deepseek-chat"
                    }
                    ChoiceChip("OpenAI", settings.baseUrl.contains("openai.com")) {
                        baseUrl = "https://api.openai.com/v1"
                        model = "gpt-4o-mini"
                    }
                    ChoiceChip("本地/自建", settings.baseUrl.contains("192.168") || settings.baseUrl.contains("localhost")) {
                        baseUrl = "http://192.168.1.10:11434/v1"
                        model = "qwen2.5:7b"
                    }
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.deepseek.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名") },
                    placeholder = { Text("deepseek-chat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text(if (hasKey) "API Key（已保存，留空则不改）" else "API Key") },
                    placeholder = { Text("sk-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = "温度 ${"%.2f".format(temperature)}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..1.5f,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = { maxTokens = it },
                        label = { Text("max_tokens") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxIterations,
                        onValueChange = { maxIterations = it },
                        label = { Text("最大轮数") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SectionCard(title = "审批与执行") {
                Text(
                    text = "决定 Agent 调用工具前是否要你点头。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChoiceChip("全都问", settings.approvalMode == ApprovalMode.ALWAYS_ASK) {
                        container.prefs.update { it.copy(approvalMode = ApprovalMode.ALWAYS_ASK) }
                    }
                    ChoiceChip("只问写删", settings.approvalMode == ApprovalMode.SMART) {
                        container.prefs.update { it.copy(approvalMode = ApprovalMode.SMART) }
                    }
                    ChoiceChip("全部自动", settings.approvalMode == ApprovalMode.AUTO) {
                        container.prefs.update { it.copy(approvalMode = ApprovalMode.AUTO) }
                    }
                }
                Text(
                    text = when (settings.approvalMode) {
                        ApprovalMode.ALWAYS_ASK -> "每次工具调用都会弹窗确认，最安全但最啰嗦。"
                        ApprovalMode.SMART -> "读文件、列目录、搜索自动放行；写文件、删除、建目录会问你。"
                        ApprovalMode.AUTO -> "全部自动执行，最像电脑版，但 Agent 可能在你没看的时候改文件。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            SectionCard(title = "工作目录") {
                Text(
                    text = "工作区：App 私有目录，Agent 默认在这里读写。\n路径写法：notes.md、workspace/notes.md",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Text(
                        text = "设备文件夹：${container.device.label()}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Text(
                    text = "授权后 Agent 可以用 device/ 前缀读写你选中的真实目录。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { picker.launch(null) }) {
                        Text(if (settings.deviceFolderUri == null) "选择目录" else "重新选择")
                    }
                    if (settings.deviceFolderUri != null) {
                        OutlinedButton(onClick = { container.device.disconnect() }) { Text("断开") }
                    }
                }
            }

            SectionCard(title = "系统提示词") {
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    label = { Text("Agent 的行为约定") }
                )
                TextButton(onClick = {
                    systemPrompt = dev.harness.mobile.core.DEFAULT_SYSTEM_PROMPT
                }) { Text("恢复默认") }
            }

            SectionCard(title = "关于") {
                Text(
                    text = "Pocket Harness 1.0.0\n可用工具：${container.tools.names().joinToString("、")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "会话、日志、Key 全部保存在 App 私有目录，Key 用 Android Keystore 加密。卸载即清除。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            val currentHint = hint
            if (currentHint != null) {
                Text(
                    text = "✅ $currentHint",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                )
            }

            Button(
                onClick = { persist() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text("保存设置")
            }
        }
    }
}