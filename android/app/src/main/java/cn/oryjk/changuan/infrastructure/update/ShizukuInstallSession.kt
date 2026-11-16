package cn.oryjk.changuan.infrastructure.update

import cn.oryjk.changuan.domain.MAX_UPDATE_APK_BYTES
import cn.oryjk.changuan.domain.isValidApkDigest
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class InstallCommandResult(
    val exitCode: Int,
    val output: String,
    val bytesWritten: Long = 0,
    val sha256: String? = null,
)

internal fun interface InstallCommandRunner {
    fun run(arguments: List<String>, input: InputStream?): InstallCommandResult
}

internal class ShizukuInstallSession(
    private val runner: InstallCommandRunner = LocalInstallCommandRunner(),
) {
    fun install(
        input: InputStream,
        size: Long,
        sha256: String,
        packageName: String,
        userId: Int,
    ) {
        require(size in 1..MAX_UPDATE_APK_BYTES && isValidApkDigest(sha256)) { "安装包校验信息无效" }
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_.]+")) && userId >= 0)
        val created = runner.run(
            listOf(PM, "install-create", "-r", "--user", "$userId", "--pkg", packageName, "-S", "$size"),
            null,
        )
        check(created.exitCode == 0) { "无法创建安装会话：${created.output.take(500)}" }
        val session = Regex("Success: created install session \\[(\\d+)]")
            .find(created.output)?.groupValues?.get(1)
            ?: error("系统未返回有效安装会话：${created.output.take(500)}")
        var committed = false
        try {
            val written = runner.run(
                listOf(PM, "install-write", "-S", "$size", session, "base.apk", "-"),
                input,
            )
            check(written.exitCode == 0 && written.bytesWritten == size) {
                "安装包写入失败：${written.output.take(500)}"
            }
            check(written.sha256.equals(sha256, ignoreCase = true)) {
                "安装包 SHA-256 校验失败，已取消安装"
            }
            val result = runner.run(listOf(PM, "install-commit", session), null)
            check(result.exitCode == 0 && Regex("(?m)^Success\\s*$").containsMatchIn(result.output)) {
                "系统拒绝安装：${result.output.take(500)}"
            }
            committed = true
        } finally {
            if (!committed) runCatching { runner.run(listOf(PM, "install-abandon", session), null) }
        }
    }

    private companion object {
        const val PM = "/system/bin/pm"
    }
}

internal class LocalInstallCommandRunner(
    private val timeoutMillis: Long = 30_000,
    private val startProcess: (List<String>) -> Process = {
        ProcessBuilder(it).redirectErrorStream(true).start()
    },
) : InstallCommandRunner {
    override fun run(arguments: List<String>, input: InputStream?): InstallCommandResult {
        val process = startProcess(arguments)
        val executor = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "apk-install-io").apply { isDaemon = true }
        }
        val output = executor.submit<String> {
            val retained = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(1024)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (retained.length < 16_384) {
                        retained.append(buffer, 0, minOf(count, 16_384 - retained.length))
                    }
                }
            }
            retained.toString()
        }
        val transfer = executor.submit<Pair<Long, String?>> {
            var bytes = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            process.outputStream.use { sink ->
                if (input != null) {
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count
                        check(bytes <= MAX_UPDATE_APK_BYTES) { "安装包超过大小限制" }
                        sink.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                }
            }
            bytes to if (input != null) digest.digest().joinToString("") { "%02x".format(it) } else null
        }
        try {
            check(process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) { "系统安装超时，请重试或使用系统安装" }
            val (bytes, hash) = transfer.get(2, TimeUnit.SECONDS)
            return InstallCommandResult(process.exitValue(), output.get(2, TimeUnit.SECONDS), bytes, hash)
        } finally {
            process.destroyForcibly()
            runCatching { process.outputStream.close() }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            transfer.cancel(true)
            output.cancel(true)
            executor.shutdownNow()
        }
    }
}
