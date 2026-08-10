package com.hooplog.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker {
    fun check(owner: String, repo: String, currentVersion: String): UpdateInfo {
        val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 8000
            readTimeout = 8000
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val tag = json.getString("tag_name")
        val releaseUrl = json.getString("html_url")
        return UpdateInfo(
            latestVersion = tag,
            releaseUrl = releaseUrl,
            isNewer = normalize(tag) != normalize(currentVersion)
        )
    }

    fun openRelease(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun normalize(value: String): String = value.trim().removePrefix("v")
}
