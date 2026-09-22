package no.juliannordli.kovacomp

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val notes: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkUrl: String?
) {
    fun isNewerThan(current: String): Boolean =
        ReleaseChecker.compareVersions(tagName, current) > 0
}

object ReleaseChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/Border55-repo/KOVA-Companion-Android/releases/latest"

    fun fetchLatest(): ReleaseInfo? {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "KOVA Companion Android/0.9.0")

        try {
            val code = connection.responseCode
            if (code == 404) return null
            if (code !in 200..299) error("GitHub releases HTTP " + code)

            val root = JSONObject(
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            )

            val assets = root.optJSONArray("assets")
            var apkUrl: String? = null

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }

            return ReleaseInfo(
                tagName = root.optString("tag_name"),
                name = root.optString("name").ifBlank { root.optString("tag_name") },
                notes = root.optString("body"),
                publishedAt = root.optString("published_at"),
                htmlUrl = root.optString("html_url"),
                apkUrl = apkUrl
            )
        } finally {
            connection.disconnect()
        }
    }

    fun compareVersions(a: String, b: String): Int {
        fun parts(value: String): List<Int> =
            value
                .trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore("-")
                .split(".")
                .map { it.toIntOrNull() ?: 0 }

        val left = parts(a)
        val right = parts(b)
        val size = maxOf(left.size, right.size)

        for (i in 0 until size) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }

        return 0
    }
}
