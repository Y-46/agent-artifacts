package dev.harness.mobile.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

fun fmtTime(ms: Long): String {
    if (ms <= 0L) return "-"
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
}

fun fmtDateTime(ms: Long): String {
    if (ms <= 0L) return "-"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ms))
}

fun fmtDuration(ms: Long): String {
    if (ms < 0) return "-"
    val s = ms / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m ${s % 60}s"
        else -> "${s / 3600}h ${(s % 3600) / 60}m"
    }
}

fun fmtRelative(ms: Long): String {
    if (ms <= 0L) return "-"
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        else -> fmtTime(ms)
    }
}

fun snippet(text: String, max: Int = 120): String {
    val one = text.replace(Regex("\\s+"), " ").trim()
    return if (one.length <= max) one else one.take(max) + "…"
}