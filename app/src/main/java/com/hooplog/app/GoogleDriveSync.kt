package com.hooplog.app

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GoogleDriveSync {
    private const val DriveScope = "https://www.googleapis.com/auth/drive.appdata"
    private const val BackupName = "hooplog-backup.json"

    val signInOptions: GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScope))
            .build()

    fun lastSignedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)?.takeIf {
            GoogleSignIn.hasPermissions(it, Scope(DriveScope))
        }

    suspend fun upload(context: Context, account: GoogleSignInAccount, json: String): String = withContext(Dispatchers.IO) {
        val token = accessToken(context, account)
        val fileId = findBackupFile(token)
        if (fileId == null) createBackupFile(token, json) else updateBackupFile(token, fileId, json)
        account.email.orEmpty()
    }

    suspend fun download(context: Context, account: GoogleSignInAccount): String = withContext(Dispatchers.IO) {
        val token = accessToken(context, account)
        val fileId = findBackupFile(token) ?: error("Google Drive 尚未有 HoopLog 備份")
        request("https://www.googleapis.com/drive/v3/files/$fileId?alt=media", "GET", token)
    }

    private fun accessToken(context: Context, account: GoogleSignInAccount): String {
        val androidAccount = account.account ?: error("找不到 Google 帳號")
        return GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$DriveScope")
    }

    private fun findBackupFile(token: String): String? {
        val query = "name='$BackupName' and trashed=false"
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name)"
        val body = request(url, "GET", token)
        val files = JSONObject(body).optJSONArray("files") ?: return null
        return if (files.length() == 0) null else files.getJSONObject(0).getString("id")
    }

    private fun createBackupFile(token: String, json: String) {
        val metadata = JSONObject()
            .put("name", BackupName)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .toString()
        multipartRequest("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", "POST", token, metadata, json)
    }

    private fun updateBackupFile(token: String, fileId: String, json: String) {
        multipartRequest("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=multipart", "PATCH", token, null, json)
    }

    private fun request(url: String, method: String, token: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15000
            readTimeout = 20000
        }
        return connection.response()
    }

    private fun multipartRequest(url: String, method: String, token: String, metadata: String?, json: String) {
        val boundary = "HoopLogBoundary"
        val body = buildString {
            metadata?.let {
                append("--$boundary\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append(it)
                append("\r\n")
            }
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(json)
            append("\r\n--$boundary--\r\n")
        }.toByteArray(Charsets.UTF_8)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            setRequestProperty("Content-Length", body.size.toString())
            connectTimeout = 15000
            readTimeout = 20000
        }
        connection.outputStream.use { it.write(body) }
        connection.response()
    }

    private fun HttpURLConnection.response(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (responseCode !in 200..299) error("Google 同步失敗 ($responseCode): $body")
        return body
    }
}
