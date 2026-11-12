package cn.oryjk.changuan.presentation.charts

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.MarketQuote
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.PriceProjection
import cn.oryjk.changuan.domain.QuotePoint
import cn.oryjk.changuan.domain.RankingEntry
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.presentation.ChanTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun RealtimeChartCanvas(
    stock: Stock,
    quote: MarketQuote?,
    points: List<QuotePoint>,
    modifier: Modifier = Modifier,
) {
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        val padH = 10.0
        val top = 16.0
        val plotH = max(24.0, h - 40.0)
        val plotW = max(1.0, w - padH * 2)
        val dashed = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)

        val session = MarketTime.session(stock)
        val duration = max(1.0, session.duration)

        val allPrices = buildList {
            points.forEach { add(it.price) }
            quote?.previousClose?.takeIf { it > 0 }?.let { add(it) }
            quote?.high?.takeIf { it > 0 }?.let { add(it) }
            quote?.low?.takeIf { it > 0 }?.let { add(it) }
            quote?.latest?.takeIf { it > 0 }?.let { add(it) }
        }
        if (allPrices.isEmpty()) return@Canvas

        val projection = PriceProjection(
            minimum = allPrices.minOrNull() ?: 0.0,
            maximum = allPrices.maxOrNull() ?: 1.0,
            top = top,
            height = plotH,
        )

        // Lunch break vertical separator (at 120 minutes out of 240 for A-shares)
        if (session.windows.size > 1) {
            val morningMinutes = (session.windows[0].second - session.windows[0].first).toDouble()
            val midX = (padH + (morningMinutes / duration) * plotW).toFloat()
            drawLine(
                color = ChanTheme.Line,
                start = Offset(midX, top.toFloat()),
                end = Offset(midX, (top + plotH).toFloat()),
                strokeWidth = 1f,
                pathEffect = dashed,
            )
        }

        // Previous close reference line
        quote?.previousClose?.takeIf { it > 0 }?.let { prevClose ->
            val prevY = projection.y(prevClose).toFloat()
            drawLine(
                color = ChanTheme.TextMuted.copy(alpha = 0.65f),
                start = Offset(padH.toFloat(), prevY),
                end = Offset((w - padH).toFloat(), prevY),
                strokeWidth = 1.2f,
                pathEffect = dashed,
            )
        }

        // Price path
        if (points.isNotEmpty()) {
            val path = Path()
            points.forEachIndexed { idx, pt ->
                val minute = MarketTime.tradingMinute(pt.time, stock).coerceIn(0.0, duration)
                val x = (padH + (minute / duration) * plotW).toFloat()
                val y = projection.y(pt.price).toFloat()
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = ChanTheme.Accent,
                style = Stroke(width = 2.0f),
            )
        }

        // Axis labels on top-most pass
        val density = drawContext.density.density
        textPaint.textSize = 10f * density
        textPaint.color = ChanTheme.TextSecondary.toArgb()
        drawContext.canvas.nativeCanvas.drawText(
            MarketFormat.price(projection.maximum, stock.decimals),
            (padH + 4).toFloat(),
            (top + 12).toFloat(),
            textPaint,
        )
        drawContext.canvas.nativeCanvas.drawText(
            MarketFormat.price(projection.minimum, stock.decimals),
            (padH + 4).toFloat(),
            (top + plotH - 4).toFloat(),
            textPaint,
        )

        val axisLabels = MarketTime.axisLabels(stock)
        axisLabels.forEach { (label, ratio) ->
            val tw = textPaint.measureText(label)
            val x = ((padH + ratio * plotW) - tw * ratio).toFloat()
                .coerceIn(padH.toFloat(), (w - padH - tw).toFloat())
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                (h - 4).toFloat(),
                textPaint,
            )
        }
    }
}

@Composable
fun RankingSparklineCanvas(
    entry: RankingEntry,
    modifier: Modifier = Modifier,
) {
    val trend = entry.trend
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
        }
    }

    Canvas(modifier = modifier) {
        if (trend.size < 2) return@Canvas
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        val markers = listOfNotNull(entry.buyPoint, entry.sellPoint)
        val prices = trend.map { it.price } + markers.map { it.price }
        val projection = PriceProjection(
            minimum = prices.minOrNull() ?: 0.0,
            maximum = prices.maxOrNull() ?: 1.0,
            top = 14.0,
            height = max(12.0, h - 28.0),
        )
        fun xAt(index: Int): Float =
            (index.toDouble() / max(1, trend.size - 1).toDouble() * max(1.0, w - 12.0) + 6.0).toFloat()

        val path = Path()
        trend.forEachIndexed { idx, pt ->
            val px = xAt(idx)
            val py = projection.y(pt.price).toFloat()
            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path = path, color = ChanTheme.Accent, style = Stroke(width = 1.6f))

        val density = drawContext.density.density
        textPaint.textSize = 9.5f * density
        markers.forEach { marker ->
            val nearestIdx = trend.indices.minByOrNull { idx ->
                abs(trend[idx].time.epochSecond - marker.time.epochSecond)
            } ?: return@forEach
            val mx = xAt(nearestIdx)
            val my = projection.y(marker.price).toFloat()
            val color = if (marker.side == "buy") ChanTheme.Up else ChanTheme.Down
            drawCircle(
                color = color,
                radius = 3.2f,
                center = Offset(mx, my),
            )
            val title = "${if (marker.side == "buy") "买" else "卖"} ${
                MarketFormat.price(marker.price, entry.stock.decimals)
            } ${MarketTime.formatDate(marker.time, "MM-dd")}"
            val tw = textPaint.measureText(title)
            val tx = (mx - tw / 2f).coerceIn(4f, max(4f, w.toFloat() - tw - 4f))
            val ty = if (marker.side == "buy") h.toFloat() - 3f else 11f
            textPaint.color = color.toArgb()
            drawContext.canvas.nativeCanvas.drawText(title, tx, ty, textPaint)
        }
    }
}
