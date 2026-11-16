package cn.oryjk.changuan.infrastructure.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import cn.oryjk.changuan.application.AppUpdateDownloader
import cn.oryjk.changuan.application.AppUpdateInstaller
import cn.oryjk.changuan.application.AppUpdateReader
import cn.oryjk.changuan.domain.AppUpdateInfo
import cn.oryjk.changuan.domain.DownloadedApk
import cn.oryjk.changuan.domain.ReleaseHistoryItem
import cn.oryjk.changuan.domain.ShizukuAvailability
import cn.oryjk.changuan.domain.ShizukuChannelState
import cn.oryjk.changuan.domain.SystemInstallResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import rikka.shizuku.Shizuku
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AndroidUpdateGateway(
    context: Context,
    private val checkerClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val downloaderClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val checker: AppUpdateChecker = AppUpdateChecker(httpClient = checkerClient),
    private val downloader: ApkDownloader = ApkDownloader(httpClient = downloaderClient),
) : AppUpdateReader, AppUpdateDownloader, AppUpdateInstaller {
    private val application = context.applicationContext
    private val directory = ApkInstaller.updatesDir(application)
    private val preferences = application.getSharedPreferences("update_install_channel", Context.MODE_PRIVATE)
    private val mutableChannel = MutableStateFlow(ShizukuChannelState(preferences.getBoolean("shizuku", false)))
    override val channelState: StateFlow<ShizukuChannelState> = mutableChannel.asStateFlow()

    private val downloads = ConcurrentHashMap<String, File>()
    private val received = Shizuku.OnBinderReceivedListener { refreshShizukuState() }
    private val died = Shizuku.OnBinderDeadListener { refreshShizukuState() }
    private val permission = Shizuku.OnRequestPermissionResultListener { request, _ ->
        if (request == PERMISSION_REQUEST) refreshShizukuState()
    }
    @Volatile
    private var closed = false

    init {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(received)
            Shizuku.addBinderDeadListener(died)
            Shizuku.addRequestPermissionResultListener(permission)
        }
        refreshShizukuState()
    }

    override suspend fun checkLatest(): AppUpdateInfo? = checker.checkLatest()

    override suspend fun fetchReleaseHistory(): List<ReleaseHistoryItem> = checker.fetchReleaseHistory()

    override suspend fun download(
        info: AppUpdateInfo,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): DownloadedApk {
        val file = downloader.download(info, directory, onProgress)
        currentCoroutineContext().ensureActive()
        downloads.clear()
        val handle = DownloadedApk(UUID.randomUUID().toString())
        downloads[handle.id] = file
        return handle
    }

    override fun setShizukuEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("shizuku", enabled).apply()
        mutableChannel.value = mutableChannel.value.copy(enabled = enabled)
        refreshShizukuState()
    }

    override fun refreshShizukuState() {
        if (closed) return
        val availability = try {
            when {
                !Shizuku.pingBinder() -> {
                    @Suppress("DEPRECATION")
                    val installed = runCatching {
                        application.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
                    }.isSuccess
                    if (installed) ShizukuAvailability.NotRunning else ShizukuAvailability.NotInstalled
                }
                Shizuku.isPreV11() -> ShizukuAvailability.Unsupported
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuAvailability.Ready
                Shizuku.shouldShowRequestPermissionRationale() -> ShizukuAvailability.PermissionDenied
                else -> ShizukuAvailability.NeedsAuthorization
            }
        } catch (_: Exception) {
            ShizukuAvailability.NotRunning
        }
        mutableChannel.value = mutableChannel.value.copy(availability = availability)
    }

    override fun requestShizukuPermission() {
        runCatching {
            refreshShizukuState()
            if (mutableChannel.value.availability == ShizukuAvailability.NeedsAuthorization) {
                Shizuku.requestPermission(PERMISSION_REQUEST)
            }
        }
    }

    override fun openShizuku() {
        val launch = application.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        application.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override suspend fun installShizuku(apk: DownloadedApk, info: AppUpdateInfo) {
        refreshShizukuState()
        check(mutableChannel.value.useShizuku) { "Shizuku 尚未就绪，可改用系统安装" }
        ShizukuApkInstaller(application).install(resolve(apk), info)
    }

    override fun installWithSystem(apk: DownloadedApk): SystemInstallResult {
        val file = resolve(apk)
        return if (ApkInstaller.canInstall(application)) {
            ApkInstaller.install(application, file)
            SystemInstallResult.Opened
        } else {
            ApkInstaller.openInstallPermissionSettings(application)
            SystemInstallResult.PermissionRequired
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(died)
            Shizuku.removeRequestPermissionResultListener(permission)
        }
    }

    private fun resolve(apk: DownloadedApk): File = downloads[apk.id]?.takeIf { it.isFile }
        ?: throw IllegalStateException("安装包已失效，请重新下载")

    private companion object {
        const val PERMISSION_REQUEST = 703
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
