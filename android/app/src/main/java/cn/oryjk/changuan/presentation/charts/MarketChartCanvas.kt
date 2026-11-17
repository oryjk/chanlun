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
import androidx.compose.ui.unit.dp
import cn.oryjk.changuan.domain.ChartLayers
import cn.oryjk.changuan.domain.ChartViewport
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.MarketSnapshot
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.PriceProjection
import cn.oryjk.changuan.domain.computeLabelPillBox
import cn.oryjk.changuan.domain.computePointBadgeBox
import cn.oryjk.changuan.presentation.LocalChanPalette
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
    val palette = LocalChanPalette.current
    val candles = snapshot.candles
    var viewport by remember(snapshot.instrument.secid, snapshot.timeframe, candles.size) {
        mutableStateOf(ChartViewport.initial(candles.size))
    }

    LaunchedEffect(snapshot.instrument.secid, snapshot.timeframe) {
        viewport = ChartViewport.initial(candles.size)
    }

    val axisTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SANS_SERIF
        }
    }
    val badgeTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
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
        val padLeft = 8.dp.toPx().toDouble()
        val padRight = 8.dp.toPx().toDouble()
        val plotWidth = max(1.0, w - padLeft - padRight)

        val mainTop = 6.dp.toPx().toDouble()
        val sectionGap = 10.dp.toPx().toDouble()
        val dateFooterH = 18.dp.toPx().toDouble()
        val usableH = max(80.dp.toPx().toDouble(), h - mainTop - sectionGap * 2.0 - dateFooterH)
        val mainHeight = usableH * 0.66
        val mainBottom = mainTop + mainHeight

        val volTop = mainBottom + sectionGap
        val volHeight = usableH * 0.14
        val volBottom = volTop + volHeight

        val macdTop = volBottom + sectionGap
        val macdHeight = max(20.dp.toPx().toDouble(), usableH - mainHeight - volHeight)
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

        val badgeReserve = min(22.dp.toPx().toDouble(), mainHeight * 0.20)
        val priceProjection = PriceProjection(
            minimum = minPrice,
            maximum = maxPrice,
            top = mainTop + badgeReserve,
            height = max(24.dp.toPx().toDouble(), mainHeight - badgeReserve * 2.0),
        )

        fun xForIndex(index: Int): Float {
            val ratio = (index.toDouble() + 0.5 - viewport.lower) / viewport.span
            return (padLeft + ratio * plotWidth).toFloat()
        }

        val barSlot = (plotWidth / viewport.span).toFloat()
        val bodyWidth = max(1.2.dp.toPx(), min(10.dp.toPx(), barSlot * 0.68f))
        val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f)
        val gridStroke = 0.75.dp.toPx()

        // 1. Horizontal grid lines
        for (step in 0..3) {
            val y = (mainTop + mainHeight * step / 3.0).toFloat()
            drawLine(
                color = palette.cardBorder,
                start = Offset(padLeft.toFloat(), y),
                end = Offset((w - padRight).toFloat(), y),
                strokeWidth = gridStroke,
            )
        }
        drawLine(
            color = palette.cardBorder,
            start = Offset(padLeft.toFloat(), volTop.toFloat()),
            end = Offset((w - padRight).toFloat(), volTop.toFloat()),
            strokeWidth = gridStroke,
        )
        drawLine(
            color = palette.cardBorder,
            start = Offset(padLeft.toFloat(), macdTop.toFloat()),
            end = Offset((w - padRight).toFloat(), macdTop.toFloat()),
            strokeWidth = gridStroke,
        )

        // 2. Zhongshu boxes (under candles so boxes don't hide wicks; labels drawn LAST on top)
        if (layers.zhongshu) {
            val minRectW = 3.dp.toPx()
            val minRectH = 1.5.dp.toPx()
            val zoneStroke = 1.2.dp.toPx()
            snapshot.analysis.zhongshu.forEach { z ->
                if (z.endIndex < startIdx || z.startIndex > endIdx) return@forEach
                val x1 = xForIndex(max(startIdx, z.startIndex))
                val x2 = xForIndex(min(endIdx, z.endIndex))
                val yTop = priceProjection.y(z.high).toFloat()
                val yBottom = priceProjection.y(z.low).toFloat()
                val rectLeft = min(x1, x2)
                val rectWidth = max(minRectW, abs(x2 - x1))
                val rectTop = min(yTop, yBottom)
                val rectHeight = max(minRectH, abs(yBottom - yTop))
                drawRect(
                    color = palette.zhongshuViolet.copy(alpha = if (palette.isDark) 0.16f else 0.10f),
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(rectWidth, rectHeight),
                )
                drawRect(
                    color = palette.zhongshuViolet.copy(alpha = if (z.confirmed) 0.78f else 0.45f),
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(rectWidth, rectHeight),
                    style = Stroke(
                        width = zoneStroke,
                        pathEffect = if (z.confirmed) null else dashedEffect,
                    ),
                )
            }
        }

        // 3. Candlesticks & Volume bars
        val maxVol = visibleCandles.maxOfOrNull { it.volume }?.coerceAtLeast(1.0) ?: 1.0
        val wickStroke = 1.dp.toPx()
        val minBodyH = 1.2.dp.toPx()
        for (i in startIdx..endIdx) {
            val c = candles[i]
            val cx = xForIndex(i)
            val isUp = c.close >= c.open
            val barColor = if (isUp) palette.positiveRed else palette.negativeGreen
            val highY = priceProjection.y(c.high).toFloat()
            val lowY = priceProjection.y(c.low).toFloat()
            val openY = priceProjection.y(c.open).toFloat()
            val closeY = priceProjection.y(c.close).toFloat()

            drawLine(
                color = barColor,
                start = Offset(cx, highY),
                end = Offset(cx, lowY),
                strokeWidth = wickStroke,
            )
            val topY = min(openY, closeY)
            val boxH = max(minBodyH, abs(closeY - openY))
            drawRect(
                color = barColor,
                topLeft = Offset(cx - bodyWidth / 2f, topY),
                size = Size(bodyWidth, boxH),
            )

            val volRatio = (c.volume / maxVol).coerceIn(0.0, 1.0)
            val vBarH = (volHeight * volRatio).toFloat()
            drawRect(
                color = barColor.copy(alpha = 0.68f),
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
                color = palette.cardBorder,
                start = Offset(padLeft.toFloat(), zeroY),
                end = Offset((w - padRight).toFloat(), zeroY),
                strokeWidth = gridStroke,
            )

            fun macdY(v: Double): Float =
                (zeroY - (v / maxAbsMacd) * (macdHeight * 0.44)).toFloat()

            val difPath = Path()
            val deaPath = Path()
            var started = false
            val macdBarStroke = max(1.dp.toPx(), bodyWidth * 0.6f)
            val macdLineStroke = 1.2.dp.toPx()
            for (i in startIdx..min(endIdx, snapshot.macd.size - 1)) {
                val m = snapshot.macd[i]
                val cx = xForIndex(i)
                val hY = macdY(m.histogram)
                val histColor = if (m.histogram >= 0) palette.positiveRed else palette.negativeGreen
                drawLine(
                    color = histColor.copy(alpha = 0.72f),
                    start = Offset(cx, zeroY),
                    end = Offset(cx, hY),
                    strokeWidth = macdBarStroke,
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
                drawPath(difPath, color = palette.segmentBlue, style = Stroke(width = macdLineStroke))
                drawPath(deaPath, color = palette.penGold, style = Stroke(width = macdLineStroke))
            }
        }

        // 5. Fractals
        if (layers.fractals) {
            val fractalOffset = 4.dp.toPx()
            val fractalRadius = 2.2.dp.toPx()
            snapshot.analysis.fractals.forEach { f ->
                if (f.candleIndex !in startIdx..endIdx) return@forEach
                val cx = xForIndex(f.candleIndex)
                val cy = priceProjection.y(f.price).toFloat() + if (f.type == "top") -fractalOffset else fractalOffset
                drawCircle(
                    color = palette.fractalSlate,
                    radius = fractalRadius,
                    center = Offset(cx, cy),
                )
            }
        }

        // 6. Pens
        if (layers.pens) {
            val penStroke = 1.5.dp.toPx()
            snapshot.analysis.pens.forEach { pen ->
                if (pen.endIndex < startIdx || pen.startIndex > endIdx) return@forEach
                val x1 = xForIndex(pen.startIndex)
                val y1 = priceProjection.y(pen.startPrice).toFloat()
                val x2 = xForIndex(pen.endIndex)
                val y2 = priceProjection.y(pen.endPrice).toFloat()
                drawLine(
                    color = palette.penGold,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = penStroke,
                    pathEffect = if (pen.confirmed) null else dashedEffect,
                )
            }
        }

        // 7. Segments
        if (layers.segments) {
            val segStroke = 2.0.dp.toPx()
            snapshot.analysis.segments.forEach { seg ->
                if (seg.endIndex < startIdx || seg.startIndex > endIdx) return@forEach
                val x1 = xForIndex(seg.startIndex)
                val y1 = priceProjection.y(seg.startPrice).toFloat()
                val x2 = xForIndex(seg.endIndex)
                val y2 = priceProjection.y(seg.endPrice).toFloat()
                drawLine(
                    color = palette.segmentBlue,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = segStroke,
                    pathEffect = if (seg.confirmed) null else dashedEffect,
                )
            }
        }

        // 8. Invalidation price line if present
        val guideStroke = 1.dp.toPx()
        snapshot.signalQuality?.invalidationPrice?.let { invPrice ->
            if (invPrice.isFinite()) {
                val invY = priceProjection.y(invPrice).toFloat()
                if (invY in mainTop.toFloat()..mainBottom.toFloat()) {
                    drawLine(
                        color = palette.zhongshuViolet.copy(alpha = 0.72f),
                        start = Offset(padLeft.toFloat(), invY),
                        end = Offset((w - padRight).toFloat(), invY),
                        strokeWidth = guideStroke,
                        pathEffect = dashedEffect,
                    )
                }
            }
        }

        // 9. Selected crosshair
        selectedIndex?.takeIf { it in startIdx..endIdx }?.let { selIdx ->
            val sx = xForIndex(selIdx)
            drawLine(
                color = palette.inkSecondary.copy(alpha = 0.55f),
                start = Offset(sx, mainTop.toFloat()),
                end = Offset(sx, macdBottom.toFloat()),
                strokeWidth = guideStroke,
                pathEffect = dashedEffect,
            )
        }

        // 10. TOP-MOST TEXT LAYER (Zhongshu labels, Buy/Sell point badges, Axis labels)
        axisTextPaint.textSize = 10.dp.toPx()
        badgeTextPaint.textSize = 10.dp.toPx()
        val axisFm = axisTextPaint.fontMetrics
        val badgeFm = badgeTextPaint.fontMetrics

        val axisX = padLeft.toFloat() + 3.dp.toPx()
        val topAxisBaseline = mainTop.toFloat() + 2.dp.toPx() - axisFm.ascent
        val bottomAxisBaseline = mainBottom.toFloat() - 2.dp.toPx() - axisFm.descent
        axisTextPaint.color = palette.inkMuted.toArgb()
        drawContext.canvas.nativeCanvas.drawText(
            MarketFormat.price(priceProjection.priceAt(mainTop), decimals),
            axisX,
            topAxisBaseline,
            axisTextPaint,
        )
        drawContext.canvas.nativeCanvas.drawText(
            MarketFormat.price(priceProjection.priceAt(mainBottom), decimals),
            axisX,
            bottomAxisBaseline,
            axisTextPaint,
        )

        val minBadgeY = mainTop.toFloat() + 2.dp.toPx()
        val maxBadgeY = mainBottom.toFloat() - 2.dp.toPx()

        if (layers.zhongshu) {
            val zPadX = 4.dp.toPx()
            val zPadY = 2.dp.toPx()
            val zGap = 2.dp.toPx()
            val zAnchorOffset = 3.dp.toPx()
            val zRadius = 3.dp.toPx()
            val zBorderStroke = 0.8.dp.toPx()
            snapshot.analysis.zhongshu.forEach { z ->
                if (z.endIndex < startIdx || z.startIndex > endIdx) return@forEach
                val x1 = xForIndex(max(startIdx, z.startIndex))
                val yTop = priceProjection.y(z.high).toFloat()
                val label = "Z${z.id}${if (z.confirmed) "" else "?"}"
                val pill = computeLabelPillBox(
                    anchorLeft = x1 + zAnchorOffset,
                    anchorTop = yTop - zGap,
                    textWidth = badgeTextPaint.measureText(label),
                    fontAscent = badgeFm.ascent,
                    fontDescent = badgeFm.descent,
                    padX = zPadX,
                    padY = zPadY,
                    minX = padLeft.toFloat(),
                    maxX = (w - padRight).toFloat(),
                    minY = minBadgeY,
                    maxY = maxBadgeY,
                )
                drawRoundRect(
                    color = palette.backgroundCanvas.copy(alpha = 0.92f),
                    topLeft = Offset(pill.left, pill.top),
                    size = Size(pill.width, pill.height),
                    cornerRadius = CornerRadius(zRadius, zRadius),
                )
                drawRoundRect(
                    color = palette.zhongshuViolet.copy(alpha = 0.35f),
                    topLeft = Offset(pill.left, pill.top),
                    size = Size(pill.width, pill.height),
                    cornerRadius = CornerRadius(zRadius, zRadius),
                    style = Stroke(width = zBorderStroke),
                )
                badgeTextPaint.color = palette.zhongshuViolet.toArgb()
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    pill.textX,
                    pill.textBaselineY,
                    badgeTextPaint,
                )
            }
        }

        if (layers.points) {
            val pPadX = 5.dp.toPx()
            val pPadY = 2.5.dp.toPx()
            val pAnchorGap = 7.dp.toPx()
            val pRadius = 3.5.dp.toPx()
            val candidateBorder = 1.2.dp.toPx()
            snapshot.signals.points.forEach { pt ->
                if (pt.candleIndex !in startIdx..endIdx) return@forEach
                val cx = xForIndex(pt.candleIndex)
                val cy = priceProjection.y(pt.price).toFloat()
                val isBuy = pt.side == "buy"
                val badgeColor = if (isBuy) palette.positiveRed else palette.negativeGreen
                val tag = "${pt.shortTag}${if (pt.confirmed) "" else "?"}"
                val box = computePointBadgeBox(
                    anchorX = cx,
                    anchorY = cy,
                    isBelowAnchor = isBuy,
                    textWidth = badgeTextPaint.measureText(tag),
                    fontAscent = badgeFm.ascent,
                    fontDescent = badgeFm.descent,
                    padX = pPadX,
                    padY = pPadY,
                    anchorGap = pAnchorGap,
                    minX = padLeft.toFloat(),
                    maxX = (w - padRight).toFloat(),
                    minY = minBadgeY,
                    maxY = maxBadgeY,
                )
                drawRoundRect(
                    color = if (pt.confirmed) badgeColor else palette.backgroundCanvas,
                    topLeft = Offset(box.left, box.top),
                    size = Size(box.width, box.height),
                    cornerRadius = CornerRadius(pRadius, pRadius),
                )
                if (!pt.confirmed) {
                    drawRoundRect(
                        color = badgeColor,
                        topLeft = Offset(box.left, box.top),
                        size = Size(box.width, box.height),
                        cornerRadius = CornerRadius(pRadius, pRadius),
                        style = Stroke(width = candidateBorder, pathEffect = dashedEffect),
                    )
                }
                badgeTextPaint.color = (if (pt.confirmed) Color.White else badgeColor).toArgb()
                drawContext.canvas.nativeCanvas.drawText(
                    tag,
                    box.textX,
                    box.textBaselineY,
                    badgeTextPaint,
                )
            }
        }

        val zone = MarketTime.zone(snapshot.instrument.currency)
        val fmt = if (snapshot.timeframe == "1d") "yyyy-MM-dd" else "MM-dd HH:mm"
        val firstDate = MarketTime.formatDate(candles[startIdx].time, fmt, zone)
        val lastDate = MarketTime.formatDate(candles[endIdx].time, fmt, zone)
        val dateBaseline = h.toFloat() - 2.dp.toPx() - axisFm.descent
        axisTextPaint.color = palette.inkMuted.toArgb()
        drawContext.canvas.nativeCanvas.drawText(
            firstDate,
            padLeft.toFloat(),
            dateBaseline,
            axisTextPaint,
        )
        val lastWidth = axisTextPaint.measureText(lastDate)
        drawContext.canvas.nativeCanvas.drawText(
            lastDate,
            (w - padRight - lastWidth).toFloat(),
            dateBaseline,
            axisTextPaint,
        )
    }
}
