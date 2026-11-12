package cn.oryjk.changuan.presentation.charts

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import cn.oryjk.changuan.domain.ChartLayers
import cn.oryjk.changuan.domain.ChartViewport
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.MarketSnapshot
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.PriceProjection
import cn.oryjk.changuan.presentation.ChanTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun MarketChartCanvas(
    snapshot: MarketSnapshot,
    layers: ChartLayers,
    decimals: Int,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val candles = snapshot.candles
    var viewport by remember(snapshot.instrument.secid, snapshot.timeframe, candles.size) {
        mutableStateOf(ChartViewport.initial(candles.size))
    }

    LaunchedEffect(snapshot.instrument.secid, snapshot.timeframe) {
        viewport = ChartViewport.initial(candles.size)
    }

    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
        }
    }

    Canvas(
        modifier = modifier
            .pointerInput(snapshot.instrument.secid, snapshot.timeframe, candles.size) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (candles.isEmpty()) return@detectTransformGestures
                    val width = size.width.toDouble().coerceAtLeast(1.0)
                    var next = viewport
                    if (abs(zoom - 1f) > 0.01f) {
                        val anchor = (centroid.x.toDouble() / width).coerceIn(0.0, 1.0)
                        next = next.zoomed(zoom.toDouble(), anchor, candles.size)
                    }
                    if (abs(pan.x) > 0.5f) {
                        val barsDelta = -(pan.x.toDouble() / width) * next.span
                        next = next.panned(barsDelta, candles.size)
                    }
                    viewport = next
                }
            }
            .pointerInput(snapshot.instrument.secid, snapshot.timeframe, candles.size) {
                detectTapGestures(
                    onTap = { offset ->
                        if (candles.isEmpty()) return@detectTapGestures
                        val fraction = (offset.x.toDouble() / size.width.toDouble().coerceAtLeast(1.0))
                            .coerceIn(0.0, 1.0)
                        val idx = viewport.indexAtFraction(fraction, candles.size)
                        onSelectIndex(if (selectedIndex == idx) null else idx)
                    },
                    onLongPress = { offset ->
                        if (candles.isEmpty()) return@detectTapGestures
                        val fraction = (offset.x.toDouble() / size.width.toDouble().coerceAtLeast(1.0))
                            .coerceIn(0.0, 1.0)
                        onSelectIndex(viewport.indexAtFraction(fraction, candles.size))
                    },
                )
            },
    ) {
        if (candles.isEmpty()) return@Canvas
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        val padLeft = 8.0
        val padRight = 8.0
        val plotWidth = max(1.0, w - padLeft - padRight)

        val mainTop = 14.0
        val mainHeight = h * 0.62
        val mainBottom = mainTop + mainHeight

        val volTop = mainBottom + 12.0
        val volHeight = h * 0.14
        val volBottom = volTop + volHeight

        val macdTop = volBottom + 12.0
        val macdHeight = max(24.0, h - macdTop - 20.0)
        val macdBottom = macdTop + macdHeight

        val startIdx = max(0, viewport.lower.toInt())
        val endIdx = min(candles.size - 1, viewport.upper.toInt())
        if (startIdx > endIdx) return@Canvas

        val visibleCandles = candles.subList(startIdx, endIdx + 1)
        var minPrice = visibleCandles.minOf { it.low }
        var maxPrice = visibleCandles.maxOf { it.high }

        if (layers.zhongshu) {
            snapshot.analysis.zhongshu.forEach { z ->
                if (z.endIndex >= startIdx && z.startIndex <= endIdx) {
                    minPrice = min(minPrice, z.low)
                    maxPrice = max(maxPrice, z.high)
                }
            }
        }
        snapshot.signalQuality?.invalidationPrice?.let { refPrice ->
            if (refPrice.isFinite()) {
                minPrice = min(minPrice, refPrice)
                maxPrice = max(maxPrice, refPrice)
            }
        }

        val priceProjection = PriceProjection(
            minimum = minPrice,
            maximum = maxPrice,
            top = mainTop,
            height = mainHeight,
        )

        fun xForIndex(index: Int): Float {
            val ratio = (index.toDouble() + 0.5 - viewport.lower) / viewport.span
            return (padLeft + ratio * plotWidth).toFloat()
        }

        val barSlot = (plotWidth / viewport.span).toFloat()
        val bodyWidth = max(1.5f, min(14f, barSlot * 0.68f))
        val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

        // 1. Horizontal grid lines
        for (step in 0..3) {
            val y = (mainTop + mainHeight * step / 3.0).toFloat()
            drawLine(
                color = ChanTheme.Line,
                start = Offset(padLeft.toFloat(), y),
                end = Offset((w - padRight).toFloat(), y),
                strokeWidth = 1f,
            )
        }
        drawLine(
            color = ChanTheme.Line,
            start = Offset(padLeft.toFloat(), volTop.toFloat()),
            end = Offset((w - padRight).toFloat(), volTop.toFloat()),
            strokeWidth = 1f,
        )
        drawLine(
            color = ChanTheme.Line,
            start = Offset(padLeft.toFloat(), macdTop.toFloat()),
            end = Offset((w - padRight).toFloat(), macdTop.toFloat()),
            strokeWidth = 1f,
        )

        // 2. Zhongshu boxes (under candles so boxes don't hide wicks; labels drawn LAST on top)
        if (layers.zhongshu) {
            snapshot.analysis.zhongshu.forEach { z ->
                if (z.endIndex < startIdx || z.startIndex > endIdx) return@forEach
                val x1 = xForIndex(max(startIdx, z.startIndex))
                val x2 = xForIndex(min(endIdx, z.endIndex))
                val yTop = priceProjection.y(z.high).toFloat()
                val yBottom = priceProjection.y(z.low).toFloat()
                val rectLeft = min(x1, x2)
                val rectWidth = max(4f, abs(x2 - x1))
                val rectTop = min(yTop, yBottom)
                val rectHeight = max(2f, abs(yBottom - yTop))
                drawRect(
                    color = ChanTheme.Violet.copy(alpha = 0.11f),
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(rectWidth, rectHeight),
                )
                drawRect(
                    color = ChanTheme.Violet.copy(alpha = if (z.confirmed) 0.78f else 0.45f),
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(rectWidth, rectHeight),
                    style = Stroke(
                        width = 1.6f,
                        pathEffect = if (z.confirmed) null else dashedEffect,
                    ),
                )
            }
        }

        // 3. Candlesticks & Volume bars
        val maxVol = visibleCandles.maxOfOrNull { it.volume }?.coerceAtLeast(1.0) ?: 1.0
        for (i in startIdx..endIdx) {
            val c = candles[i]
            val cx = xForIndex(i)
            val isUp = c.close >= c.open
            val barColor = if (isUp) ChanTheme.Up else ChanTheme.Down
            val highY = priceProjection.y(c.high).toFloat()
            val lowY = priceProjection.y(c.low).toFloat()
            val openY = priceProjection.y(c.open).toFloat()
            val closeY = priceProjection.y(c.close).toFloat()

            drawLine(
                color = barColor,
                start = Offset(cx, highY),
                end = Offset(cx, lowY),
                strokeWidth = 1.2f,
            )
            val topY = min(openY, closeY)
            val boxH = max(1.5f, abs(closeY - openY))
            drawRect(
                color = barColor,
                topLeft = Offset(cx - bodyWidth / 2f, topY),
                size = Size(bodyWidth, boxH),
            )

            val volRatio = (c.volume / maxVol).coerceIn(0.0, 1.0)
            val vBarH = (volHeight * volRatio).toFloat()
            drawRect(
                color = barColor.copy(alpha = 0.7f),
                topLeft = Offset(cx - bodyWidth / 2f, volBottom.toFloat() - vBarH),
                size = Size(bodyWidth, vBarH),
            )
        }

        // 4. MACD subplot
        if (snapshot.macd.isNotEmpty()) {
            val macdSlice = snapshot.macd.subList(
                startIdx.coerceAtMost(snapshot.macd.size),
                (endIdx + 1).coerceAtMost(snapshot.macd.size),
            )
            val maxAbsMacd = macdSlice.maxOfOrNull {
                max(abs(it.dif), max(abs(it.dea), abs(it.histogram)))
            }?.coerceAtLeast(0.001) ?: 1.0
            val zeroY = (macdTop + macdHeight / 2.0).toFloat()
            drawLine(
                color = ChanTheme.Line,
                start = Offset(padLeft.toFloat(), zeroY),
                end = Offset((w - padRight).toFloat(), zeroY),
                strokeWidth = 1f,
            )

            fun macdY(v: Double): Float =
                (zeroY - (v / maxAbsMacd) * (macdHeight * 0.44)).toFloat()

            val difPath = Path()
            val deaPath = Path()
            var started = false
            for (i in startIdx..min(endIdx, snapshot.macd.size - 1)) {
                val m = snapshot.macd[i]
                val cx = xForIndex(i)
                val hY = macdY(m.histogram)
                val histColor = if (m.histogram >= 0) ChanTheme.Up else ChanTheme.Down
                drawLine(
                    color = histColor.copy(alpha = 0.75f),
                    start = Offset(cx, zeroY),
                    end = Offset(cx, hY),
                    strokeWidth = max(1.2f, bodyWidth * 0.6f),
                )
                val dY = macdY(m.dif)
                val eY = macdY(m.dea)
                if (!started) {
                    difPath.moveTo(cx, dY)
                    deaPath.moveTo(cx, eY)
                    started = true
                } else {
                    difPath.lineTo(cx, dY)
                    deaPath.lineTo(cx, eY)
                }
            }
            if (started) {
                drawPath(difPath, color = ChanTheme.Accent, style = Stroke(width = 1.5f))
                drawPath(deaPath, color = ChanTheme.Gold, style = Stroke(width = 1.5f))
            }
        }

        // 5. Fractals
        if (layers.fractals) {
            snapshot.analysis.fractals.forEach { f ->
                if (f.candleIndex !in startIdx..endIdx) return@forEach
                val cx = xForIndex(f.candleIndex)
                val cy = priceProjection.y(f.price).toFloat() + if (f.type == "top") -6f else 6f
                drawCircle(
                    color = ChanTheme.Fractal,
                    radius = 3.2f,
                    center = Offset(cx, cy),
                )
            }
        }

        // 6. Pens
        if (layers.pens) {
            snapshot.analysis.pens.forEach { pen ->
                if (pen.endIndex < startIdx || pen.startIndex > endIdx) return@forEach
                val x1 = xForIndex(pen.startIndex)
                val y1 = priceProjection.y(pen.startPrice).toFloat()
                val x2 = xForIndex(pen.endIndex)
                val y2 = priceProjection.y(pen.endPrice).toFloat()
                drawLine(
                    color = ChanTheme.Gold,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 2.0f,
                    pathEffect = if (pen.confirmed) null else dashedEffect,
                )
            }
        }

        // 7. Segments
        if (layers.segments) {
            snapshot.analysis.segments.forEach { seg ->
                if (seg.endIndex < startIdx || seg.startIndex > endIdx) return@forEach
                val x1 = xForIndex(seg.startIndex)
                val y1 = priceProjection.y(seg.startPrice).toFloat()
                val x2 = xForIndex(seg.endIndex)
                val y2 = priceProjection.y(seg.endPrice).toFloat()
                drawLine(
                    color = ChanTheme.Accent,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 2.8f,
                    pathEffect = if (seg.confirmed) null else dashedEffect,
                )
            }
        }

        // 8. Invalidation price line if present
        snapshot.signalQuality?.invalidationPrice?.let { invPrice ->
            if (invPrice.isFinite()) {
                val invY = priceProjection.y(invPrice).toFloat()
                if (invY in mainTop.toFloat()..mainBottom.toFloat()) {
                    drawLine(
                        color = ChanTheme.Violet.copy(alpha = 0.7f),
                        start = Offset(padLeft.toFloat(), invY),
                        end = Offset((w - padRight).toFloat(), invY),
                        strokeWidth = 1.2f,
                        pathEffect = dashedEffect,
                    )
                }
            }
        }

        // 9. Selected crosshair
        selectedIndex?.takeIf { it in startIdx..endIdx }?.let { selIdx ->
            val sx = xForIndex(selIdx)
            drawLine(
                color = ChanTheme.Accent.copy(alpha = 0.45f),
                start = Offset(sx, mainTop.toFloat()),
                end = Offset(sx, macdBottom.toFloat()),
                strokeWidth = 1.2f,
                pathEffect = dashedEffect,
            )
        }

        // 10. TOP-MOST TEXT LAYER (Zhongshu labels, Buy/Sell point badges, Axis labels)
        val density = drawContext.density.density
        textPaint.textSize = 10f * density

        // Axis price labels
        textPaint.color = ChanTheme.TextSecondary.toArgb()
        drawContext.canvas.nativeCanvas.drawText(
            MarketFormat.price(priceProjection.maximum, decimals),
            (padLeft + 4).toFloat(),
            (mainTop + 12).toFloat(),
            textPaint,
        )
        drawContext.canvas.nativeCanvas.drawText(
            MarketFormat.price(priceProjection.minimum, decimals),
            (padLeft + 4).toFloat(),
            (mainBottom - 4).toFloat(),
            textPaint,
        )

        // Zhongshu labels on top-most layer so they are never occluded by candles or pens
        if (layers.zhongshu) {
            snapshot.analysis.zhongshu.forEach { z ->
                if (z.endIndex < startIdx || z.startIndex > endIdx) return@forEach
                val x1 = xForIndex(max(startIdx, z.startIndex))
                val yTop = priceProjection.y(z.high).toFloat()
                val label = "Z${z.id}${if (z.confirmed) "" else "?"}"
                val textWidth = textPaint.measureText(label)
                val badgeLeft = (x1 + 3f).coerceIn(padLeft.toFloat(), (w - padRight - textWidth - 10).toFloat())
                val badgeTop = (yTop - 16f).coerceAtLeast(mainTop.toFloat() + 2f)
                drawRoundRect(
                    color = ChanTheme.Sheet.copy(alpha = 0.92f),
                    topLeft = Offset(badgeLeft, badgeTop),
                    size = Size(textWidth + 8f, 15f),
                    cornerRadius = CornerRadius(3f, 3f),
                )
                textPaint.color = ChanTheme.Violet.toArgb()
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    badgeLeft + 4f,
                    badgeTop + 11.5f,
                    textPaint,
                )
            }
        }

        // Buy/Sell signal badges on top-most layer
        if (layers.points) {
            snapshot.signals.points.forEach { pt ->
                if (pt.candleIndex !in startIdx..endIdx) return@forEach
                val cx = xForIndex(pt.candleIndex)
                val cy = priceProjection.y(pt.price).toFloat()
                val isBuy = pt.side == "buy"
                val badgeColor = if (isBuy) ChanTheme.Up else ChanTheme.Down
                val tag = "${pt.shortTag}${if (pt.confirmed) "" else "?"}"
                val tw = textPaint.measureText(tag)
                val boxW = tw + 10f
                val boxH = 16f
                val boxLeft = (cx - boxW / 2f).coerceIn(padLeft.toFloat(), (w - padRight - boxW).toFloat())
                val boxTop = if (isBuy) {
                    min((mainBottom - boxH - 2).toFloat(), cy + 6f)
                } else {
                    max(mainTop.toFloat() + 2f, cy - boxH - 6f)
                }
                drawRoundRect(
                    color = if (pt.confirmed) badgeColor else ChanTheme.Sheet,
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(3f, 3f),
                )
                if (!pt.confirmed) {
                    drawRoundRect(
                        color = badgeColor,
                        topLeft = Offset(boxLeft, boxTop),
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(3f, 3f),
                        style = Stroke(width = 1.3f, pathEffect = dashedEffect),
                    )
                }
                textPaint.color = (if (pt.confirmed) Color.White else badgeColor).toArgb()
                drawContext.canvas.nativeCanvas.drawText(
                    tag,
                    boxLeft + 5f,
                    boxTop + 12f,
                    textPaint,
                )
            }
        }

        // Bottom time axis labels
        val zone = MarketTime.zone(snapshot.instrument.currency)
        val fmt = if (snapshot.timeframe == "1d") "yyyy-MM-dd" else "MM-dd HH:mm"
        val firstDate = MarketTime.formatDate(candles[startIdx].time, fmt, zone)
        val lastDate = MarketTime.formatDate(candles[endIdx].time, fmt, zone)
        textPaint.color = ChanTheme.TextSecondary.toArgb()
        drawContext.canvas.nativeCanvas.drawText(
            firstDate,
            padLeft.toFloat(),
            (h - 4).toFloat(),
            textPaint,
        )
        val lastWidth = textPaint.measureText(lastDate)
        drawContext.canvas.nativeCanvas.drawText(
            lastDate,
            (w - padRight - lastWidth).toFloat(),
            (h - 4).toFloat(),
            textPaint,
        )
    }
}
