package dev.harness.mobile.net

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

/**
 * 联网工具：DuckDuckGo HTML 版搜索（无需 Key）+ 网页正文抓取。
 */
class WebClient(private val http: OkHttpClient) {

    private val client: OkHttpClient = http.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun search(query: String, limit: Int = 6): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val html = runCatching {
            get("https://html.duckduckgo.com/html/?q=$encoded&kl=wt-wt")
        }.getOrElse { error ->
            throw IllegalStateException("搜索请求失败：${error.message}")
        }
        return parseResults(html, limit)
    }

    fun fetch(url: String, maxChars: Int = 12000): String {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val raw = get(normalized)
        val looksHtml = raw.contains("<html", ignoreCase = true) || raw.contains("<body", ignoreCase = true)
        val text = if (looksHtml) htmlToText(raw) else raw
        val clipped = if (text.length > maxChars) {
            text.take(maxChars) + "\n…[已截断，原文 ${text.length} 字符]"
        } else {
            text
        }
        if (clipped.isBlank()) throw IllegalStateException("页面没有可读正文：$normalized")
        return clipped
    }

    private fun get(url: String, maxBytes: Long = 700_000): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("响应体为空")
            return body.peekBody(maxBytes).string()
        }
    }

    private fun parseResults(html: String, limit: Int): List<SearchResult> {
        val primary = TITLE_LINK.findAll(html).map { it.groupValues[1] to it.groupValues[2] }.toList()
        val pairs = if (primary.isNotEmpty()) {
            primary
        } else {
            TITLE_LINK_ALT.findAll(html).map { it.groupValues[1] to it.groupValues[2] }.toList()
        }
        val snippets = SNIPPET.findAll(html).map { cleanInline(it.groupValues[1]) }.toList()
        return pairs.take(limit).mapIndexed { index, pair ->
            SearchResult(
                title = cleanInline(pair.second),
                url = unwrapRedirect(pair.first),
                snippet = snippets.getOrElse(index) { "" }
            )
        }
    }

    private fun unwrapRedirect(href: String): String {
        val marker = "uddg="
        val position = href.indexOf(marker)
        if (position < 0) {
            return if (href.startsWith("//")) "https:$href" else href
        }
        val raw = href.substring(position + marker.length).substringBefore('&')
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    private fun cleanInline(html: String): String =
        decodeEntities(html.replace(Regex("<[^>]+>"), " ")).replace(Regex("\\s+"), " ").trim()

    private fun htmlToText(html: String): String {
        var text = html
        text = Regex("(?is)<script.*?</script>").replace(text, " ")
        text = Regex("(?is)<style.*?</style>").replace(text, " ")
        text = Regex("(?is)<noscript.*?</noscript>").replace(text, " ")
        text = Regex("(?is)<!--.*?-->").replace(text, " ")
        text = Regex("(?i)<br\\s*/?>").replace(text, "\n")
        text = Regex("(?i)</(p|div|h[1-6]|li|tr|section|article|header|footer|blockquote)>").replace(text, "\n")
        text = Regex("(?i)<li[^>]*>").replace(text, "\n- ")
        text = Regex("<[^>]+>").replace(text, " ")
        text = decodeEntities(text)
        text = text.lines()
            .map { it.trim().replace(Regex("[ \\t]+"), " ") }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        return Regex("\n{3,}").replace(text, "\n\n").trim()
    }

    private fun decodeEntities(input: String): String {
        var out = input
        out = out.replace("&nbsp;", " ")
        out = out.replace("&amp;", "&")
        out = out.replace("&lt;", "<")
        out = out.replace("&gt;", ">")
        out = out.replace("&quot;", "\"")
        out = out.replace("&#39;", "'")
        out = out.replace("&apos;", "'")
        out = Regex("""&#(\d+);""").replace(out) { match ->
            runCatching { match.groupValues[1].toInt().toChar().toString() }.getOrDefault(match.value)
        }
        out = Regex("""&#x([0-9a-fA-F]+);""").replace(out) { match ->
            runCatching { match.groupValues[1].toInt(16).toChar().toString() }.getOrDefault(match.value)
        }
        return out
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

        private val TITLE_LINK = Regex(
            """<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val TITLE_LINK_ALT = Regex(
            """<a[^>]*href="([^"]+)"[^>]*class="result__a"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val SNIPPET = Regex(
            """<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
    }
}