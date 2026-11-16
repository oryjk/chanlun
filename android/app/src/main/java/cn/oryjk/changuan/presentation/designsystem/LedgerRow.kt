package cn.oryjk.changuan.presentation.designsystem

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.oryjk.changuan.presentation.ChanPalette
import cn.oryjk.changuan.presentation.LocalChanPalette

private const val LedgerTransitionMillis = 220

@Composable
fun LedgerHeaderRule(modifier: Modifier = Modifier) {
    val palette = LocalChanPalette.current
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 0.5.dp,
        color = palette.cardBorder,
    )
}

@Composable
fun LedgerRow(
    title: String,
    headlineValue: String,
    headlineLabel: String,
    headlineColor: Color,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    titlePrefix: (@Composable () -> Unit)? = null,
    titleSuffix: (@Composable () -> Unit)? = null,
    metaContent: @Composable () -> Unit,
    sparkline: (@Composable () -> Unit)? = null,
    expandable: Boolean = true,
    headerStateDescription: String? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    showDivider: Boolean = true,
    expandedContent: @Composable ColumnScope.() -> Unit = {},
) {
    val palette = LocalChanPalette.current
    val context = LocalContext.current
    val scopeMotionScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor
    val motionScale = scopeMotionScale ?: Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    val animate = motionScale > 0f
    val isExpanded = expandable && expanded

    val rowBackground = if (animate) {
        animateColorAsState(
            targetValue = if (isExpanded) palette.subtleSurface.copy(alpha = 0.45f) else Color.Transparent,
            animationSpec = tween(durationMillis = LedgerTransitionMillis),
            label = "ledger-row-background",
        ).value
    } else {
        if (isExpanded) palette.subtleSurface.copy(alpha = 0.45f) else Color.Transparent
    }

    val chevronRotation = if (animate) {
        animateFloatAsState(
            targetValue = if (isExpanded) 180f else 0f,
            animationSpec = tween(durationMillis = LedgerTransitionMillis),
            label = "ledger-row-chevron",
        ).value
    } else {
        if (isExpanded) 180f else 0f
    }

    val resolvedClickLabel = if (expandable) {
        if (isExpanded) "收起" else "展开"
    } else {
        null
    }
    val resolvedStateDescription = if (expandable) {
        val expandState = if (isExpanded) "已展开" else "已收起"
        if (headerStateDescription != null) "$headerStateDescription，$expandState" else expandState
    } else {
        headerStateDescription
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = resolvedClickLabel,
                    onClick = onToggleExpand,
                )
                .then(
                    if (resolvedStateDescription != null) {
                        Modifier.semantics { stateDescription = resolvedStateDescription }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (titlePrefix != null) {
                        titlePrefix()
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (titleSuffix != null) Modifier.weight(1f, fill = false) else Modifier,
                    )
                    if (titleSuffix != null) {
                        Spacer(Modifier.width(6.dp))
                        titleSuffix()
                    }
                }
                metaContent()
            }
            if (trailingContent != null) {
                trailingContent()
            } else {
                if (sparkline != null) {
                    sparkline()
                    Spacer(Modifier.width(10.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    FinancialNumber(
                        value = headlineValue,
                        color = headlineColor,
                    )
                    Text(
                        text = headlineLabel,
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = signedColorFromText(headlineLabel, palette),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                if (expandable) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = palette.inkMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(chevronRotation),
                    )
                }
            }
        }

        if (expandable) {
            if (animate) {
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(animationSpec = tween(LedgerTransitionMillis)) +
                        fadeIn(animationSpec = tween(LedgerTransitionMillis)),
                    exit = shrinkVertically(animationSpec = tween(LedgerTransitionMillis)) +
                        fadeOut(animationSpec = tween(LedgerTransitionMillis)),
                ) {
                    LedgerExpandedContainer(expandedContent)
                }
            } else if (isExpanded) {
                LedgerExpandedContainer(expandedContent)
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = palette.cardBorder,
            )
        }
    }
}

private fun signedColorFromText(text: String, palette: ChanPalette): Color {
    val trimmed = text.trim()
    return when {
        trimmed.startsWith("+") -> palette.positiveRed
        trimmed.startsWith("-") -> palette.negativeGreen
        else -> palette.inkMuted
    }
}

@Composable
private fun LedgerExpandedContainer(
    expandedContent: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = expandedContent,
    )
}

fun validSparklinePoints(points: List<Double>?): List<Double>? {
    if (points == null || points.size < 2) return null
    if (points.any { !it.isFinite() }) return null
    return points
}

@Composable
fun LedgerSparkline(
    points: List<Double>,
    changePercent: Double?,
    modifier: Modifier = Modifier,
) {
    val valid = validSparklinePoints(points) ?: return
    val palette = LocalChanPalette.current
    val strokeColor = when {
        changePercent != null && changePercent > 0.0 -> palette.positiveRed
        changePercent != null && changePercent < 0.0 -> palette.negativeGreen
        valid.last() > valid.first() -> palette.positiveRed
        valid.last() < valid.first() -> palette.negativeGreen
        else -> palette.inkSecondary
    }

    Box(modifier = modifier.size(width = 56.dp, height = 28.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics {},
        ) {
            val hPad = 1.5.dp.toPx()
            val vPad = 3.dp.toPx()
            val plotW = (size.width - hPad * 2f).coerceAtLeast(1f)
            val plotH = (size.height - vPad * 2f).coerceAtLeast(1f)
            val minVal = valid.min()
            val maxVal = valid.max()
            val span = maxVal - minVal
            val isFlat = !span.isFinite() || span <= 1e-9
            val stepX = plotW / (valid.size - 1)
            val offsets = valid.mapIndexed { index, value ->
                val x = hPad + index * stepX
                val y = if (isFlat) {
                    size.height * 0.5f
                } else {
                    val norm = ((value - minVal) / span).toFloat().coerceIn(0f, 1f)
                    vPad + (1f - norm) * plotH
                }
                Offset(x, y)
            }

            val linePath = Path().apply {
                offsets.forEachIndexed { idx, offset ->
                    if (idx == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
                }
            }

            if (!isFlat) {
                val bottomY = (size.height - vPad).coerceAtLeast(vPad)
                val fillPath = Path().apply {
                    addPath(linePath)
                    lineTo(offsets.last().x, bottomY)
                    lineTo(offsets.first().x, bottomY)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            strokeColor.copy(alpha = 0.14f),
                            strokeColor.copy(alpha = 0.0f),
                        ),
                        startY = vPad,
                        endY = bottomY,
                    ),
                )
            }

            drawPath(
                path = linePath,
                color = strokeColor,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}
