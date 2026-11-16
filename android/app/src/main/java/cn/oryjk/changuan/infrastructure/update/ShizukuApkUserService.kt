package cn.oryjk.changuan.infrastructure.update

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import cn.oryjk.changuan.BuildConfig
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

internal object UpdateInstallerProtocol {
    const val DESCRIPTOR = "cn.oryjk.changuan.SelfUpdateInstaller"
    const val INSTALL = IBinder.FIRST_CALL_TRANSACTION
    const val DESTROY = 16777115
}

class ShizukuApkUserService : Binder() {
    private val installing = AtomicBoolean(false)
    private val cleanup = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "installer-cleanup").apply { isDaemon = true }
    }

    init {
        attachInterface(null, UpdateInstallerProtocol.DESCRIPTOR)
        cleanup.schedule({ exitProcess(0) }, 180, TimeUnit.SECONDS)
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == INTERFACE_TRANSACTION) {
            reply?.writeString(UpdateInstallerProtocol.DESCRIPTOR)
            return true
        }
        if (code == UpdateInstallerProtocol.DESTROY) {
            if (!installing.get()) cleanup.schedule({ exitProcess(0) }, 1, TimeUnit.SECONDS)
            reply?.writeNoException()
            return true
        }
        if (code != UpdateInstallerProtocol.INSTALL) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(UpdateInstallerProtocol.DESCRIPTOR)
        if (!installing.compareAndSet(false, true)) {
            reply?.writeNoException()
            reply?.writeInt(1)
            reply?.writeString("已有安装正在进行")
            return true
        }
        var descriptor: ParcelFileDescriptor? = null
        var failure: String? = null
        try {
            require(data.readInt() == 1) { "缺少安装包" }
            descriptor = ParcelFileDescriptor.CREATOR.createFromParcel(data)
            val size = data.readLong()
            val digest = data.readString().orEmpty()
            val uid = getCallingUid()
            require(uid % 100_000 >= 10_000) { "无效的安装请求来源" }
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
                ShizukuInstallSession().install(stream, size, digest, BuildConfig.APPLICATION_ID, uid / 100_000)
            }
        } catch (error: Exception) {
            failure = error.message?.take(500) ?: "Shizuku 安装失败"
        } finally {
            runCatching { descriptor?.close() }
            installing.set(false)
            cleanup.schedule({ exitProcess(0) }, 5, TimeUnit.SECONDS)
        }
        reply?.writeNoException()
        reply?.writeInt(if (failure == null) 0 else 1)
        reply?.writeString(failure)
        return true
    }
}
