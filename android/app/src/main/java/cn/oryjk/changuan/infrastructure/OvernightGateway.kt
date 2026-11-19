package cn.oryjk.changuan.infrastructure

import cn.oryjk.changuan.application.OvernightReader
import cn.oryjk.changuan.domain.OvernightCandidate
import cn.oryjk.changuan.domain.OvernightCandidateStatus
import cn.oryjk.changuan.domain.OvernightPhase
import cn.oryjk.changuan.domain.OvernightReminder
import cn.oryjk.changuan.domain.OvernightSnapshot
import cn.oryjk.changuan.domain.OvernightStatus
import cn.oryjk.changuan.domain.Stock
import com.google.gson.JsonObject

class OvernightGateway(private val api: ApiClient) : OvernightReader {
    override suspend fun snapshot(): OvernightSnapshot =
        api.send(path = "api/methods/overnight", mapper = ::parseSnapshot)

    private fun parseSnapshot(root: JsonObject): OvernightSnapshot =
        OvernightSnapshot(
            tradeDate = root.optString("trade_date"),
            phase = root.reqEnum("phase"),
            observedAt = root.optInstant("observed_at"),
            historySessions = root.reqInt("history_sessions"),
            universeCount = root.reqInt("universe_count"),
            freshCount = root.reqInt("fresh_count"),
            matchedCount = root.reqInt("matched_count"),
            minuteCheckedCount = root.reqInt("minute_checked_count"),
            elapsedMillis = root.optInt("elapsed_ms"),
            maxQuoteAgeSeconds = root.optDouble("max_quote_age_seconds"),
            status = root.reqEnum<OvernightStatus>("status"),
            candidates = root.optArray("candidates").map(::parseCandidate),
            reminders = root.optArray("reminders").map(::parseReminder),
            remindersUntil = root.optInstant("reminders_until"),
        )

    private fun parseCandidate(item: JsonObject): OvernightCandidate =
        OvernightCandidate(
            stock = parseStock(item),
            price = item.reqDouble("price"),
            changePercent = item.reqDouble("change_percent"),
            volumeRatio = item.reqDouble("volume_ratio"),
            turnoverRate = item.reqDouble("turnover_rate"),
            totalMarketCap = item.reqDouble("total_market_cap"),
            limitUpDate = item.reqString("limit_up_date"),
            asOf = item.reqInstant("as_of"),
            averagePrice = item.optDouble("average_price"),
            benchmarkChangePercent = item.optDouble("benchmark_change_percent"),
            pullbackAt = item.optInstant("pullback_at"),
            status = item.reqEnum<OvernightCandidateStatus>("status"),
            reason = item.optString("reason"),
            minuteAt = item.optInstant("minute_at"),
            minuteChangePercent = item.optDouble("minute_change_percent"),
        )

    private fun parseReminder(item: JsonObject): OvernightReminder =
        OvernightReminder(
            stock = parseStock(item),
            signalDate = item.reqString("signal_date"),
            signalAt = item.reqInstant("signal_at"),
            exceptionObserved = item.optBoolean("exception_observed"),
            exceptionAsOf = item.optInstant("exception_as_of"),
            exceptionValidUntil = item.optInstant("exception_valid_until"),
        )

    private fun parseStock(item: JsonObject): Stock =
        Stock(
            secid = item.reqString("secid"),
            symbol = item.reqString("symbol"),
            name = item.reqString("name"),
            market = "A股",
        )

    private inline fun <reified T : Enum<T>> JsonObject.reqEnum(key: String): T {
        val wireValue = reqString(key)
        return enumValues<T>().firstOrNull { it.name.equals(wireValue, ignoreCase = true) }
            ?: throw ApiError.InvalidData()
    }
}
