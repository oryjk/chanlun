package cn.oryjk.changuan.domain

import java.time.Instant

data class Stock(
    val secid: String,
    val symbol: String,
    val name: String,
    val market: String,
) {
    val decimals: Int
        get() = if (market == "ETF") 3 else 2
}

enum class Timeframe(val rawValue: String, val label: String) {
    FIVE_MINUTES("5m", "5 分钟"),
    THIRTY_MINUTES("30m", "30 分钟"),
    ONE_HOUR("1h", "1 小时"),
    DAILY("1d", "日线"),
    REALTIME("realtime", "实时");

    companion object {
        fun fromRaw(raw: String?): Timeframe =
            entries.firstOrNull { it.rawValue == raw } ?: DAILY
    }
}

enum class RootTab(val rawValue: String, val label: String) {
    MARKET("market", "行情"),
    RANKING("ranking", "榜单"),
    ACCOUNT("account", "账号");

    companion object {
        fun fromRaw(raw: String?): RootTab =
            entries.firstOrNull { it.rawValue == raw } ?: MARKET
    }
}

data class Candle(
    val time: Instant,
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
)

data class Instrument(
    val secid: String,
    val symbol: String,
    val name: String,
    val decimal: Int,
    val currency: String?,
    val exchange: String?,
)

data class MarketSummary(
    val latest: Double,
    val change: Double,
    val changePercent: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
)

data class Fractal(
    val type: String,
    val candleIndex: Int,
    val time: Instant,
    val price: Double,
)

data class Pen(
    val id: Int,
    val startIndex: Int,
    val endIndex: Int,
    val direction: String,
    val startTime: Instant,
    val endTime: Instant,
    val startPrice: Double,
    val endPrice: Double,
    val high: Double,
    val low: Double,
    val confirmed: Boolean,
)

data class Segment(
    val id: Int,
    val startIndex: Int,
    val endIndex: Int,
    val direction: String,
    val startTime: Instant,
    val endTime: Instant,
    val startPrice: Double,
    val endPrice: Double,
    val high: Double,
    val low: Double,
    val confirmed: Boolean,
)

data class Zhongshu(
    val id: Int,
    val startIndex: Int,
    val endIndex: Int,
    val startTime: Instant,
    val endTime: Instant,
    val low: Double,
    val high: Double,
    val gg: Double,
    val dd: Double,
    val confirmed: Boolean,
)

data class ChanAnalysis(
    val fractals: List<Fractal>,
    val pens: List<Pen>,
    val segments: List<Segment>,
    val zhongshu: List<Zhongshu>,
    val mergedCount: Int,
    val trend: String,
)

data class MacdPoint(
    val dif: Double,
    val dea: Double,
    val histogram: Double,
)

data class SignalPoint(
    val kind: Int,
    val side: String,
    val penId: Int,
    val candleIndex: Int,
    val time: Instant,
    val price: Double,
    val confirmed: Boolean,
    val reason: String,
    val invalidationPrice: Double?,
    val sourceZoneId: Int?,
) {
    val label: String
        get() {
            val prefix = when (kind) {
                1 -> "一"
                2 -> "二"
                3 -> "三"
                else -> kind.toString()
            }
            val sideText = if (side == "buy") "买" else "卖"
            return "${prefix}类${sideText}点"
        }

    val shortTag: String
        get() = "${if (side == "buy") "B" else "S"}$kind"
}

data class Divergence(
    val kind: String,
    val direction: String,
    val time: Instant,
    val price: Double,
    val confirmed: Boolean,
)

data class ChanSignals(
    val trendType: String,
    val divergences: List<Divergence>,
    val points: List<SignalPoint>,
)

data class SignalQuality(
    val state: String?,
    val invalidationPrice: Double?,
    val momentumStatus: String?,
    val volumeStatus: String?,
    val historyStatus: String?,
    val macdHistogram: Double?,
    val macdAreaRatio: Double?,
    val pullbackMacdRatio: Double?,
    val breakoutVolumeRatio: Double?,
    val pullbackVolumeRatio: Double?,
    val referenceTouched: Boolean?,
    val barsSincePivot: Int?,
    val firstReferenceCrossedAt: Instant?,
    val firstReferenceCrossedClose: Double?,
    val historyCheckedThrough: Instant?,
    val firstDetectedAt: Instant?,
    val confirmedAt: Instant?,
    val reasons: List<String>,
)

data class MarketGuidance(
    val bias: String,
    val reasons: List<String>,
    val invalidationPrice: Double?,
    val strength: Int,
)

data class MarketSnapshot(
    val instrument: Instrument,
    val timeframe: String,
    val timeframeLabel: String,
    val candles: List<Candle>,
    val analysis: ChanAnalysis,
    val summary: MarketSummary,
    val guidance: MarketGuidance,
    val macd: List<MacdPoint>,
    val signals: ChanSignals,
    val signalQuality: SignalQuality?,
)

data class QuotePoint(
    val time: Instant,
    val price: Double,
)

data class QuoteSeries(
    val secid: String,
    val points: List<QuotePoint>,
)

data class MarketQuote(
    val secid: String,
    val symbol: String,
    val name: String,
    val time: Instant,
    val open: Double,
    val high: Double,
    val low: Double,
    val previousClose: Double,
    val latest: Double,
    val change: Double,
    val changePercent: Double,
    val volume: Double,
    val amount: Double,
)

data class LevelSignal(
    val timeframe: String,
    val timeframeLabel: String,
    val trendType: String,
    val latestPoint: SignalPoint?,
    val lowerConfirmation: SignalPoint?,
)

data class SignalConfluence(
    val secid: String,
    val conclusion: String,
    val levels: List<LevelSignal>,
)

data class RankingEntry(
    val rank: Int,
    val stock: Stock,
    val bias: String,
    val strength: Int,
    val latest: Double,
    val changePercent: Double,
    val invalidationPrice: Double?,
    val latestPoint: SignalPoint?,
    val buyPoint: SignalPoint?,
    val sellPoint: SignalPoint?,
    val trend: List<QuotePoint>,
    val signalQuality: SignalQuality?,
    val signalGroup: String?,
) {
    val isConfirmedGroup: Boolean
        get() = signalGroup == "confirmed"
}

data class RankingBoard(
    val generatedAt: Instant?,
    val timeframe: String,
    val bullish: List<RankingEntry>,
    val bearish: List<RankingEntry>,
    val tradeDate: String?,
    val totalCount: Int?,
    val analyzedCount: Int?,
    val failedCount: Int?,
    val progressTotal: Int?,
    val progressProcessed: Int?,
    val state: String?,
    val updateError: String?,
    val analysisVersion: Int?,
)

data class AppUser(
    val userId: String,
    val nickname: String,
)

data class AppSession(
    val authenticated: Boolean,
    val user: AppUser?,
)

data class AppLoginResult(
    val user: AppUser,
    val token: String,
    val expiresAt: Instant,
)

data class EmailCodeSent(
    val expiresInSeconds: Int,
    val cooldownSeconds: Int,
)

data class TermDefinition(
    val key: String,
    val term: String,
    val meaning: String,
    val usage: String,
)

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
)

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val downloaded: Long, val total: Long?) : UpdateDownloadState
    data class Ready(val apkPath: String) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

object DefaultStocks {
    val KWEICHOW_MOUTAI = Stock(
        secid = "600519.SH",
        symbol = "600519",
        name = "贵州茅台",
        market = "沪A",
    )
}
