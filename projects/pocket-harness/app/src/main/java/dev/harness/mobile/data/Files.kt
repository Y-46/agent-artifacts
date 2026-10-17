package dev.harness.mobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.harness.mobile.core.Prefs
import java.io.File

data class FileEntry(
    val name: String,
    val rel: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long
)

data class SearchHit(
    val path: String,
    val line: Int,
    val text: String
)

private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "json", "jsonl", "kt", "kts", "java", "xml", "yml", "yaml",
    "toml", "ini", "cfg", "conf", "csv", "log", "py", "js", "jsx", "ts", "tsx", "html",
    "htm", "css", "scss", "sh", "bash", "zsh", "bat", "ps1", "c", "h", "cpp", "hpp", "cc",
    "cs", "go", "rs", "rb", "php", "sql", "swift", "gradle", "properties", "env", "vue",
    "svelte", "lua", "pl", "r", "tex", "srt", "vtt", "dockerfile", "gitignore", "editorconfig"
)

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

fun extOf(name: String): String = name.substringAfterLast('.', "").lowercase()

fun isTextPath(name: String): Boolean {
    if (name.startsWith(".") && !name.contains('.')) return true
    return extOf(name) in TEXT_EXTENSIONS || name == "Dockerfile" || name.startsWith("Makefile")
}

fun isImagePath(name: String): Boolean = extOf(name) in IMAGE_EXTENSIONS

/** 去掉空段、`.`、并安全处理 `..`（不允许逃出根目录）。 */
internal fun sanitize(rel: String): List<String> {
    val parts = mutableListOf<String>()
    rel.replace('\\', '/').split('/').forEach { seg ->
        when {
            seg.isBlank() || seg == "." -> Unit
            seg == ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
            else -> parts.add(seg)
        }
    }
    return parts
}

internal fun joinRel(base: String, name: String): String =
    if (base.isBlank()) name else "$base/$name"

private fun safeJoin(root: File, rel: String): File? {
    val parts = sanitize(rel)
    val target = if (parts.isEmpty()) root else File(root, parts.joinToString(File.separator))
    val rootPath = root.absolutePath.trimEnd(File.separatorChar)
    val targetPath = target.absolutePath
    return if (targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)) target else null
}

/** App 专属工作区，永远可用。 */
class WorkspaceStore(context: Context) {

    val root: File = File(context.filesDir, "workspace").also { it.mkdirs() }

    init {
        seedIfEmpty()
    }

    private fun seedIfEmpty() {
        val entries = root.listFiles()
        if (entries != null && entries.isNotEmpty()) return
        root.mkdirs()
        File(root, "README.md").writeText(
            """
            # 工作区

            这是 Pocket Harness 的应用专属工作区，Agent 可以在这里自由读写。

            - 把要改的代码、要读的文档放这里
            - 用「文件」页可以浏览和预览
            - Agent 的路径写成相对路径或 `workspace/x.md` 即可
            """.trimIndent()
        )
    }

    fun resolve(rel: String): File? = safeJoin(root, rel)

    fun list(rel: String): List<FileEntry> {
        val dir = resolve(rel) ?: throw IllegalArgumentException("路径不合法：$rel")
        if (!dir.exists()) throw IllegalArgumentException("目录不存在：$rel")
        if (!dir.isDirectory) throw IllegalArgumentException("不是目录：$rel")
        val files = dir.listFiles() ?: emptyArray()
        return files
            .map { f ->
                FileEntry(
                    name = f.name,
                    rel = joinRel(rel, f.name),
                    isDir = f.isDirectory,
                    size = if (f.isFile) f.length() else 0L,
                    modified = f.lastModified()
                )
            }
            .sortedWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    fun read(rel: String, maxChars: Int = 200_000): String {
        val file = resolve(rel) ?: throw IllegalArgumentException("路径不合法：$rel")
        if (!file.exists()) throw IllegalArgumentException("文件不存在：$rel")
        if (file.isDirectory) throw IllegalArgumentException("$rel 是目录，请用 fs_list")
        if (!isTextPath(file.name)) return "（二进制文件 ${file.name}，${file.length()} 字节，不能按文本读取）"
        val text = file.readText()
        return if (text.length > maxChars) {
            text.take(maxChars) + "\n…[已截断，共 ${text.length} 字符]"
        } else {
            text
        }
    }

    fun bytes(rel: String, maxBytes: Int = 12_000_000): ByteArray? {
        val file = resolve(rel) ?: return null
        if (!file.isFile || file.length() > maxBytes) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    fun write(rel: String, content: String) {
        val file = resolve(rel) ?: throw IllegalArgumentException("路径不合法：$rel")
        if (file.isDirectory) throw IllegalArgumentException("$rel 是目录")
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun mkdir(rel: String) {
        val dir = resolve(rel) ?: throw IllegalArgumentException("路径不合法：$rel")
        if (!dir.mkdirs() && !dir.isDirectory) throw IllegalArgumentException("创建目录失败：$rel")
    }

    fun delete(rel: String) {
        val file = resolve(rel) ?: throw IllegalArgumentException("路径不合法：$rel")
        if (file == root) throw IllegalArgumentException("不能删除工作区根目录")
        if (!file.exists()) throw IllegalArgumentException("不存在：$rel")
        if (!file.deleteRecursively()) throw IllegalStateException("删除失败：$rel")
    }

    fun exists(rel: String): Boolean = resolve(rel)?.exists() == true

    fun size(rel: String): Long = resolve(rel)?.takeIf { it.isFile }?.length() ?: 0L

    fun modified(rel: String): Long = resolve(rel)?.lastModified() ?: 0L

    fun search(query: String, rel: String, maxHits: Int = 60): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        val base = resolve(rel) ?: return emptyList()
        val hits = mutableListOf<SearchHit>()
        val candidates = mutableListOf<File>()
        if (base.isFile) {
            candidates.add(base)
        } else if (base.isDirectory) {
            base.walkTopDown().take(3000).forEach { if (it.isFile) candidates.add(it) }
        }
        for (file in candidates) {
            if (hits.size >= maxHits) break
            if (!isTextPath(file.name) || file.length() > 512L * 1024L) continue
            runCatching {
                file.readLines().forEachIndexed { index, line ->
                    if (hits.size < maxHits && line.contains(query, ignoreCase = true)) {
                        val relPath = file.relativeTo(root).path.replace('\\', '/')
                        hits.add(SearchHit(relPath, index + 1, line.trim().take(240)))
                    }
                }
            }
        }
        return hits
    }
}

/**
 * 手机真实存储：用户通过系统文件选择器授权一个目录（SAF），保存持久化权限。
 */
class DeviceStore(private val context: Context, private val prefs: Prefs) {

    fun treeUri(): Uri? = prefs.value.deviceFolderUri?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun rootDoc(): DocumentFile? = treeUri()?.let { runCatching { DocumentFile.fromTreeUri(context, it) }.getOrNull() }

    fun connected(): Boolean = rootDoc()?.canRead() == true

    fun label(): String {
        if (treeUri() == null) return "未连接"
        val saved = prefs.value.deviceFolderLabel
        if (!saved.isNullOrBlank()) return saved
        return runCatching { rootDoc()?.name }.getOrNull() ?: "设备文件夹"
    }

    fun connect(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val label = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        prefs.update { it.copy(deviceFolderUri = uri.toString(), deviceFolderLabel = label ?: "设备文件夹") }
    }

    fun disconnect() {
        prefs.update { it.copy(deviceFolderUri = null, deviceFolderLabel = null) }
    }

    private fun requireRoot(): DocumentFile =
        rootDoc()?.takeIf { it.canRead() }
            ?: throw IllegalStateException("设备文件夹未授权。请到「设置 → 设备文件夹」选择一次真实目录，再用 device/ 前缀访问。")

    private fun resolve(rel: String): DocumentFile? {
        var current: DocumentFile = rootDoc() ?: return null
        for (segment in sanitize(rel)) {
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    private fun resolveParent(rel: String): Pair<DocumentFile, String>? {
        val parts = sanitize(rel)
        if (parts.isEmpty()) return null
        var current: DocumentFile = requireRoot()
        for (segment in parts.dropLast(1)) {
            current = current.findFile(segment) ?: return null
        }
        return current to parts.last()
    }

    fun list(rel: String): List<FileEntry> {
        val dir = resolve(rel) ?: run {
            requireRoot()
            throw IllegalArgumentException("目录不存在：$rel")
        }
        if (!dir.isDirectory) throw IllegalArgumentException("不是目录：$rel")
        return dir.listFiles()
            .map { doc ->
                val name = doc.name ?: "(未命名)"
                FileEntry(
                    name = name,
                    rel = joinRel(rel, name),
                    isDir = doc.isDirectory,
                    size = doc.length(),
                    modified = doc.lastModified()
                )
            }
            .sortedWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    fun read(rel: String, maxChars: Int = 200_000): String {
        val doc = resolve(rel) ?: run {
            requireRoot()
            throw IllegalArgumentException("文件不存在：$rel")
        }
        if (doc.isDirectory) throw IllegalArgumentException("$rel 是目录")
        val name = doc.name ?: rel
        if (!isTextPath(name)) return "（二进制文件 $name，${doc.length()} 字节，不能按文本读取）"
        val text = context.contentResolver.openInputStream(doc.uri)?.use { input ->
            String(input.readBytes(), Charsets.UTF_8)
        } ?: throw IllegalStateException("无法读取：$rel")
        return if (text.length > maxChars) {
            text.take(maxChars) + "\n…[已截断，共 ${text.length} 字符]"
        } else {
            text
        }
    }

    fun bytes(rel: String, maxBytes: Int = 12_000_000): ByteArray? {
        val doc = resolve(rel) ?: return null
        if (doc.isDirectory || doc.length() > maxBytes) return null
        return runCatching {
            context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes() }
        }.getOrNull()
    }

    fun write(rel: String, content: String) {
        val existing = resolve(rel)
        if (existing != null && existing.isFile) {
            val out = context.contentResolver.openOutputStream(existing.uri, "wt")
                ?: throw IllegalStateException("无法写入：$rel")
            out.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            return
        }
        val (parent, name) = resolveParent(rel) ?: throw IllegalArgumentException("目标目录不存在：$rel")
        val created = parent.createFile("text/plain", name)
            ?: throw IllegalStateException("创建文件失败：$name")
        val out = context.contentResolver.openOutputStream(created.uri, "wt")
            ?: throw IllegalStateException("无法写入：$rel")
        out.use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }

    fun mkdir(rel: String) {
        if (resolve(rel) != null) return
        val (parent, name) = resolveParent(rel) ?: throw IllegalArgumentException("父目录不存在：$rel")
        parent.createDirectory(name) ?: throw IllegalStateException("创建目录失败：$name")
    }

    fun delete(rel: String) {
        if (sanitize(rel).isEmpty()) throw IllegalArgumentException("不能删除根目录")
        val doc = resolve(rel) ?: throw IllegalArgumentException("不存在：$rel")
        if (!doc.delete()) throw IllegalStateException("删除失败：$rel")
    }

    fun exists(rel: String): Boolean = resolve(rel) != null

    fun size(rel: String): Long = resolve(rel)?.length() ?: 0L

    fun modified(rel: String): Long = resolve(rel)?.lastModified() ?: 0L

    fun search(query: String, rel: String, maxHits: Int = 40): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        val base = resolve(rel) ?: return emptyList()
        val hits = mutableListOf<SearchHit>()
        val queue = ArrayDeque<Pair<DocumentFile, String>>()
        queue.add(base to rel)
        var visited = 0
        while (queue.isNotEmpty() && hits.size < maxHits && visited < 800) {
            val (doc, path) = queue.removeFirst()
            visited++
            if (doc.isDirectory) {
                doc.listFiles().forEach { child ->
                    val childName = child.name ?: return@forEach
                    val childPath = joinRel(path, childName)
                    if (child.isDirectory) {
                        queue.add(child to childPath)
                    } else if (hits.size < maxHits) {
                        searchOneFile(child, childPath, query, hits, maxHits)
                    }
                }
            } else if (hits.size < maxHits) {
                searchOneFile(doc, path, query, hits, maxHits)
            }
        }
        return hits
    }

    private fun searchOneFile(
        file: DocumentFile,
        path: String,
        query: String,
        hits: MutableList<SearchHit>,
        maxHits: Int
    ) {
        val name = file.name ?: return
        if (!isTextPath(name) || file.length() > 512L * 1024L) return
        runCatching {
            val text = context.contentResolver.openInputStream(file.uri)?.use { input ->
                String(input.readBytes(), Charsets.UTF_8)
            } ?: return
            text.lines().forEachIndexed { index, line ->
                if (hits.size < maxHits && line.contains(query, ignoreCase = true)) {
                    hits.add(SearchHit(path, index + 1, line.trim().take(240)))
                }
            }
        }
    }
}

/**
 * 统一路径门面：`device/...` 走手机真实存储，其余走 App 工作区。
 */
class Paths(private val workspace: WorkspaceStore, private val device: DeviceStore) {

    private fun normalized(path: String): String {
        var p = path.trim().replace('\\', '/').trimStart('/')
        p = when {
            p == "device" -> ""
            p.startsWith("device/") -> p.removePrefix("device/")
            p == "workspace" -> ""
            p.startsWith("workspace/") -> p.removePrefix("workspace/")
            else -> p
        }
        return p.trimEnd('/')
    }

    private fun isDevice(path: String): Boolean {
        val p = path.trim().replace('\\', '/').trimStart('/')
        return p == "device" || p.startsWith("device/")
    }

    private fun requireDevice() {
        if (!device.connected()) {
            throw IllegalStateException(
                "设备文件夹未授权。请到「设置 → 设备文件夹」选择一个真实目录，再用 device/ 前缀访问。"
            )
        }
    }

    fun list(path: String): List<FileEntry> =
        if (isDevice(path)) {
            requireDevice()
            device.list(normalized(path))
        } else {
            workspace.list(normalized(path))
        }

    fun read(path: String, maxChars: Int = 200_000): String =
        if (isDevice(path)) {
            requireDevice()
            device.read(normalized(path), maxChars)
        } else {
            workspace.read(normalized(path), maxChars)
        }

    fun bytes(path: String, maxBytes: Int = 12_000_000): ByteArray? =
        if (isDevice(path)) {
            if (!device.connected()) null else device.bytes(normalized(path), maxBytes)
        } else {
            workspace.bytes(normalized(path), maxBytes)
        }

    fun write(path: String, content: String) {
        if (isDevice(path)) {
            requireDevice()
            device.write(normalized(path), content)
        } else {
            workspace.write(normalized(path), content)
        }
    }

    fun mkdir(path: String) {
        if (isDevice(path)) {
            requireDevice()
            device.mkdir(normalized(path))
        } else {
            workspace.mkdir(normalized(path))
        }
    }

    fun delete(path: String) {
        if (isDevice(path)) {
            requireDevice()
            device.delete(normalized(path))
        } else {
            workspace.delete(normalized(path))
        }
    }

    fun search(query: String, path: String = ""): List<SearchHit> =
        if (isDevice(path)) {
            requireDevice()
            device.search(query, normalized(path))
        } else {
            workspace.search(query, normalized(path))
        }

    fun exists(path: String): Boolean =
        if (isDevice(path)) device.connected() && device.exists(normalized(path))
        else workspace.exists(normalized(path))

    fun size(path: String): Long =
        if (isDevice(path)) {
            if (!device.connected()) 0L else device.size(normalized(path))
        } else {
            workspace.size(normalized(path))
        }

    fun modified(path: String): Long =
        if (isDevice(path)) {
            if (!device.connected()) 0L else device.modified(normalized(path))
        } else {
            workspace.modified(normalized(path))
        }
}