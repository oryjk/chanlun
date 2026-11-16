package cn.oryjk.changuan

import androidx.test.core.app.ApplicationProvider
import cn.oryjk.changuan.domain.ApkUpdateIdentity
import cn.oryjk.changuan.domain.AppUpdateInfo
import cn.oryjk.changuan.domain.BackendEnvironment
import cn.oryjk.changuan.domain.InstalledApkIdentity
import cn.oryjk.changuan.domain.validateSelfUpdate
import cn.oryjk.changuan.infrastructure.SharedPreferencesEnvironmentStore
import cn.oryjk.changuan.infrastructure.SharedPreferencesSessionStore
import cn.oryjk.changuan.infrastructure.update.ApkDownloader
import cn.oryjk.changuan.infrastructure.update.AppUpdateChecker
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateCheckerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `checker falls back from failed primary endpoint to LAN fallback endpoint`() = runTest {
        val primary = MockWebServer()
        val fallback = MockWebServer()
        primary.start()
        fallback.start()
        try {
            primary.enqueue(MockResponse().setResponseCode(502))
            fallback.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "versionName": "1.2.0",
                          "versionCode": "99",
                          "publishedAt": "2026-11-16 18:00:00 +0800",
                          "gitCommit": "abc1234",
                          "releaseNotes": ["新增双通道校验"],
                          "releaseFile": "releases/changuan-v1.2.0.apk",
                          "sha256": "${"a".repeat(64)}",
                          "fileSizeBytes": 1024
                        }
                        """.trimIndent(),
                    ),
            )

            val checker = AppUpdateChecker(
                metadataUrl = primary.url("/chanlun-android/metadata.json").toString(),
                downloadPageUrl = primary.url("/chanlun-android/").toString(),
                currentVersionCode = 3,
                fallbackMetadataUrl = fallback.url("/chanlun-android/metadata.json").toString(),
                fallbackDownloadPageUrl = fallback.url("/chanlun-android/").toString(),
            )
            val info = checker.checkLatest()
            assertNotNull(info)
            assertEquals(99, info?.versionCode)
            assertEquals("1.2.0", info?.versionName)
            assertTrue(info?.apkUrl.orEmpty().contains("/chanlun-android/releases/changuan-v1.2.0.apk"))
        } finally {
            primary.shutdown()
            fallback.shutdown()
        }
    }

    @Test
    fun `release history preserves apkAvailable and sorts by descending versionCode`() {
        val raw = """
            [
              {
                "versionName": "1.0.0",
                "versionCode": "1",
                "publishedAt": "2026-11-01 12:00:00",
                "releaseNotes": ["首个版本"],
                "apkAvailable": false,
                "releaseFile": null
              },
              {
                "versionName": "1.1.0",
                "versionCode": "3",
                "publishedAt": "2026-11-16 12:00:00",
                "releaseNotes": ["视觉重构", "飞书邮箱登录"],
                "apkAvailable": true,
                "releaseFile": "releases/v1.1.0.apk"
              }
            ]
        """.trimIndent()
        val parsed = AppUpdateChecker.parseReleaseHistoryStrict(raw)
        assertEquals(2, parsed.size)
        assertEquals(3, parsed[0].versionCode)
        assertTrue(parsed[0].apkAvailable)
        assertEquals(1, parsed[1].versionCode)
        assertTrue(!parsed[1].apkAvailable)
    }

    @Test
    fun `apk downloader falls back to secondary url and enforces sha256`() = runTest {
        val primary = MockWebServer()
        val fallback = MockWebServer()
        primary.start()
        fallback.start()
        try {
            val payload = "FAKE_APK_BINARY_PAYLOAD".toByteArray()
            val expectedSha = MessageDigest.getInstance("SHA-256")
                .digest(payload)
                .joinToString("") { "%02x".format(it) }

            primary.enqueue(MockResponse().setResponseCode(503))
            fallback.enqueue(MockResponse().setResponseCode(200).setBody(String(payload)))

            val downloader = ApkDownloader()
            val downloaded = downloader.download(
                info = AppUpdateInfo(
                    versionName = "1.2.0",
                    versionCode = 10,
                    publishedAt = null,
                    gitCommit = null,
                    releaseNotes = emptyList(),
                    downloadPageUrl = fallback.url("/chanlun-android/").toString(),
                    apkUrl = primary.url("/chanlun-android/latest.apk").toString(),
                    fallbackApkUrl = fallback.url("/chanlun-android/latest.apk").toString(),
                    sha256 = expectedSha,
                    fileSizeBytes = payload.size.toLong(),
                ),
                dir = tempFolder.newFolder("apk-ok"),
                onProgress = { _, _ -> },
            )
            assertTrue(downloaded.isFile)
            assertEquals(payload.size.toLong(), downloaded.length())

            // Wrong SHA-256 must fail cleanly
            fallback.enqueue(MockResponse().setResponseCode(200).setBody(String(payload)))
            var failedMessage: String? = null
            try {
                downloader.download(
                    info = AppUpdateInfo(
                        versionName = "1.2.0",
                        versionCode = 11,
                        publishedAt = null,
                        gitCommit = null,
                        releaseNotes = emptyList(),
                        downloadPageUrl = fallback.url("/chanlun-android/").toString(),
                        apkUrl = fallback.url("/chanlun-android/latest.apk").toString(),
                        sha256 = "f".repeat(64),
                        fileSizeBytes = payload.size.toLong(),
                    ),
                    dir = tempFolder.newFolder("apk-bad"),
                    onProgress = { _, _ -> },
                )
            } catch (err: IOException) {
                failedMessage = err.message
            }
            assertTrue(failedMessage?.contains("SHA-256") == true)
        } finally {
            primary.shutdown()
            fallback.shutdown()
        }
    }

    @Test
    fun `validateSelfUpdate rejects mismatched package downgrade or signer`() {
        val current = InstalledApkIdentity(
            packageName = "cn.oryjk.changuan.debug",
            versionCode = 3L,
            signers = setOf("SIG_A"),
        )
        validateSelfUpdate(
            current = current,
            apk = ApkUpdateIdentity("cn.oryjk.changuan.debug", 4L, setOf("SIG_A"), 1024L),
            expectedVersion = 4,
            expectedSize = 1024L,
        )

        val pkgErr = runCatching {
            validateSelfUpdate(
                current = current,
                apk = ApkUpdateIdentity("com.other.app", 4L, setOf("SIG_A"), 1024L),
                expectedVersion = 4,
                expectedSize = 1024L,
            )
        }.exceptionOrNull()
        assertNotNull(pkgErr)

        val signErr = runCatching {
            validateSelfUpdate(
                current = current,
                apk = ApkUpdateIdentity("cn.oryjk.changuan.debug", 4L, setOf("SIG_B"), 1024L),
                expectedVersion = 4,
                expectedSize = 1024L,
            )
        }.exceptionOrNull()
        assertNotNull(signErr)
    }

    @Test
    fun `session tokens and environment preference persist separately per environment`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val envStore = SharedPreferencesEnvironmentStore(context)
        assertEquals(BackendEnvironment.DevelopmentProxy, envStore.environment.value)

        val proxySession = SharedPreferencesSessionStore(
            context,
            SharedPreferencesSessionStore.prefsNameFor(BackendEnvironment.DevelopmentProxy),
        )
        val lanSession = SharedPreferencesSessionStore(
            context,
            SharedPreferencesSessionStore.prefsNameFor(BackendEnvironment.Development),
        )
        proxySession.clear()
        lanSession.clear()

        proxySession.save("token-proxy-1")
        assertEquals("token-proxy-1", proxySession.token())
        assertNull(lanSession.token())

        lanSession.save("token-lan-2")
        assertEquals("token-proxy-1", proxySession.token())
        assertEquals("token-lan-2", lanSession.token())

        envStore.select(BackendEnvironment.Production)
        assertEquals(BackendEnvironment.Production, envStore.environment.value)
        val reloaded = SharedPreferencesEnvironmentStore(context)
        assertEquals(BackendEnvironment.Production, reloaded.environment.value)
    }
}
