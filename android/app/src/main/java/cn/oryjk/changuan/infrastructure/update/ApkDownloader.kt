package cn.oryjk.changuan.infrastructure.update

import cn.oryjk.changuan.domain.AppUpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ApkDownloader(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun download(
        info: AppUpdateInfo,
        dir: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val urls = listOfNotNull(info.apkUrl, info.fallbackApkUrl)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (urls.isEmpty()) throw IOException("缺少安装包下载地址")

        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "changuan-update-${info.versionCode}.apk")
        val partial = File(dir, "changuan-update-${info.versionCode}.apk.part")

        var lastError: Exception? = null
        for (url in urls) {
            try {
                downloadFromUrl(url, info, target, partial, onProgress)
                return@withContext target
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                target.delete()
                lastError = error
            }
        }
        throw lastError ?: IOException("下载更新失败")
    }

    private suspend fun downloadFromUrl(
        url: String,
        info: AppUpdateInfo,
        target: File,
        partial: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ) {
        partial.delete()
        try {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("下载更新失败 (${response.code})，请稍后重试")
                val body = response.body ?: throw IOException("下载响应为空")
                val total = body.contentLength().takeIf { it > 0 } ?: info.fileSizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
                if (total != null && downloaded != total) throw IOException("安装包下载不完整")
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (info.sha256 != null && !actual.equals(info.sha256, ignoreCase = true)) {
                    throw IOException("安装包 SHA-256 校验失败")
                }
            }
            if (!partial.renameTo(target)) throw IOException("无法保存安装包")
        } finally {
            partial.delete()
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
