package com.filesflow.transfer

internal object LanTransferLandingPage {
    data class Item(
        val name: String,
        val sizeBytes: Long,
        val path: String,
    )

    fun render(items: List<Item>): String = buildString {
        append("<!doctype html><html lang=\"en\"><head>")
        append("<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<meta name=\"robots\" content=\"noindex,nofollow,noarchive\">")
        append("<title>FilesFlow transfer</title>")
        append("<style>")
        append("body{margin:0;background:#f6f2e9;color:#26231f;font-family:system-ui,-apple-system,sans-serif}")
        append("main{max-width:720px;margin:auto;padding:32px 20px 56px}")
        append("h1{font-size:2rem;margin:0 0 8px}p{color:#645f57;margin:0 0 28px}")
        append(".file{display:flex;align-items:center;gap:16px;background:#fff;border-radius:18px;padding:18px;margin:12px 0;box-shadow:0 8px 28px #0000000d}")
        append(".meta{min-width:0;flex:1}.name{font-weight:700;overflow-wrap:anywhere}.size{font-size:.9rem;color:#777067;margin-top:4px}")
        append("a{display:inline-block;text-decoration:none;background:#2e4938;color:white;padding:11px 16px;border-radius:12px;font-weight:700}")
        append("footer{margin-top:28px;font-size:.9rem;color:#777067}")
        append("</style></head><body><main>")
        append("<h1>FilesFlow transfer</h1><p>${items.size} ${if (items.size == 1) "file" else "files"} available on this local network.</p>")
        items.forEach { item ->
            append("<section class=\"file\"><div class=\"meta\"><div class=\"name\">")
            append(escape(item.name))
            append("</div><div class=\"size\">")
            append(formatBytes(item.sizeBytes))
            append("</div></div><a href=\"")
            append(escapeAttribute(item.path))
            append("\" download>Download</a></section>")
        }
        append("<footer>Keep the sender screen open. This private link expires automatically.</footer>")
        append("</main></body></html>")
    }

    internal fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun escapeAttribute(value: String): String = escape(value)

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "Size unavailable"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit += 1
        }
        return if (unit == 0) "$bytes ${units[unit]}" else "%.1f %s".format(java.util.Locale.US, value, units[unit])
    }
}
