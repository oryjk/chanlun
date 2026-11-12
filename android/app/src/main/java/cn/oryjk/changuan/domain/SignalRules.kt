package cn.oryjk.changuan.domain

import java.util.Locale

object MarketFormat {
    fun price(value: Double?, decimals: Int): String {
        if (value == null || !value.isFinite()) return "—"
        return String.format(Locale.US, "%.*f".replace("*", decimals.toString()), value)
    }

    fun signedPrice(value: Double?, decimals: Int): String {
        if (value == null || !value.isFinite()) return "—"
        return String.format(Locale.US, "%+.*f".replace("*", decimals.toString()), value)
    }

    fun percent(value: Double?): String {
        if (value == null || !value.isFinite()) return "—"
        return String.format(Locale.US, "%+.2f%%", value)
    }

    fun volume(value: Double?): String {
        if (value == null || !value.isFinite()) return "—"
        return when {
            value >= 100_000_000 -> String.format(Locale.US, "%.2f 亿", value / 100_000_000)
            value >= 10_000 -> String.format(Locale.US, "%.1f 万", value / 10_000)
            else -> String.format(Locale.US, "%.0f", value)
        }
    }
}

data class SignalConclusion(
    val title: String,
    val detail: String,
)

data class CandleChange(
    val delta: Double,
    val percent: Double,
)

object SignalRules {
    fun trend(value: String?): String = when (value) {
        "up_trend" -> "上涨趋势"
        "down_trend" -> "下跌趋势"
        "consolidation" -> "盘整"
        "none", null, "" -> "结构不足"
        else -> value
    }

    fun biasLabel(bias: String?): String = when (bias) {
        "bullish" -> "偏多"
        "bearish" -> "偏空"
        else -> "观察"
    }

    fun conclusion(
        bias: String,
        latest: Double,
        point: SignalPoint?,
        quality: SignalQuality?,
    ): SignalConclusion {
        if (point != null && (
                quality?.state == "invalidated" ||
                    quality?.historyStatus == "invalidated" ||
                    quality?.firstReferenceCrossedAt != null
                )
        ) {
            val sideText = if (point.side == "buy") "买" else "卖"
            return SignalConclusion(
                title = "${sideText}点结构已失效",
                detail = "已有收盘价越过该信号对应的失效参考，即使价格恢复也不继续沿用原信号",
            )
        }
        if (point != null && !point.confirmed) {
            return SignalConclusion(
                title = if (point.side == "buy") "候选买点，等待确认" else "候选卖点，注意风险",
                detail = "结构端点仍可能修正，候选状态不能作为已确认的买卖依据",
            )
        }
        val expectedSide = when (bias) {
            "bullish" -> "buy"
            "bearish" -> "sell"
            else -> ""
        }
        val aligned = point != null && point.side == expectedSide
        if (aligned && point != null) {
            if (quality?.state != "confirmed") {
                return SignalConclusion(
                    title = "结构已确认，证据待补齐",
                    detail = "尚不能核对该信号的有效性或当前 K 线未收盘，等待完整评价",
                )
            }
            if (quality.historyStatus != "checked_from_pivot") {
                return SignalConclusion(
                    title = "结构已确认，历史待核对",
                    detail = "尚未核对端点起的已收盘 K 线是否曾越界，暂列观察",
                )
            }
            return SignalConclusion(
                title = if (point.side == "buy") "买点结构已确认" else "卖点结构已确认",
                detail = "仅代表结构确认，量价辅助条件尚未验证；需另行制定入场、退出与仓位规则",
            )
        }
        if (bias == "bullish") {
            if (point != null && point.side == "sell") {
                return if (latest > point.price) {
                    SignalConclusion(
                        title = "刚突破中枢，等回踩买点",
                        detail = "现价已站上中枢上沿并突破前期卖点，等待回踩不破确认买点",
                    )
                } else {
                    SignalConclusion(
                        title = "出现卖点，暂不买入",
                        detail = "价格虽在中枢上方，但最近信号为卖点，注意冲高回落风险",
                    )
                }
            }
            return SignalConclusion(
                title = "偏多结构，等待买点",
                detail = "价格位于中枢上方，等待回抽出现买点信号",
            )
        }
        if (bias == "bearish") {
            if (point != null && point.side == "buy") {
                return if (latest < point.price) {
                    SignalConclusion(
                        title = "刚跌破中枢，等反抽确认",
                        detail = "现价已跌破中枢下沿及前期买点，结构转弱，暂宜规避",
                    )
                } else {
                    SignalConclusion(
                        title = "低位买点，观察筑底",
                        detail = "中枢下方出现买点信号，需收复中枢下沿确认转强",
                    )
                }
            }
            return SignalConclusion(
                title = "偏空结构，暂不买入",
                detail = "价格位于中枢下方，尚未出现明确买点信号",
            )
        }
        return SignalConclusion(
            title = "中枢震荡，暂不操作",
            detail = "价格尚未离开笔中枢核心区间或方向未达成一致，等待突破信号",
        )
    }

    fun invalidationReferencePrice(point: SignalPoint?, quality: SignalQuality?): Double? =
        quality?.invalidationPrice ?: point?.invalidationPrice

    fun historySummary(quality: SignalQuality?, decimals: Int): String {
        if (quality == null) return "未核对端点起历史越界情况"
        val crossedAt = quality.firstReferenceCrossedAt
        if (crossedAt != null) {
            val dateText = MarketTime.formatDate(crossedAt, "yyyy-MM-dd")
            val closeText = quality.firstReferenceCrossedClose?.let {
                "（收盘 ${MarketFormat.price(it, decimals)}）"
            }.orEmpty()
            return "首次越界：$dateText$closeText，信号已失效"
        }
        return when (quality.historyStatus) {
            "checked_from_pivot" -> {
                val touched = if (quality.referenceTouched == true) "（盘中高低价曾触及边界）" else ""
                "已核对端点起完成 K 线，未发生收盘越界$touched"
            }
            "invalidated" -> "已有收盘越过固定参考，信号已失效"
            else -> "端点起越界区间尚未完整核对，暂列观察"
        }
    }

    fun recognitionTimeSummary(quality: SignalQuality?): String {
        val detected = quality?.firstDetectedAt?.let { MarketTime.formatDate(it, "yyyy-MM-dd") }
        val confirmed = quality?.confirmedAt?.let { MarketTime.formatDate(it, "yyyy-MM-dd") }
        return if (detected == null && confirmed == null) {
            "首次识别与确认时间：未知（图上与列表日期为结构端点日期）"
        } else {
            "首次识别：${detected ?: "未知"}，确认时间：${confirmed ?: "未知"}"
        }
    }

    fun candleChange(candles: List<Candle>, index: Int): CandleChange? {
        if (index !in candles.indices) return null
        val current = candles[index]
        val base = if (index > 0) candles[index - 1].close else current.open
        if (base <= 0.0 || !base.isFinite()) return null
        val delta = current.close - base
        val pct = (delta / base) * 100.0
        return CandleChange(delta = delta, percent = pct)
    }
}
