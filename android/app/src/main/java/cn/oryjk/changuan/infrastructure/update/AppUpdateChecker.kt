package cn.oryjk.changuan.infrastructure.update

import cn.oryjk.changuan.BuildConfig
import cn.oryjk.changuan.domain.AppUpdateInfo
import cn.oryjk.changuan.domain.ReleaseHistoryItem
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class AppUpdateChecker(
    private val metadataUrl: String = DEFAULT_METADATA_URL,
    private val downloadPageUrl: String = DEFAULT_DOWNLOAD_PAGE_URL,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    private val fallbackMetadataUrl: String? =
        if (metadataUrl == DEFAULT_METADATA_URL) FALLBACK_METADATA_URL else null,
    private val fallbackDownloadPageUrl: String? =
        if (downloadPageUrl == DEFAULT_DOWNLOAD_PAGE_URL) FALLBACK_DOWNLOAD_PAGE_URL else null,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    private val gson = Gson()

    private fun buildEndpoints(): List<UpdateEndpoint> = buildList {
        add(UpdateEndpoint(metadataUrl, downloadPageUrl))
        val fallbackUrl = fallbackMetadataUrl?.takeIf { it.isNotBlank() && it != metadataUrl }
        if (fallbackUrl != null) {
            add(
                UpdateEndpoint(
                    metadataUrl = fallbackUrl,
                    downloadPageUrl = fallbackDownloadPageUrl?.takeIf { it.isNotBlank() }
                        ?: fallbackUrl.substringBeforeLast('/', fallbackUrl) + "/",
                ),
            )
        }
    }

    suspend fun checkLatest(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val endpoints = buildEndpoints()
        var lastError: Exception? = null
        for ((index, endpoint) in endpoints.withIndex()) {
            try {
                return@withContext when (val result = checkEndpoint(endpoint)) {
                    is ParsedMetadata.UpToDate -> null
                    is ParsedMetadata.UpdateAvailable -> {
                        val fallbackEndpoint = endpoints.getOrNull(index + 1)
                        if (fallbackEndpoint != null) {
                            result.info.copy(
                                fallbackApkUrl = resolveApkUrl(fallbackEndpoint.metadataUrl, result.releaseFile),
                                fallbackDownloadPageUrl = fallbackEndpoint.downloadPageUrl,
                            )
                        } else {
                            result.info
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw IOException("无法检查更新，请稍后重试", lastError)
    }

    suspend fun fetchReleaseHistory(): List<ReleaseHistoryItem> = withContext(Dispatchers.IO) {
        val endpoints = buildEndpoints()
        var lastError: Exception? = null
        for (endpoint in endpoints) {
            try {
                val historyUrl = endpoint.metadataUrl.toHttpUrlOrNull()
                    ?.resolve("releases.json")
                    ?.toString()
                    ?: throw IOException("历史版本地址无效")
                val request = Request.Builder()
                    .url(historyUrl)
                    .header("Cache-Control", "no-cache")
                    .build()
                return@withContext httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("无法获取历史版本 (${response.code})")
                    val bodyText = response.body?.string() ?: throw IOException("历史版本为空")
                    parseReleaseHistoryStrict(json = bodyText, gson = gson)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw IOException("无法加载历史版本记录，请稍后重试", lastError)
    }

    private fun checkEndpoint(endpoint: UpdateEndpoint): ParsedMetadata {
        val request = Request.Builder()
            .url(endpoint.metadataUrl)
            .header("Cache-Control", "no-cache")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("无法获取更新信息 (${response.code})")
            val bodyText = response.body?.string() ?: throw IOException("更新信息为空")
            parseMetadataStrict(
                json = bodyText,
                currentVersionCode = currentVersionCode,
                metadataUrl = endpoint.metadataUrl,
                downloadPageUrl = endpoint.downloadPageUrl,
                gson = gson,
            )
        }
    }

    internal data class UpdateEndpoint(
        val metadataUrl: String,
        val downloadPageUrl: String,
    )

    internal sealed interface ParsedMetadata {
        data class UpdateAvailable(val info: AppUpdateInfo, val releaseFile: String?) : ParsedMetadata
        data object UpToDate : ParsedMetadata
    }

    internal data class MetadataDto(
        val versionName: String? = null,
        val versionCode: String? = null,
        val publishedAt: String? = null,
        val gitCommit: String? = null,
        val releaseNotes: List<String>? = null,
        val releaseFile: String? = null,
        val sha256: String? = null,
        val fileSizeBytes: Long? = null,
        val apkAvailable: Boolean? = null,
    )

    companion object {
        const val DEFAULT_DOWNLOAD_PAGE_URL = "https://ai.oryjk.cn:82/" + BuildConfig.UPDATE_DIRECTORY
        const val DEFAULT_METADATA_URL = DEFAULT_DOWNLOAD_PAGE_URL + "metadata.json"
        const val FALLBACK_DOWNLOAD_PAGE_URL = "http://172.16.60.233/" + BuildConfig.UPDATE_DIRECTORY
        const val FALLBACK_METADATA_URL = FALLBACK_DOWNLOAD_PAGE_URL + "metadata.json"

        internal fun resolveApkUrl(metadataUrl: String, releaseFile: String?): String? =
            metadataUrl.toHttpUrlOrNull()
                ?.resolve(releaseFile?.takeIf { it.isNotBlank() } ?: "latest.apk")
                ?.toString()

        internal fun parseMetadataStrict(
            json: String,
            currentVersionCode: Int,
            metadataUrl: String = DEFAULT_METADATA_URL,
            downloadPageUrl: String = DEFAULT_DOWNLOAD_PAGE_URL,
            gson: Gson = Gson(),
        ): ParsedMetadata {
            val metadata = try {
                gson.fromJson(json, MetadataDto::class.java)
            } catch (error: Exception) {
                throw IOException("更新信息格式异常", error)
            } ?: throw IOException("更新信息为空")
            val remoteCode = metadata.versionCode?.toIntOrNull()
                ?: throw IOException("更新版本号无效")
            if (remoteCode <= currentVersionCode) return ParsedMetadata.UpToDate
            return ParsedMetadata.UpdateAvailable(
                info = AppUpdateInfo(
                    versionName = metadata.versionName ?: "未知版本",
                    versionCode = remoteCode,
                    publishedAt = metadata.publishedAt,
                    gitCommit = metadata.gitCommit,
                    releaseNotes = metadata.releaseNotes.orEmpty(),
                    downloadPageUrl = downloadPageUrl,
                    apkUrl = resolveApkUrl(metadataUrl, metadata.releaseFile),
                    sha256 = metadata.sha256?.lowercase(),
                    fileSizeBytes = metadata.fileSizeBytes,
                ),
                releaseFile = metadata.releaseFile,
            )
        }

        internal fun parseAndCompareMetadata(
            json: String,
            currentVersionCode: Int,
            metadataUrl: String = DEFAULT_METADATA_URL,
            downloadPageUrl: String = DEFAULT_DOWNLOAD_PAGE_URL,
            gson: Gson = Gson(),
        ): AppUpdateInfo? = runCatching {
            (parseMetadataStrict(
                json = json,
                currentVersionCode = currentVersionCode,
                metadataUrl = metadataUrl,
                downloadPageUrl = downloadPageUrl,
                gson = gson,
            ) as? ParsedMetadata.UpdateAvailable)?.info
        }.getOrNull()

        internal fun parseReleaseHistoryStrict(
            json: String,
            gson: Gson = Gson(),
        ): List<ReleaseHistoryItem> {
            val raw = try {
                gson.fromJson(json, Array<MetadataDto>::class.java)
            } catch (error: Exception) {
                throw IOException("历史版本格式异常", error)
            } ?: throw IOException("历史版本为空")
            return raw.mapNotNull { dto ->
                val code = dto.versionCode?.toIntOrNull() ?: return@mapNotNull null
                ReleaseHistoryItem(
                    versionName = dto.versionName?.takeIf { it.isNotBlank() } ?: "未知版本",
                    versionCode = code,
                    publishedAt = dto.publishedAt,
                    gitCommit = dto.gitCommit,
                    releaseNotes = dto.releaseNotes.orEmpty().filter { it.isNotBlank() },
                    apkAvailable = dto.apkAvailable ?: !dto.releaseFile.isNullOrBlank(),
                )
            }.sortedWith(
                compareByDescending<ReleaseHistoryItem> { it.versionCode }
                    .thenByDescending { it.publishedAt.orEmpty() },
            )
        }
    }
}
