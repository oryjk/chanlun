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

data class ChartBadgeBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val textX: Float,
    val textBaselineY: Float,
) {
    val right: Float
        get() = left + width

    val bottom: Float
        get() = top + height
}

private fun buildBadgeBox(
    rawLeft: Float,
    rawTop: Float,
    textWidth: Float,
    fontAscent: Float,
    fontDescent: Float,
    padX: Float,
    padY: Float,
    minX: Float,
    maxX: Float,
    minY: Float,
    maxY: Float,
): ChartBadgeBox {
    val glyphHeight = fontDescent - fontAscent
    val width = textWidth + padX * 2f
    val height = glyphHeight + padY * 2f
    val left = rawLeft.coerceIn(minX, max(minX, maxX - width))
    val top = rawTop.coerceIn(minY, max(minY, maxY - height))
    val textX = left + padX
    val textBaselineY = top + height / 2f - (fontAscent + fontDescent) / 2f
    return ChartBadgeBox(
        left = left,
        top = top,
        width = width,
        height = height,
        textX = textX,
        textBaselineY = textBaselineY,
    )
}

fun computePointBadgeBox(
    anchorX: Float,
    anchorY: Float,
    isBelowAnchor: Boolean,
    textWidth: Float,
    fontAscent: Float,
    fontDescent: Float,
    padX: Float,
    padY: Float,
    anchorGap: Float,
    minX: Float,
    maxX: Float,
    minY: Float,
    maxY: Float,
): ChartBadgeBox {
    val height = (fontDescent - fontAscent) + padY * 2f
    val width = textWidth + padX * 2f
    val rawLeft = anchorX - width / 2f
    val rawTop = if (isBelowAnchor) anchorY + anchorGap else anchorY - anchorGap - height
    return buildBadgeBox(
        rawLeft = rawLeft,
        rawTop = rawTop,
        textWidth = textWidth,
        fontAscent = fontAscent,
        fontDescent = fontDescent,
        padX = padX,
        padY = padY,
        minX = minX,
        maxX = maxX,
        minY = minY,
        maxY = maxY,
    )
}

fun computeLabelPillBox(
    anchorLeft: Float,
    anchorTop: Float,
    textWidth: Float,
    fontAscent: Float,
    fontDescent: Float,
    padX: Float,
    padY: Float,
    minX: Float,
    maxX: Float,
    minY: Float,
    maxY: Float,
): ChartBadgeBox {
    val height = (fontDescent - fontAscent) + padY * 2f
    return buildBadgeBox(
        rawLeft = anchorLeft,
        rawTop = anchorTop - height,
        textWidth = textWidth,
        fontAscent = fontAscent,
        fontDescent = fontDescent,
        padX = padX,
        padY = padY,
        minX = minX,
        maxX = maxX,
        minY = minY,
        maxY = maxY,
    )
}
