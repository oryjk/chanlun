package cn.oryjk.changuan.domain

import java.time.Instant

enum class OvernightPhase {
    WAITING,
    PREPARING,
    SCANNING,
    CLOSED,
    EXIT,
}

enum class OvernightStatus {
    PENDING,
    READY,
    PARTIAL,
    UNAVAILABLE,
}

enum class OvernightCandidateStatus {
    WATCHING,
    PULLBACK,
    FILTERED,
    UNAVAILABLE,
}

data class OvernightSnapshot(
    val tradeDate: String?,
    val phase: OvernightPhase,
    val observedAt: Instant?,
    val historySessions: Int,
    val universeCount: Int,
    val freshCount: Int,
    val matchedCount: Int,
    val minuteCheckedCount: Int,
    val elapsedMillis: Int?,
    val maxQuoteAgeSeconds: Double?,
    val status: OvernightStatus,
    val candidates: List<OvernightCandidate>,
    val reminders: List<OvernightReminder>,
    val remindersUntil: Instant? = null,
)

data class OvernightCandidate(
    val stock: Stock,
    val price: Double,
    val changePercent: Double,
    val volumeRatio: Double,
    val turnoverRate: Double,
    val totalMarketCap: Double,
    val limitUpDate: String,
    val asOf: Instant,
    val averagePrice: Double?,
    val benchmarkChangePercent: Double?,
    val pullbackAt: Instant?,
    val status: OvernightCandidateStatus,
    val reason: String?,
    val minuteAt: Instant? = null,
    val minuteChangePercent: Double? = null,
)

data class OvernightReminder(
    val stock: Stock,
    val signalDate: String,
    val signalAt: Instant,
    val exceptionObserved: Boolean?,
    val exceptionAsOf: Instant?,
    val exceptionValidUntil: Instant? = null,
)
