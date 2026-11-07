package com.danza.noticontrol.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object AppUpdater {

    private const val GITHUB_API = "https://api.github.com/repos/DanzaMenu/NotiControl/releases/latest"

    fun getCurrentVersion(context: Context): String {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    }

    private fun parseVersion(v: String): List<Int> {
        return v.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = parseVersion(remote)
        val l = parseVersion(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(GITHUB_API).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "NotiControl-Updater")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val obj = JSONObject(json)
            val tag = obj.optString("tag_name", "").removePrefix("v")
            val notes = obj.optString("body", "")
            val assets = obj.optJSONArray("assets")

            if (tag.isEmpty() || assets == null) return@withContext null

            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }

            if (apkUrl == null) return@withContext null

            val currentVersion = getCurrentVersion(context)
            if (isNewer(tag, currentVersion)) {
                UpdateInfo(tag, apkUrl, notes)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getApkFile(context: Context): File {
        val updateDir = File(context.cacheDir, "updates")
        updateDir.mkdirs()
        return File(updateDir, "update.apk")
    }

    private fun launchInstallIntent(context: Context, apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun hasCachedApk(context: Context): Boolean {
        return getApkFile(context).exists()
    }

    fun installCachedApk(context: Context) {
        val apkFile = getApkFile(context)
        if (apkFile.exists()) {
            launchInstallIntent(context, apkFile)
        }
    }

    suspend fun downloadAndInstall(context: Context, url: String) {
        withContext(Dispatchers.IO) {
            val apkFile = getApkFile(context)

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "NotiControl-Updater")
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            conn.disconnect()

            withContext(Dispatchers.Main) {
                launchInstallIntent(context, apkFile)
            }
        }
    }
}
