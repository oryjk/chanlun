package cn.oryjk.changuan.infrastructure.update

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import cn.oryjk.changuan.BuildConfig
import cn.oryjk.changuan.domain.ApkUpdateIdentity
import cn.oryjk.changuan.domain.AppUpdateInfo
import cn.oryjk.changuan.domain.InstalledApkIdentity
import cn.oryjk.changuan.domain.isValidApkDigest
import cn.oryjk.changuan.domain.validateSelfUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ShizukuApkInstaller(private val context: Context) {
    private val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuApkUserService::class.java))
        .tag("changuan-self-update-${Process.myUid() / 100_000}")
        .processNameSuffix("update_installer")
        .version(BuildConfig.VERSION_CODE)
        .daemon(true)

    suspend fun install(apk: File, info: AppUpdateInfo) = withContext(Dispatchers.IO) {
        require(isValidApkDigest(info.sha256)) { "本次更新缺少有效 SHA-256，无法使用 Shizuku" }
        val manager = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        @Suppress("DEPRECATION")
        val current = manager.getPackageInfo(context.packageName, flags)
        @Suppress("DEPRECATION")
        val candidate = manager.getPackageArchiveInfo(apk.absolutePath, flags) ?: error("无法读取安装包")
        validateSelfUpdate(
            InstalledApkIdentity(current.packageName, current.version(), current.signers()),
            ApkUpdateIdentity(candidate.packageName, candidate.version(), candidate.signers(), apk.length()),
            info.versionCode,
            info.fileSizeBytes,
        )
        val actual = apk.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        check(actual.equals(info.sha256, true)) { "安装包 SHA-256 校验失败" }
        var connection: ServiceConnection? = null
        try {
            val binder = withContext(Dispatchers.Main.immediate) {
                withTimeout(15_000) {
                    suspendCancellableCoroutine<IBinder> { continuation ->
                        val callback = object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                                if (continuation.isActive) {
                                    if (service != null && service.pingBinder()) continuation.resume(service)
                                    else continuation.resumeWithException(IllegalStateException("Shizuku 安装服务未就绪"))
                                }
                            }

                            override fun onServiceDisconnected(name: ComponentName?) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(IllegalStateException("Shizuku 安装服务已断开"))
                                }
                            }
                        }
                        connection = callback
                        try {
                            Shizuku.bindUserService(args, callback)
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                }
            }
            ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(UpdateInstallerProtocol.DESCRIPTOR)
                    data.writeInt(1)
                    descriptor.writeToParcel(data, 0)
                    data.writeLong(apk.length())
                    data.writeString(info.sha256)
                    check(binder.transact(UpdateInstallerProtocol.INSTALL, data, reply, 0)) {
                        "Shizuku 安装服务不支持本次请求"
                    }
                    reply.readException()
                    val status = reply.readInt()
                    val message = reply.readString()
                    check(status == 0) { message ?: "Shizuku 安装失败" }
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        } finally {
            withContext(Dispatchers.Main.immediate + NonCancellable) {
                connection?.let { runCatching { Shizuku.unbindUserService(args, it, true) } }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun PackageInfo.version(): Long =
    if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

@Suppress("DEPRECATION")
private fun PackageInfo.signers(): Set<String> {
    val certificates = if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures
    return certificates.orEmpty().map { signature ->
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }.toSet()
}
