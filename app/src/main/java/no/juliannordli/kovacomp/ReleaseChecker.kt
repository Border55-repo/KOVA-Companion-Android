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
    private const val UPDATE_MANIFEST_URL =
        "https://raw.githubusercontent.com/Border55-repo/KOVA-Companion-Android/main/bridge/data/app-update.json"

    fun fetchLatest(): ReleaseInfo? {
        val url = URL(
            UPDATE_MANIFEST_URL + "?ts=" + System.currentTimeMillis()
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Cache-Control", "no-cache, no-store")
        connection.setRequestProperty("Pragma", "no-cache")
        connection.setRequestProperty("User-Agent", "KOVA Companion Android/0.9.4")

        try {
            val code = connection.responseCode
            if (code == 404) return null
            if (code !in 200..299) {
                error("Oppdateringsmanifest HTTP " + code)
            }

            val body = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            return parseManifest(body)
        } finally {
            connection.disconnect()
        }
    }

    fun parseManifest(body: String): ReleaseInfo {
        val root = JSONObject(body)

        val tagName = root.optString("tagName").trim()
        if (tagName.isBlank()) {
            error("Oppdateringsmanifest mangler tagName")
        }

        val htmlUrl = root.optString("htmlUrl").trim()
        if (htmlUrl.isBlank()) {
            error("Oppdateringsmanifest mangler htmlUrl")
        }

        val apkUrl = root.optString("apkUrl").trim().takeIf { it.isNotBlank() }

        return ReleaseInfo(
            tagName = tagName,
            name = root.optString("name").ifBlank { tagName },
            notes = root.optString("notes"),
            publishedAt = root.optString("publishedAt"),
            htmlUrl = htmlUrl,
            apkUrl = apkUrl
        )
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
