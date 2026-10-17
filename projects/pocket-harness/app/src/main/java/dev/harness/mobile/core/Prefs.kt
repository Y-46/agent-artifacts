package dev.harness.mobile.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.harness.mobile.data.appJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
enum class ApprovalMode {
    /** 每次工具调用都要确认。 */
    ALWAYS_ASK,

    /** 只读操作自动放行，写/删需要确认。 */
    SMART,

    /** 全部自动放行（用起来像桌面版，风险自负）。 */
    AUTO
}

@Serializable
data class Settings(
    val baseUrl: String = "https://api.deepseek.com/v1",
    val model: String = "deepseek-chat",
    val temperature: Double = 0.6,
    val maxTokens: Int = 4096,
    val maxIterations: Int = 16,
    val outputCharLimit: Int = 16000,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val approvalMode: ApprovalMode = ApprovalMode.SMART,
    val deviceFolderUri: String? = null,
    val deviceFolderLabel: String? = null
)

const val DEFAULT_SYSTEM_PROMPT: String = """你是 Pocket Harness，一个运行在安卓手机上的自主智能体（agent harness）。你和桌面版一样拥有工具调用能力，但屏幕很小。

路径规则（很重要）：
- 相对路径，或 workspace/ 前缀 → App 专属工作区，可自由读写。
- device/ 前缀 → 用户授权的手机真实文件夹；未授权时调用会报错，此时请提醒用户去「设置 → 设备文件夹」授权。

工作方式：
1. 先理解需求，需要了解现状时用 fs_list / fs_read 查证，不要凭空猜测文件内容。
2. 改文件用 fs_write，写之前若文件已存在先读，保留无关内容。
3. 需要外部资料用 web_search 找链接、web_fetch 抓正文。
4. 能并行的地方一次发出多个工具调用；不要重复读同一个文件。
5. 完成后用简短中文总结：改了什么、文件在哪、怎么验证。
6. 手机屏幕上，回答保持精简；代码块必须标注语言；不要输出大段无意义表格。"""

/** 用 Android Keystore 里的 AES/GCM 密钥加密 API Key，不落明文。 */
object Crypto {
    private const val ALIAS = "pocket_harness_master"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getEntry(ALIAS, null)
        if (existing is KeyStore.SecretKeyEntry) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String = runCatching {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + body, Base64.NO_WRAP)
    }.getOrElse {
        "plain:" + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String {
        if (stored.isBlank()) return ""
        if (stored.startsWith("plain:")) {
            return runCatching {
                String(Base64.decode(stored.removePrefix("plain:"), Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrDefault("")
        }
        return runCatching {
            val all = Base64.decode(stored, Base64.NO_WRAP)
            val iv = all.copyOfRange(0, 12)
            val body = all.copyOfRange(12, all.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrDefault("")
    }
}

class Prefs(private val context: Context) {
    private val settingsFile = File(context.filesDir, "settings.json")
    private val keyFile = File(context.filesDir, "api_key.enc")

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val value: Settings get() = _settings.value

    private fun loadSettings(): Settings = runCatching {
        if (settingsFile.exists()) {
            appJson.decodeFromString(Settings.serializer(), settingsFile.readText())
        } else {
            Settings()
        }
    }.getOrElse { Settings() }

    @Synchronized
    fun update(block: (Settings) -> Settings) {
        val next = block(_settings.value)
        _settings.value = next
        runCatching { settingsFile.writeText(appJson.encodeToString(Settings.serializer(), next)) }
    }

    @Synchronized
    fun setApiKey(key: String) {
        runCatching { keyFile.writeText(Crypto.encrypt(key)) }
    }

    fun apiKey(): String = runCatching {
        if (keyFile.exists()) Crypto.decrypt(keyFile.readText()) else ""
    }.getOrDefault("")

    fun hasApiKey(): Boolean = apiKey().isNotBlank()
}