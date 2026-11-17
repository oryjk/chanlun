package cn.oryjk.changuan

import cn.oryjk.changuan.domain.AssetCategory
import cn.oryjk.changuan.infrastructure.AuthGateway
import cn.oryjk.changuan.infrastructure.MarketGateway
import cn.oryjk.changuan.infrastructure.TrackingGateway
import cn.oryjk.changuan.infrastructure.update.AppUpdateChecker
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
            AppUpdateChecker.parseAndCompareMetadata(
                json = metadataJson,
                currentVersionCode = 2,
            ),
        )

        val update = AppUpdateChecker.parseAndCompareMetadata(
            json = metadataJson,
            currentVersionCode = 1,
        )
        assertEquals("1.0.1", update?.versionName)
        assertEquals(2, update?.versionCode)
        assertEquals(
            "https://ai.oryjk.cn:82/chanlun-android/releases/changuan-v1.0.1-20261112.apk",
            update?.apkUrl,
        )
        assertEquals("abcdef0123456789", update?.sha256)
    }

    @Test
    fun `market and tracking gateways decode asset list and tracking overview`() {
        val assetJson = """
            {
              "category": "etf",
              "as_of_date": "2026-11-16",
              "total": 938,
              "offset": 0,
              "has_more": true,
              "items": [
                {
                  "stock": {"secid": "510300.SH", "symbol": "510300", "name": "沪深300ETF", "market": "ETF"},
                  "latest": 4.182,
                  "change": 0.039,
                  "change_percent": 0.94,
                  "open": 4.150,
                  "high": 4.195,
                  "low": 4.132,
                  "previous_close": 4.143,
                  "volume": 1200000,
                  "amount": 5018400.0,
                  "sparkline": [4.09, 4.12, 4.182],
                  "signal_label": "二类买点"
                }
              ]
            }
        """.trimIndent()
        val parsedList = MarketGateway.parseAssetList(JsonParser.parseString(assetJson).asJsonObject)
        assertEquals(AssetCategory.ETF, parsedList.category)
        assertEquals("2026-11-16", parsedList.asOfDate)
        assertEquals(938, parsedList.total)
        assertEquals(0, parsedList.offset)
        assertEquals(true, parsedList.hasMore)
        assertEquals(1, parsedList.items.size)
        assertEquals("510300.SH", parsedList.items.first().stock.secid)
        assertEquals(3, parsedList.items.first().sparkline.size)
        assertEquals("二类买点", parsedList.items.first().signalLabel)

        val trackingJson = """
            {
              "as_of_date": "2026-11-16",
              "items": [
                {
                  "stock": {"secid": "600519.SH", "symbol": "600519", "name": "贵州茅台", "market": "沪A"},
                  "start_date": "2026-11-10",
                  "start_price": 1400.0,
                  "latest_price": 1453.9,
                  "day_return_pct": 1.30,
                  "day_profit": 133.20,
                  "cumulative_return_pct": 3.85,
                  "cumulative_profit": 385.0,
                  "current_value": 10385.0,
                  "sparkline": [1400.0, 1425.0, 1453.9]
                }
              ]
            }
        """.trimIndent()
        val overview = TrackingGateway.parseOverview(JsonParser.parseString(trackingJson).asJsonObject)
        assertEquals("2026-11-16", overview.asOfDate)
        assertEquals(1, overview.items.size)
        assertEquals(10385.0, overview.items.first().currentValue, 1e-6)
        assertEquals(385.0, overview.items.first().cumulativeProfit, 1e-6)
    }
}
