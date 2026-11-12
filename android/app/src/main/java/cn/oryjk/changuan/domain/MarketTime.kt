package cn.oryjk.changuan.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

object MarketTime {
    val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val hongKong: ZoneId = ZoneId.of("Asia/Hong_Kong")
    private val newYork: ZoneId = ZoneId.of("America/New_York")

    data class Session(
        val zone: ZoneId,
        val windows: List<Pair<Int, Int>>,
        val label: String,
    ) {
        val duration: Double
            get() = windows.sumOf { it.second - it.first }.toDouble()
    }

    fun session(stock: Stock? = null): Session {
        val market = stock?.market?.uppercase(Locale.ROOT).orEmpty()
        if (market.contains("美") || market.contains("US")) {
            return Session(zone = newYork, windows = listOf(570 to 960), label = "纽约时间")
        }
        if (market.contains("港") || market.contains("HK")) {
            return Session(zone = hongKong, windows = listOf(570 to 720, 780 to 960), label = "香港时间")
        }
        return Session(zone = shanghai, windows = listOf(570 to 690, 780 to 900), label = "北京时间")
    }

    fun zone(currency: String?): ZoneId = when (currency) {
        "USD" -> newYork
        "HKD" -> hongKong
        else -> shanghai
    }

    fun formatDate(
        instant: Instant,
        pattern: String = "yyyy-MM-dd",
        zone: ZoneId = shanghai,
    ): String {
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.SIMPLIFIED_CHINESE)
        return instant.atZone(zone).format(formatter)
    }

    fun isOpen(instant: Instant = Instant.now(), stock: Stock? = null): Boolean {
        val session = session(stock)
        val zoned = instant.atZone(session.zone)
        val day = zoned.dayOfWeek
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false
        }
        val minute = zoned.hour * 60 + zoned.minute
        return session.windows.any { (start, end) -> minute in start until end }
    }

    fun tradingMinute(instant: Instant, stock: Stock? = null): Double {
        val session = session(stock)
        val zoned = instant.atZone(session.zone)
        val minute = (zoned.hour * 60 + zoned.minute).toDouble() + zoned.second / 60.0
        var elapsed = 0.0
        for ((start, end) in session.windows) {
            if (minute < end.toDouble()) {
                return elapsed + max(0.0, minute - start.toDouble())
            }
            elapsed += (end - start).toDouble()
        }
        return elapsed
    }

    fun clock(minute: Int): String =
        String.format(Locale.US, "%02d:%02d", minute / 60, minute % 60)

    fun axisLabels(stock: Stock?): List<Pair<String, Double>> {
        val session = session(stock)
        val first = session.windows.firstOrNull() ?: return emptyList()
        val last = session.windows.lastOrNull() ?: return emptyList()
        val labels = mutableListOf<Pair<String, Double>>()
        labels += clock(first.first) to 0.0
        if (session.windows.size > 1) {
            val second = session.windows[1]
            labels += "${clock(first.second)} / ${clock(second.first)}" to
                ((first.second - first.first).toDouble() / session.duration)
        }
        labels += clock(last.second) to 1.0
        return labels
    }

    fun parseInstant(raw: String?): Instant? {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        return runCatching { Instant.parse(cleaned) }
            .recoverCatching { OffsetDateTime.parse(cleaned).toInstant() }
            .recoverCatching {
                LocalDateTime.parse(cleaned.replace(' ', 'T'))
                    .atZone(shanghai)
                    .toInstant()
            }
            .recoverCatching {
                LocalDate.parse(cleaned)
                    .atStartOfDay(shanghai)
                    .toInstant()
            }
            .getOrNull()
    }
}
