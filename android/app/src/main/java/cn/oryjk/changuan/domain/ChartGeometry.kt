package cn.oryjk.changuan.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ChartViewport(
    val lower: Double,
    val upper: Double,
) {
    val span: Double
        get() = max(1.0, upper - lower)

    fun panned(bars: Double, count: Int): ChartViewport {
        val total = max(1, count).toDouble()
        val width = min(span, total)
        val nextLower = min(max(0.0, lower + bars), max(0.0, total - width))
        return ChartViewport(lower = nextLower, upper = nextLower + width)
    }

    fun zoomed(scale: Double, anchor: Double, count: Int): ChartViewport {
        if (!scale.isFinite() || scale <= 0.0) return this
        val total = max(1, count).toDouble()
        val minBars = min(12, max(1, count)).toDouble()
        val fraction = min(1.0, max(0.0, anchor))
        val center = lower + span * fraction
        val width = min(total, max(minBars, span / scale))
        val nextLower = min(max(0.0, center - width * fraction), max(0.0, total - width))
        return ChartViewport(lower = nextLower, upper = nextLower + width)
    }

    fun indexAtFraction(fraction: Double, count: Int): Int {
        if (count <= 0) return 0
        val clamped = min(1.0, max(0.0, fraction))
        val raw = (lower + span * clamped).toInt()
        return raw.coerceIn(0, count - 1)
    }

    companion object {
        fun initial(count: Int, defaultBars: Int = 90): ChartViewport {
            val total = max(1, count)
            val lower = max(0, total - defaultBars).toDouble()
            return ChartViewport(lower = lower, upper = total.toDouble())
        }
    }
}

class PriceProjection(
    minimum: Double,
    maximum: Double,
    val top: Double,
    height: Double,
) {
    val minimum: Double
    val maximum: Double
    val height: Double = max(1.0, height)

    init {
        val padding = max(abs(maximum - minimum) * 0.06, max(abs(minimum) * 0.001, 0.001))
        this.minimum = minimum - padding
        this.maximum = maximum + padding
    }

    fun y(price: Double): Double =
        top + (maximum - price) / (maximum - minimum) * height

    fun priceAt(y: Double): Double =
        maximum - (y - top) / height * (maximum - minimum)
}

data class ChartLayers(
    val fractals: Boolean = true,
    val pens: Boolean = true,
    val segments: Boolean = true,
    val zhongshu: Boolean = true,
    val points: Boolean = true,
)
