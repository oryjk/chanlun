package cn.oryjk.changuan.presentation.charts

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.MarketQuote
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.PriceProjection
import cn.oryjk.changuan.domain.QuotePoint
import cn.oryjk.changuan.domain.RankingEntry
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.presentation.LocalChanPalette
import kotlin.math.abs
import kotlin.math.max

@Composable
fun RealtimeChartCanvas(
    stock: Stock,
    quote: MarketQuote?,
    points: List<QuotePoint>,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChanPalette.current
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SANS_SERIF
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        val padH = 8.dp.toPx().toDouble()
        val top = 8.dp.toPx().toDouble()
        val footerH = 18.dp.toPx().toDouble()
        val plotH = max(24.dp.toPx().toDouble(), h - top - footerH)
        val plotW = max(1.0, w - padH * 2)
        val dashed = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f)
        val guideStroke = 0.85.dp.toPx()

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

        if (session.windows.size > 1) {
            val morningMinutes = (session.windows[0].second - session.windows[0].first).toDouble()
            val midX = (padH + (morningMinutes / duration) * plotW).toFloat()
            drawLine(
                color = palette.cardBorder,
                start = Offset(midX, top.toFloat()),
                end = Offset(midX, (top + plotH).toFloat()),
                strokeWidth = guideStroke,
                pathEffect = dashed,
            )
        }

        quote?.previousClose?.takeIf { it > 0 }?.let { prevClose ->
            val prevY = projection.y(prevClose).toFloat()
            drawLine(
                color = palette.inkMuted.copy(alpha = 0.65f),
                start = Offset(padH.toFloat(), prevY),
                end = Offset((w - padH).toFloat(), prevY),
                strokeWidth = guideStroke,
                pathEffect = dashed,
            )
        }

        if (points.isNotEmpty()) {
            val lineColor = palette.primaryBrand
            val path = Path()
            var firstX = padH.toFloat()
            var lastX = padH.toFloat()
            points.forEachIndexed { idx, pt ->
                val minute = MarketTime.tradingMinute(pt.time, stock).coerceIn(0.0, duration)
                val x = (padH + (minute / duration) * plotW).toFloat()
                val y = projection.y(pt.price).toFloat()
                if (idx == 0) {
                    firstX = x
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
                lastX = x
            }
            if (points.size >= 2) {
                val bottomY = (top + plotH).toFloat()
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(lastX, bottomY)
                    lineTo(firstX, bottomY)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.16f),
                            lineColor.copy(alpha = 0.0f),
                        ),
                        startY = top.toFloat(),
                        endY = bottomY,
                    ),
                )
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        textPaint.textSize = 10.dp.toPx()
        val fm = textPaint.fontMetrics
        textPaint.color = palette.inkMuted.toArgb()
        drawContext.canvas.nativeCanvas.drawText(
            MarketFormat.price(projection.maximum, stock.decimals),
            (padH + 3.dp.toPx()).toFloat(),
            top.toFloat() + 2.dp.toPx() - fm.ascent,
            textPaint,
        )
        drawContext.canvas.nativeCanvas.drawText(
            MarketFormat.price(projection.minimum, stock.decimals),
            (padH + 3.dp.toPx()).toFloat(),
            (top + plotH).toFloat() - 2.dp.toPx() - fm.descent,
            textPaint,
        )

        val axisLabels = MarketTime.axisLabels(stock)
        val axisBaseline = h.toFloat() - 2.dp.toPx() - fm.descent
        axisLabels.forEach { (label, ratio) ->
            val tw = textPaint.measureText(label)
            val x = ((padH + ratio * plotW) - tw * ratio).toFloat()
                .coerceIn(padH.toFloat(), max(padH.toFloat(), (w - padH - tw).toFloat()))
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                axisBaseline,
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
    val palette = LocalChanPalette.current
    val trend = entry.trend
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SANS_SERIF
        }
    }

    Canvas(modifier = modifier) {
        if (trend.size < 2) return@Canvas
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        val markers = listOfNotNull(entry.buyPoint, entry.sellPoint)
        val prices = trend.map { it.price } + markers.map { it.price }
        val hInset = 6.dp.toPx().toDouble()
        val vInset = 14.dp.toPx().toDouble()
        val projection = PriceProjection(
            minimum = prices.minOrNull() ?: 0.0,
            maximum = prices.maxOrNull() ?: 1.0,
            top = vInset,
            height = max(12.dp.toPx().toDouble(), h - vInset * 2.0),
        )
        fun xAt(index: Int): Float =
            (index.toDouble() / max(1, trend.size - 1).toDouble() * max(1.0, w - hInset * 2.0) + hInset).toFloat()

        val lineColor = palette.primaryBrand
        val path = Path()
        trend.forEachIndexed { idx, pt ->
            val px = xAt(idx)
            val py = projection.y(pt.price).toFloat()
            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        textPaint.textSize = 9.5.dp.toPx()
        val fm = textPaint.fontMetrics
        val markerRadius = 2.8.dp.toPx()
        val textEdgePad = 2.dp.toPx()
        val vPad = 1.dp.toPx()
        markers.forEach { marker ->
            val nearestIdx = trend.indices.minByOrNull { idx ->
                abs(trend[idx].time.epochSecond - marker.time.epochSecond)
            } ?: return@forEach
            val mx = xAt(nearestIdx)
            val my = projection.y(marker.price).toFloat()
            val color = if (marker.side == "buy") palette.positiveRed else palette.negativeGreen
            drawCircle(
                color = color,
                radius = markerRadius,
                center = Offset(mx, my),
            )
            val title = "${if (marker.side == "buy") "买" else "卖"} ${
                MarketFormat.price(marker.price, entry.stock.decimals)
            } ${MarketTime.formatDate(marker.time, "MM-dd")}"
            val tw = textPaint.measureText(title)
            val tx = (mx - tw / 2f).coerceIn(textEdgePad, max(textEdgePad, w.toFloat() - tw - textEdgePad))
            val ty = if (marker.side == "buy") h.toFloat() - vPad - fm.descent else vPad - fm.ascent
            textPaint.color = color.toArgb()
            drawContext.canvas.nativeCanvas.drawText(title, tx, ty, textPaint)
        }
    }
}
