package cn.oryjk.changuan.infrastructure

import cn.oryjk.changuan.application.MarketReader
import cn.oryjk.changuan.application.QuoteWriter
import cn.oryjk.changuan.domain.Candle
import cn.oryjk.changuan.domain.ChanAnalysis
import cn.oryjk.changuan.domain.ChanSignals
import cn.oryjk.changuan.domain.Divergence
import cn.oryjk.changuan.domain.Fractal
import cn.oryjk.changuan.domain.Instrument
import cn.oryjk.changuan.domain.LevelSignal
import cn.oryjk.changuan.domain.MacdPoint
import cn.oryjk.changuan.domain.MarketGuidance
import cn.oryjk.changuan.domain.MarketQuote
import cn.oryjk.changuan.domain.MarketSnapshot
import cn.oryjk.changuan.domain.MarketSummary
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.Pen
import cn.oryjk.changuan.domain.QuotePoint
import cn.oryjk.changuan.domain.QuoteSeries
import cn.oryjk.changuan.domain.RankingBoard
import cn.oryjk.changuan.domain.RankingEntry
import cn.oryjk.changuan.domain.Segment
import cn.oryjk.changuan.domain.SignalConfluence
import cn.oryjk.changuan.domain.SignalPoint
import cn.oryjk.changuan.domain.SignalQuality
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.domain.Timeframe
import cn.oryjk.changuan.domain.Zhongshu
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.time.Instant

class MarketGateway(
    private val api: ApiClient,
) : MarketReader, QuoteWriter {
    override suspend fun search(query: String): List<Stock> =
        api.send(
            path = "api/stocks/search",
            query = listOf("q" to query),
        ) { root ->
            root.optArray("items").map(::parseStock)
        }

    override suspend fun snapshot(
        secid: String,
        timeframe: Timeframe,
        refresh: Boolean,
    ): MarketSnapshot =
        api.send(
            path = "api/chan",
            query = listOf(
                "secid" to secid,
                "timeframe" to timeframe.rawValue,
                "refresh" to refresh.toString(),
            ),
            mapper = ::parseSnapshot,
        )

    override suspend fun quote(secid: String): MarketQuote =
        api.send(
            path = "api/quote",
            query = listOf("secid" to secid),
            mapper = ::parseQuote,
        )

    override suspend fun series(secid: String): QuoteSeries =
        api.send(
            path = "api/quote/series",
            query = listOf("secid" to secid),
            mapper = ::parseQuoteSeries,
        )

    override suspend fun rankings(): RankingBoard =
        api.send(
            path = "api/rankings",
            query = listOf("limit" to "500"),
            mapper = ::parseRankingBoard,
        )

    override suspend fun confluence(secid: String): SignalConfluence =
        api.send(
            path = "api/chan/confluence",
            query = listOf("secid" to secid),
            mapper = ::parseConfluence,
        )

    override suspend fun record(quote: MarketQuote): QuoteSeries {
        val body = JsonObject().apply {
            addProperty("secid", quote.secid)
            addProperty("time", quote.time.toString())
            addProperty("price", quote.latest)
        }
        return api.send(
            path = "api/quote/samples",
            body = body,
            mapper = ::parseQuoteSeries,
        )
    }

    companion object {
        internal fun parseStock(obj: JsonObject): Stock =
            Stock(
                secid = obj.reqString("secid"),
                symbol = obj.reqString("symbol"),
                name = obj.reqString("name"),
                market = obj.optString("market") ?: "沪深",
            )

        internal fun parseSnapshot(obj: JsonObject): MarketSnapshot {
            val instrumentObj = obj.reqObject("instrument")
            val summaryObj = obj.reqObject("summary")
            val analysisObj = obj.reqObject("analysis")
            val guidanceObj = obj.reqObject("guidance")
            val signalsObj = obj.reqObject("signals")
            return MarketSnapshot(
                instrument = Instrument(
                    secid = instrumentObj.reqString("secid"),
                    symbol = instrumentObj.reqString("symbol"),
                    name = instrumentObj.reqString("name"),
                    decimal = instrumentObj.optInt("decimal") ?: 2,
                    currency = instrumentObj.optString("currency"),
                    exchange = instrumentObj.optString("exchange"),
                ),
                timeframe = obj.reqString("timeframe"),
                timeframeLabel = obj.optString("timeframe_label") ?: obj.reqString("timeframe"),
                candles = obj.optArray("candles").map { c ->
                    Candle(
                        time = c.reqInstant("time"),
                        open = c.reqDouble("open"),
                        close = c.reqDouble("close"),
                        high = c.reqDouble("high"),
                        low = c.reqDouble("low"),
                        volume = c.optDouble("volume") ?: 0.0,
                    )
                },
                analysis = ChanAnalysis(
                    fractals = analysisObj.optArray("fractals").map { f ->
                        Fractal(
                            type = f.reqString("type"),
                            candleIndex = f.reqInt("candle_index"),
                            time = f.reqInstant("time"),
                            price = f.reqDouble("price"),
                        )
                    },
                    pens = analysisObj.optArray("pens").map { p ->
                        Pen(
                            id = p.reqInt("id"),
                            startIndex = p.reqInt("start_index"),
                            endIndex = p.reqInt("end_index"),
                            direction = p.reqString("direction"),
                            startTime = p.reqInstant("start_time"),
                            endTime = p.reqInstant("end_time"),
                            startPrice = p.reqDouble("start_price"),
                            endPrice = p.reqDouble("end_price"),
                            high = p.reqDouble("high"),
                            low = p.reqDouble("low"),
                            confirmed = p.optBoolean("confirmed") ?: true,
                        )
                    },
                    segments = analysisObj.optArray("segments").map { s ->
                        Segment(
                            id = s.reqInt("id"),
                            startIndex = s.reqInt("start_index"),
                            endIndex = s.reqInt("end_index"),
                            direction = s.reqString("direction"),
                            startTime = s.reqInstant("start_time"),
                            endTime = s.reqInstant("end_time"),
                            startPrice = s.reqDouble("start_price"),
                            endPrice = s.reqDouble("end_price"),
                            high = s.reqDouble("high"),
                            low = s.reqDouble("low"),
                            confirmed = s.optBoolean("confirmed") ?: true,
                        )
                    },
                    zhongshu = analysisObj.optArray("zhongshu").map { z ->
                        Zhongshu(
                            id = z.reqInt("id"),
                            startIndex = z.reqInt("start_index"),
                            endIndex = z.reqInt("end_index"),
                            startTime = z.reqInstant("start_time"),
                            endTime = z.reqInstant("end_time"),
                            low = z.reqDouble("low"),
                            high = z.reqDouble("high"),
                            gg = z.reqDouble("gg"),
                            dd = z.reqDouble("dd"),
                            confirmed = z.optBoolean("confirmed") ?: true,
                        )
                    },
                    mergedCount = analysisObj.optInt("merged_count") ?: 0,
                    trend = analysisObj.optString("trend") ?: "none",
                ),
                summary = MarketSummary(
                    latest = summaryObj.reqDouble("latest"),
                    change = summaryObj.optDouble("change") ?: 0.0,
                    changePercent = summaryObj.optDouble("change_percent") ?: 0.0,
                    high = summaryObj.reqDouble("high"),
                    low = summaryObj.reqDouble("low"),
                    volume = summaryObj.optDouble("volume") ?: 0.0,
                ),
                guidance = MarketGuidance(
                    bias = guidanceObj.optString("bias") ?: "wait",
                    reasons = guidanceObj.optStringList("reasons"),
                    invalidationPrice = guidanceObj.optDouble("invalidation_price"),
                    strength = guidanceObj.optInt("strength") ?: 0,
                ),
                macd = obj.optArray("macd").map { m ->
                    MacdPoint(
                        dif = m.optDouble("dif") ?: 0.0,
                        dea = m.optDouble("dea") ?: 0.0,
                        histogram = m.optDouble("histogram") ?: 0.0,
                    )
                },
                signals = ChanSignals(
                    trendType = signalsObj.optString("trend_type") ?: "none",
                    divergences = signalsObj.optArray("divergences").map { d ->
                        Divergence(
                            kind = d.reqString("kind"),
                            direction = d.reqString("direction"),
                            time = d.reqInstant("time"),
                            price = d.reqDouble("price"),
                            confirmed = d.optBoolean("confirmed") ?: true,
                        )
                    },
                    points = signalsObj.optArray("points").map(::parseSignalPoint),
                ),
                signalQuality = obj.optObject("signal_quality")?.let(::parseSignalQuality),
            )
        }

        internal fun parseSignalPoint(obj: JsonObject): SignalPoint =
            SignalPoint(
                kind = obj.reqInt("kind"),
                side = obj.reqString("side"),
                penId = obj.optInt("pen_id") ?: 0,
                candleIndex = obj.optInt("candle_index") ?: 0,
                time = obj.reqInstant("time"),
                price = obj.reqDouble("price"),
                confirmed = obj.optBoolean("confirmed") ?: false,
                reason = obj.optString("reason").orEmpty(),
                invalidationPrice = obj.optDouble("invalidation_price"),
                sourceZoneId = obj.optInt("source_zone_id"),
            )

        internal fun parseSignalQuality(obj: JsonObject): SignalQuality =
            SignalQuality(
                state = obj.optString("state"),
                invalidationPrice = obj.optDouble("invalidation_price"),
                momentumStatus = obj.optString("momentum_status"),
                volumeStatus = obj.optString("volume_status"),
                historyStatus = obj.optString("history_status"),
                macdHistogram = obj.optDouble("macd_histogram"),
                macdAreaRatio = obj.optDouble("macd_area_ratio"),
                pullbackMacdRatio = obj.optDouble("pullback_macd_ratio"),
                breakoutVolumeRatio = obj.optDouble("breakout_volume_ratio"),
                pullbackVolumeRatio = obj.optDouble("pullback_volume_ratio"),
                referenceTouched = obj.optBoolean("reference_touched"),
                barsSincePivot = obj.optInt("bars_since_pivot"),
                firstReferenceCrossedAt = obj.optInstant("first_reference_crossed_at"),
                firstReferenceCrossedClose = obj.optDouble("first_reference_crossed_close"),
                historyCheckedThrough = obj.optInstant("history_checked_through"),
                firstDetectedAt = obj.optInstant("first_detected_at"),
                confirmedAt = obj.optInstant("confirmed_at"),
                reasons = obj.optStringList("reasons"),
            )

        internal fun parseQuote(obj: JsonObject): MarketQuote =
            MarketQuote(
                secid = obj.reqString("secid"),
                symbol = obj.reqString("symbol"),
                name = obj.reqString("name"),
                time = obj.reqInstant("time"),
                open = obj.optDouble("open") ?: 0.0,
                high = obj.optDouble("high") ?: 0.0,
                low = obj.optDouble("low") ?: 0.0,
                previousClose = obj.optDouble("previous_close") ?: 0.0,
                latest = obj.reqDouble("latest"),
                change = obj.optDouble("change") ?: 0.0,
                changePercent = obj.optDouble("change_percent") ?: 0.0,
                volume = obj.optDouble("volume") ?: 0.0,
                amount = obj.optDouble("amount") ?: 0.0,
            )

        internal fun parseQuotePoint(obj: JsonObject): QuotePoint =
            QuotePoint(
                time = obj.reqInstant("time"),
                price = obj.reqDouble("price"),
            )

        internal fun parseQuoteSeries(obj: JsonObject): QuoteSeries =
            QuoteSeries(
                secid = obj.reqString("secid"),
                points = obj.optArray("points").map(::parseQuotePoint),
            )

        internal fun parseConfluence(obj: JsonObject): SignalConfluence =
            SignalConfluence(
                secid = obj.reqString("secid"),
                conclusion = obj.optString("conclusion").orEmpty(),
                levels = obj.optArray("levels").map { lvl ->
                    LevelSignal(
                        timeframe = lvl.reqString("timeframe"),
                        timeframeLabel = lvl.optString("timeframe_label") ?: lvl.reqString("timeframe"),
                        trendType = lvl.optString("trend_type") ?: "none",
                        latestPoint = lvl.optObject("latest_point")?.let(::parseSignalPoint),
                        lowerConfirmation = lvl.optObject("lower_confirmation")?.let(::parseSignalPoint),
                    )
                },
            )

        internal fun parseRankingEntry(obj: JsonObject): RankingEntry =
            RankingEntry(
                rank = obj.optInt("rank") ?: 0,
                stock = parseStock(obj.reqObject("stock")),
                bias = obj.optString("bias") ?: "wait",
                strength = obj.optInt("strength") ?: 0,
                latest = obj.reqDouble("latest"),
                changePercent = obj.optDouble("change_percent") ?: 0.0,
                invalidationPrice = obj.optDouble("invalidation_price"),
                latestPoint = obj.optObject("latest_point")?.let(::parseSignalPoint),
                buyPoint = obj.optObject("buy_point")?.let(::parseSignalPoint),
                sellPoint = obj.optObject("sell_point")?.let(::parseSignalPoint),
                trend = obj.optArray("trend").map(::parseQuotePoint),
                signalQuality = obj.optObject("signal_quality")?.let(::parseSignalQuality),
                signalGroup = obj.optString("signal_group"),
            )

        internal fun parseRankingBoard(obj: JsonObject): RankingBoard =
            RankingBoard(
                generatedAt = obj.optInstant("generated_at"),
                timeframe = obj.optString("timeframe") ?: "1d",
                bullish = obj.optArray("bullish").map(::parseRankingEntry),
                bearish = obj.optArray("bearish").map(::parseRankingEntry),
                tradeDate = obj.optString("trade_date"),
                totalCount = obj.optInt("total_count"),
                analyzedCount = obj.optInt("analyzed_count"),
                failedCount = obj.optInt("failed_count"),
                progressTotal = obj.optInt("progress_total"),
                progressProcessed = obj.optInt("progress_processed"),
                state = obj.optString("state"),
                updateError = obj.optString("update_error"),
                analysisVersion = obj.optInt("analysis_version"),
            )
    }
}

private fun JsonObject.reqObject(key: String): JsonObject =
    optObject(key) ?: throw ApiError.InvalidData()

private fun JsonObject.optObject(key: String): JsonObject? =
    optElement(key)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.optArray(key: String): List<JsonObject> {
    val arr: JsonArray = optElement(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
    return arr.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
}

private fun JsonObject.optStringList(key: String): List<String> {
    val arr: JsonArray = optElement(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
    return arr.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
}

private fun JsonObject.reqInstant(key: String): Instant =
    optInstant(key) ?: throw ApiError.InvalidData()

private fun JsonObject.optInstant(key: String): Instant? =
    optString(key)?.let(MarketTime::parseInstant)
