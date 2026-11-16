package cn.oryjk.changuan.domain

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val publishedAt: String?,
    val gitCommit: String?,
    val releaseNotes: List<String>,
    val downloadPageUrl: String,
    val apkUrl: String? = null,
    val sha256: String? = null,
    val fileSizeBytes: Long? = null,
    val fallbackApkUrl: String? = null,
    val fallbackDownloadPageUrl: String? = null,
)

data class ReleaseHistoryItem(
    val versionName: String,
    val versionCode: Int,
    val publishedAt: String?,
    val gitCommit: String?,
    val releaseNotes: List<String>,
    val apkAvailable: Boolean = false,
)

/** Opaque handle for a verified downloaded APK held inside infrastructure. */
data class DownloadedApk(val id: String)

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val downloaded: Long, val total: Long?) : UpdateDownloadState
    data class Ready(val apk: DownloadedApk) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

enum class ShizukuAvailability {
    NotInstalled,
    NotRunning,
    NeedsAuthorization,
    PermissionDenied,
    Ready,
    Unsupported,
}

data class ShizukuChannelState(
    val enabled: Boolean = false,
    val availability: ShizukuAvailability = ShizukuAvailability.NotRunning,
) {
    val useShizuku: Boolean
        get() = enabled && availability == ShizukuAvailability.Ready
}

enum class SystemInstallResult {
    Opened,
    PermissionRequired,
}

sealed interface ApkInstallState {
    data object Idle : ApkInstallState
    data object Installing : ApkInstallState
    data object Submitted : ApkInstallState
    data object NeedsSystemPermission : ApkInstallState
    data class Failed(val message: String) : ApkInstallState
}

data class InstalledApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val signers: Set<String>,
)

data class ApkUpdateIdentity(
    val packageName: String,
    val versionCode: Long,
    val signers: Set<String>,
    val size: Long,
)

internal const val MAX_UPDATE_APK_BYTES = 256L * 1024 * 1024

fun isValidApkDigest(value: String?): Boolean =
    value?.matches(Regex("[a-fA-F0-9]{64}")) == true

fun validateSelfUpdate(
    current: InstalledApkIdentity,
    apk: ApkUpdateIdentity,
    expectedVersion: Int,
    expectedSize: Long?,
) {
    require(apk.packageName == current.packageName) { "安装包不属于缠观" }
    require(apk.versionCode > current.versionCode && apk.versionCode == expectedVersion.toLong()) {
        "安装包版本与本次更新不符"
    }
    require(current.signers.isNotEmpty() && apk.signers == current.signers) {
        "安装包签名与当前应用不一致"
    }
    require(
        apk.size in 1..MAX_UPDATE_APK_BYTES &&
            (expectedSize == null || apk.size == expectedSize),
    ) {
        "安装包大小与更新信息不符"
    }
}
