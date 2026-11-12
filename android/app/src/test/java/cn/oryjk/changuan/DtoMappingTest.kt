package cn.oryjk.changuan

import cn.oryjk.changuan.infrastructure.AppUpdateGateway
import cn.oryjk.changuan.infrastructure.AuthGateway
import cn.oryjk.changuan.infrastructure.MarketGateway
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtoMappingTest {
    @Test
    fun `market gateway decodes canonical secid ranking board and signal quality`() {
        val json = """
            {
              "generated_at": "2026-04-09T07:35:00+00:00",
              "timeframe": "1d",
              "trade_date": "2026-04-09",
              "total_count": 5200,
              "analyzed_count": 5198,
              "failed_count": 2,
              "analysis_version": 3,
              "bullish": [
                {
                  "rank": 1,
                  "stock": {
                    "secid": "600519.SH",
                    "symbol": "600519",
                    "name": "贵州茅台",
                    "market": "沪A"
                  },
                  "bias": "bullish",
                  "strength": 88,
                  "latest": 1520.5,
                  "change_percent": 1.68,
                  "invalidation_price": 1490.0,
                  "signal_group": "confirmed",
                  "signal_quality": {
                    "state": "confirmed",
                    "invalidation_price": 1485.0,
                    "history_status": "checked_from_pivot",
                    "bars_since_pivot": 3,
                    "reasons": ["结构已确认"]
                  }
                }
              ],
              "bearish": []
            }
        """.trimIndent()
        val board = MarketGateway.parseRankingBoard(JsonParser.parseString(json).asJsonObject)
        assertEquals("2026-04-09", board.tradeDate)
        assertEquals(1, board.bullish.size)
        val entry = board.bullish.first()
        assertEquals("600519.SH", entry.stock.secid)
        assertTrue(entry.isConfirmedGroup)
        assertEquals(1485.0, entry.signalQuality?.invalidationPrice ?: 0.0, 1e-6)
        assertEquals(3, entry.signalQuality?.barsSincePivot)
    }

    @Test
    fun `auth gateway decodes email code and login responses`() {
        val codeJson = """{"ok": true, "expires_in": 600, "cooldown_seconds": 60}"""
        val codeSent = AuthGateway.parseEmailCodeSent(JsonParser.parseString(codeJson).asJsonObject)
        assertEquals(600, codeSent.expiresInSeconds)
        assertEquals(60, codeSent.cooldownSeconds)

        val loginJson = """
            {
              "user": {"user_id": "u-123", "nickname": "量化交易员"},
              "token": "tok-abc",
              "expires_at": "2026-05-09T08:00:00+00:00"
            }
        """.trimIndent()
        val login = AuthGateway.parseLoginResult(JsonParser.parseString(loginJson).asJsonObject)
        assertEquals("u-123", login.user.userId)
        assertEquals("量化交易员", login.user.nickname)
        assertEquals("tok-abc", login.token)
    }

    @Test
    fun `update gateway compares version code and resolves release apk url`() {
        val metadataJson = """
            {
              "versionName": "1.0.1",
              "versionCode": "2",
              "publishedAt": "2026-11-12T16:00:00+08:00",
              "fileSizeBytes": 17825792,
              "sha256": "ABCDEF0123456789",
              "gitCommit": "245cf7c999",
              "releaseFile": "releases/changuan-v1.0.1-20261112.apk",
              "releaseNotes": ["新增 30 秒自动检测更新"]
            }
        """.trimIndent()

        assertNull(
            AppUpdateGateway.parseAndCompareMetadata(
                json = metadataJson,
                currentVersionCode = 2,
            ),
        )

        val update = AppUpdateGateway.parseAndCompareMetadata(
            json = metadataJson,
            currentVersionCode = 1,
        )
        assertEquals("1.0.1", update?.versionName)
        assertEquals(2, update?.versionCode)
        assertEquals(
            "http://172.16.60.233/chanlun-android/releases/changuan-v1.0.1-20261112.apk",
            update?.apkUrl,
        )
        assertEquals("abcdef0123456789", update?.sha256)
    }
}
