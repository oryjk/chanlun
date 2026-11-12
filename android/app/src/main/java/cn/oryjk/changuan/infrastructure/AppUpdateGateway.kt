package cn.oryjk.changuan.infrastructure

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import cn.oryjk.changuan.BuildConfig
import cn.oryjk.changuan.application.AppUpdateDownloader
import cn.oryjk.changuan.application.AppUpdateInstaller
import cn.oryjk.changuan.application.AppUpdateReader
import cn.oryjk.changuan.domain.AppUpdateInfo
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class AppUpdateGateway(
    private val context: Context? = null,
    private val metadataUrl: String = DEFAULT_METADATA_URL,
    private val downloadPageUrl: String = DEFAULT_DOWNLOAD_PAGE_URL,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : AppUpdateReader, AppUpdateDownloader, AppUpdateInstaller {

    override suspend fun checkLatest(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(metadataUrl)
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bodyText = response.body?.string() ?: return@runCatching null
                parseAndCompareMetadata(
                    json = bodyText,
                    currentVersionCode = currentVersionCode,
                    metadataUrl = metadataUrl,
                    downloadPageUrl = downloadPageUrl,
                )
            }
        }.getOrNull()
    }

    override suspend fun download(
        info: AppUpdateInfo,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val url = info.apkUrl ?: throw IOException("缺少安装包下载地址")
        val appCtx = context ?: throw IOException("未初始化应用上下文")
        val dir = File(appCtx.cacheDir, "updates")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "changuan-update-${info.versionCode}.apk")
        val partial = File(dir, "changuan-update-${info.versionCode}.apk.part")
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("下载失败 HTTP ${response.code}")
                val body = response.body ?: throw IOException("下载响应为空")
                val total = body.contentLength().takeIf { it > 0 } ?: info.fileSizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
                if (total != null && downloaded != total) {
                    throw IOException("安装包下载不完整")
                }
                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                if (info.sha256 != null && !actualSha.equals(info.sha256, ignoreCase = true)) {
                    throw IOException("安装包 SHA-256 校验失败")
                }
            }
            if (!partial.renameTo(target)) {
                throw IOException("无法保存安装包")
            }
            target.absolutePath
        } finally {
            partial.delete()
        }
    }

    override fun canInstallPackages(): Boolean =
        context?.packageManager?.canRequestPackageInstalls() ?: false

    override fun openInstallPermissionSettings() {
        val appCtx = context ?: return
        appCtx.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appCtx.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun installApk(apkPath: String) {
        val appCtx = context ?: return
        val apkFile = File(apkPath)
        if (!apkFile.exists()) return
        val uri = FileProvider.getUriForFile(
            appCtx,
            "${appCtx.packageName}.fileprovider",
            apkFile,
        )
        appCtx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        const val DEFAULT_DOWNLOAD_PAGE_URL = "http://172.16.60.233/chanlun-android/"
        const val DEFAULT_METADATA_URL = "http://172.16.60.233/chanlun-android/metadata.json"
        private const val BUFFER_SIZE = 64 * 1024

        internal fun parseAndCompareMetadata(
            json: String,
            currentVersionCode: Int,
            metadataUrl: String = DEFAULT_METADATA_URL,
            downloadPageUrl: String = DEFAULT_DOWNLOAD_PAGE_URL,
        ): AppUpdateInfo? {
            val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
                ?: return null
            val remoteCode = obj.optString("versionCode")?.toIntOrNull()
                ?: obj.optInt("versionCode")
                ?: return null
            if (remoteCode <= currentVersionCode) return null
            val releaseFile = obj.optString("releaseFile")?.takeIf { it.isNotBlank() } ?: "latest.apk"
            val notes = obj.optElement("releaseNotes")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
                .orEmpty()
            val fileSize = obj.optElement("fileSizeBytes")
                ?.takeIf { it.isJsonPrimitive }
                ?.asLong
            return AppUpdateInfo(
                versionName = obj.optString("versionName") ?: "未知版本",
                versionCode = remoteCode,
                publishedAt = obj.optString("publishedAt"),
                gitCommit = obj.optString("gitCommit"),
                releaseNotes = notes,
                downloadPageUrl = downloadPageUrl,
                apkUrl = metadataUrl.toHttpUrlOrNull()?.resolve(releaseFile)?.toString(),
                sha256 = obj.optString("sha256")?.lowercase(),
                fileSizeBytes = fileSize,
            )
        }
    }
}
