package cn.oryjk.changuan.presentation.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.oryjk.changuan.domain.TrackingGrowthStage
import cn.oryjk.changuan.presentation.LocalChanPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SproutSeedScale = 0.25f
private val SheetTopPeek = 28.dp
private val SheetDismissDistance = 160.dp
private val SheetFlingMinDistance = 24.dp
private val SheetFlingVelocity = 1_000.dp
private const val SheetScrimAlpha = 0.36f

@Composable
fun TrackingActionPill(
    tracked: Boolean,
    pending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = !pending,
    large: Boolean = false,
    horizontalPadding: Dp = if (large) 16.dp else 12.dp,
) {
    val sawUntracked = remember { BooleanArray(1) }
    if (!tracked) sawUntracked[0] = true
    if (tracked && !pending) {
        CompanionTrackingPill(
            onClick = onClick,
            modifier = modifier,
            large = large,
            enabled = enabled,
            animateIn = sawUntracked[0],
            horizontalPadding = horizontalPadding,
        )
    } else {
        ActionPill(
            text = if (pending) "同步中…" else "+ 跟踪",
            onClick = onClick,
            modifier = modifier,
            active = tracked,
            warm = tracked,
            enabled = enabled,
            large = large,
            horizontalPadding = horizontalPadding,
        )
    }
}

@Composable
fun CompanionTrackingPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "跟踪中",
    large: Boolean = false,
    compact: Boolean = false,
    enabled: Boolean = true,
    animateIn: Boolean = false,
    onClickLabel: String? = "结束跟踪",
    horizontalPadding: Dp = if (large) 15.dp else if (compact) 8.dp else 11.dp,
    pillTestTag: String? = null,
    a11yContentDescription: String? = null,
) {
    val palette = LocalChanPalette.current
    val growth = remember { Animatable(if (animateIn) SproutSeedScale else 1f) }
    LaunchedEffect(Unit) {
        if (growth.value < 1f) {
            growth.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
    }
    val stemColor = palette.warmAmberText
    val leafColor = palette.warmAmber
    if (compact) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = modifier
                .defaultMinSize(minHeight = 40.dp)
                .then(if (pillTestTag != null) Modifier.testTag("${pillTestTag}-touch") else Modifier)
                .clickable(
                    enabled = enabled,
                    onClickLabel = onClickLabel,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = null,
                ) { onClick() }
                .semantics {
                    stateDescription = "已加入跟踪"
                    if (a11yContentDescription != null) {
                        contentDescription = a11yContentDescription
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .then(if (pillTestTag != null) Modifier.testTag(pillTestTag) else Modifier)
                    .graphicsLayer { alpha = if (enabled) 1f else 0.65f }
                    .clip(ClarityRadius.pill)
                    .indication(interactionSource, ripple())
                    .background(palette.warmAmberSurface, ClarityRadius.pill)
                    .border(1.dp, palette.warmAmberBorder, ClarityRadius.pill),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(14.dp)) {
                        drawSprout(growth = growth.value, stemColor = stemColor, leafColor = leafColor)
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.warmAmberText,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        return
    }
    Box(
        modifier = modifier
            .then(if (pillTestTag != null) Modifier.testTag(pillTestTag) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.65f }
            .clip(ClarityRadius.pill)
            .clickable(enabled = enabled, onClickLabel = onClickLabel, role = Role.Button) { onClick() }
            .semantics { stateDescription = "已加入跟踪" }
            .background(palette.warmAmberSurface, ClarityRadius.pill)
            .border(1.dp, palette.warmAmberBorder, ClarityRadius.pill),
        contentAlignment = Alignment.Center,
    ) {
        AdaptivePillContent(
            text = text,
            style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            color = palette.warmAmberText,
            horizontalPadding = horizontalPadding,
            verticalPadding = if (large) 7.dp else 6.dp,
            iconGap = if (large) 6.dp else 4.dp,
            adaptiveIcon = !large,
            leadingIcon = {
                Canvas(Modifier.size(if (large) 18.dp else 14.dp)) {
                    drawSprout(growth = growth.value, stemColor = stemColor, leafColor = leafColor)
                }
            },
        )
    }
}

private fun DrawScope.drawSprout(growth: Float, stemColor: Color, leafColor: Color) {
    val s = minOf(size.width, size.height)
    if (s <= 0f) return
    val root = Offset(size.width / 2f, size.height / 2f + s * 0.46f)
    fun at(x: Float, y: Float) = Offset(size.width / 2f + (x - 0.5f) * s, size.height / 2f + (y - 0.5f) * s)

    scale(scale = growth.coerceAtLeast(0f), pivot = root) {
        val fork = at(0.50f, 0.52f)
        drawLine(
            color = stemColor,
            start = root,
            end = fork,
            strokeWidth = s * 0.11f,
            cap = StrokeCap.Round,
        )
        drawPath(leafPath(base = fork, tip = at(0.10f, 0.34f), bulge = s * 0.20f), stemColor)
        drawPath(leafPath(base = fork, tip = at(0.94f, 0.10f), bulge = -s * 0.24f), leafColor)
    }
}

private fun leafPath(base: Offset, tip: Offset, bulge: Float): Path {
    val dx = tip.x - base.x
    val dy = tip.y - base.y
    val len = hypot(dx, dy).coerceAtLeast(1e-4f)
    val nx = -dy / len
    val ny = dx / len
    val mid = Offset((base.x + tip.x) / 2f, (base.y + tip.y) / 2f)
    val outer = Offset(mid.x + nx * bulge, mid.y + ny * bulge)
    val inner = Offset(mid.x - nx * bulge * 0.25f, mid.y - ny * bulge * 0.25f)
    return Path().apply {
        moveTo(base.x, base.y)
        cubicTo(outer.x, outer.y, outer.x, outer.y, tip.x, tip.y)
        cubicTo(inner.x, inner.y, inner.x, inner.y, base.x, base.y)
        close()
    }
}

@Composable
fun TrackingGrowthIcon(
    stage: TrackingGrowthStage,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    contentDescription: String? = stage.label,
) {
    val palette = LocalChanPalette.current
    val lightBrand = if (palette.isDark) {
        lerp(palette.primaryBrand, palette.inkPrimary, 0.35f)
    } else {
        lerp(palette.primaryBrand, palette.backgroundCanvas, 0.42f)
    }
    val deepBrand = if (palette.isDark) {
        lerp(palette.primaryBrand, palette.backgroundCanvas, 0.28f)
    } else {
        lerp(palette.primaryBrand, palette.inkPrimary, 0.38f)
    }
    val amber = palette.warmAmber
    val deepWarm = palette.warmAmberText
    val softGround = palette.warmAmberSurface

    val canvasModifier = modifier
        .size(size)
        .let { base ->
            if (contentDescription != null) {
                base.semantics { this.contentDescription = contentDescription }
            } else {
                base
            }
        }

    Canvas(modifier = canvasModifier) {
        val w = this.size.width
        val h = this.size.height
        val groundY = h * 0.87f

        drawLine(
            color = if (stage == TrackingGrowthStage.SPROUT || stage == TrackingGrowthStage.NEW_LEAF) {
                deepWarm.copy(alpha = 0.32f)
            } else {
                deepBrand.copy(alpha = 0.26f)
            },
            start = Offset(w * 0.28f, groundY),
            end = Offset(w * 0.72f, groundY),
            strokeWidth = (w * 0.055f).coerceAtLeast(1.2f),
            cap = StrokeCap.Round,
        )

        when (stage) {
            TrackingGrowthStage.SPROUT -> {
                val stem = Path().apply {
                    moveTo(w * 0.46f, groundY)
                    quadraticTo(w * 0.45f, h * 0.68f, w * 0.50f, h * 0.54f)
                }
                drawPath(
                    path = stem,
                    color = deepWarm,
                    style = Stroke(
                        width = (w * 0.075f).coerceAtLeast(1.6f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                drawBotanicalLeaf(
                    base = Offset(w * 0.49f, h * 0.56f),
                    length = w * 0.31f,
                    halfWidth = w * 0.135f,
                    angleDeg = -48f,
                    color = amber,
                )
            }

            TrackingGrowthStage.NEW_LEAF -> {
                val stem = Path().apply {
                    moveTo(w * 0.50f, groundY)
                    quadraticTo(w * 0.52f, h * 0.62f, w * 0.50f, h * 0.38f)
                }
                drawPath(
                    path = stem,
                    color = deepWarm,
                    style = Stroke(
                        width = (w * 0.075f).coerceAtLeast(1.6f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                drawBotanicalLeaf(
                    base = Offset(w * 0.50f, h * 0.61f),
                    length = w * 0.29f,
                    halfWidth = w * 0.12f,
                    angleDeg = -142f,
                    color = deepWarm,
                )
                drawBotanicalLeaf(
                    base = Offset(w * 0.50f, h * 0.43f),
                    length = w * 0.33f,
                    halfWidth = w * 0.135f,
                    angleDeg = -36f,
                    color = amber,
                )
            }

            TrackingGrowthStage.SEEDLING -> {
                val stem = Path().apply {
                    moveTo(w * 0.50f, groundY)
                    quadraticTo(w * 0.48f, h * 0.56f, w * 0.50f, h * 0.24f)
                }
                drawPath(
                    path = stem,
                    color = deepBrand,
                    style = Stroke(
                        width = (w * 0.078f).coerceAtLeast(1.8f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                drawBotanicalLeaf(Offset(w * 0.49f, h * 0.66f), w * 0.28f, w * 0.11f, -145f, palette.primaryBrand)
                drawBotanicalLeaf(Offset(w * 0.49f, h * 0.54f), w * 0.29f, w * 0.115f, -35f, palette.primaryBrand)
                drawBotanicalLeaf(Offset(w * 0.50f, h * 0.40f), w * 0.26f, w * 0.105f, -132f, lightBrand)
                drawBotanicalLeaf(Offset(w * 0.50f, h * 0.26f), w * 0.25f, w * 0.10f, -46f, lightBrand)
            }

            TrackingGrowthStage.SAPLING -> {
                drawLine(
                    color = deepBrand,
                    start = Offset(w * 0.50f, groundY),
                    end = Offset(w * 0.50f, h * 0.44f),
                    strokeWidth = (w * 0.092f).coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = deepBrand,
                    start = Offset(w * 0.50f, h * 0.62f),
                    end = Offset(w * 0.60f, h * 0.50f),
                    strokeWidth = (w * 0.055f).coerceAtLeast(1.2f),
                    cap = StrokeCap.Round,
                )
                drawCircle(color = palette.primaryBrand, radius = w * 0.255f, center = Offset(w * 0.50f, h * 0.38f))
                drawCircle(color = lightBrand, radius = w * 0.12f, center = Offset(w * 0.43f, h * 0.32f))
            }

            TrackingGrowthStage.TREE -> {
                drawCanopyCrown(w, h, deepBrand, palette.primaryBrand, lightBrand)
                drawTaperedTrunk(w, h, groundY, deepBrand)
            }

            TrackingGrowthStage.YEAR_RING -> {
                drawCanopyCrown(w, h, deepBrand, palette.primaryBrand, lightBrand)
                drawTaperedTrunk(w, h, groundY, deepBrand)
                val fruitRadius = (w * 0.068f).coerceAtLeast(1.6f)
                val fruitCenters = listOf(
                    Offset(w * 0.31f, h * 0.43f),
                    Offset(w * 0.50f, h * 0.24f),
                    Offset(w * 0.69f, h * 0.42f),
                    Offset(w * 0.41f, h * 0.34f),
                    Offset(w * 0.60f, h * 0.33f),
                )
                val fruitGleam = if (palette.isDark) lerp(amber, palette.inkPrimary, 0.55f) else softGround
                fruitCenters.forEach { center ->
                    drawCircle(color = amber, radius = fruitRadius, center = center)
                    drawCircle(
                        color = fruitGleam,
                        radius = fruitRadius * 0.30f,
                        center = Offset(center.x - fruitRadius * 0.24f, center.y - fruitRadius * 0.24f),
                    )
                }
            }
        }
    }
}

@Composable
fun GrowthStageTag(
    stage: TrackingGrowthStage,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChanPalette.current
    val (bgColor, fgColor) = when (stage) {
        TrackingGrowthStage.SPROUT,
        TrackingGrowthStage.NEW_LEAF,
        -> palette.warmAmberSurface to palette.warmAmberText

        TrackingGrowthStage.SEEDLING,
        TrackingGrowthStage.SAPLING,
        -> palette.primaryBrandSoft to palette.primaryBrand

        TrackingGrowthStage.TREE -> palette.primaryBrand to palette.backgroundCanvas

        TrackingGrowthStage.YEAR_RING -> palette.warmAmberText to palette.warmAmberSurface
    }
    Box(
        modifier = modifier
            .background(color = bgColor, shape = ClarityRadius.pill)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = stage.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fgColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun DrawScope.drawBotanicalLeaf(
    base: Offset,
    length: Float,
    halfWidth: Float,
    angleDeg: Float,
    color: Color,
) {
    val rad = angleDeg * (PI / 180.0).toFloat()
    val dx = cos(rad)
    val dy = sin(rad)
    val nx = -dy
    val ny = dx

    val tip = Offset(base.x + dx * length, base.y + dy * length)
    val leftC1 = Offset(base.x + dx * length * 0.32f + nx * halfWidth, base.y + dy * length * 0.32f + ny * halfWidth)
    val leftC2 = Offset(base.x + dx * length * 0.72f + nx * halfWidth * 0.62f, base.y + dy * length * 0.72f + ny * halfWidth * 0.62f)
    val rightC1 = Offset(base.x + dx * length * 0.72f - nx * halfWidth * 0.62f, base.y + dy * length * 0.72f - ny * halfWidth * 0.62f)
    val rightC2 = Offset(base.x + dx * length * 0.32f - nx * halfWidth, base.y + dy * length * 0.32f - ny * halfWidth)

    val leaf = Path().apply {
        moveTo(base.x, base.y)
        cubicTo(leftC1.x, leftC1.y, leftC2.x, leftC2.y, tip.x, tip.y)
        cubicTo(rightC1.x, rightC1.y, rightC2.x, rightC2.y, base.x, base.y)
        close()
    }
    drawPath(path = leaf, color = color)
}

private fun DrawScope.drawCanopyCrown(
    w: Float,
    h: Float,
    backColor: Color,
    frontColor: Color,
    highlightColor: Color,
) {
    drawCircle(color = backColor, radius = w * 0.22f, center = Offset(w * 0.31f, h * 0.42f))
    drawCircle(color = backColor, radius = w * 0.22f, center = Offset(w * 0.69f, h * 0.42f))
    drawCircle(color = frontColor, radius = w * 0.255f, center = Offset(w * 0.50f, h * 0.30f))
    drawCircle(color = frontColor, radius = w * 0.195f, center = Offset(w * 0.33f, h * 0.43f))
    drawCircle(color = frontColor, radius = w * 0.195f, center = Offset(w * 0.67f, h * 0.43f))
    drawCircle(color = highlightColor.copy(alpha = 0.55f), radius = w * 0.11f, center = Offset(w * 0.44f, h * 0.24f))
}

private fun DrawScope.drawTaperedTrunk(
    w: Float,
    h: Float,
    groundY: Float,
    trunkColor: Color,
) {
    val trunk = Path().apply {
        moveTo(w * 0.415f, groundY)
        quadraticTo(w * 0.455f, h * 0.66f, w * 0.458f, h * 0.49f)
        lineTo(w * 0.542f, h * 0.49f)
        quadraticTo(w * 0.545f, h * 0.66f, w * 0.585f, groundY)
        close()
    }
    drawPath(path = trunk, color = trunkColor)
    drawLine(
        color = trunkColor,
        start = Offset(w * 0.48f, h * 0.57f),
        end = Offset(w * 0.36f, h * 0.46f),
        strokeWidth = (w * 0.06f).coerceAtLeast(1.4f),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = trunkColor,
        start = Offset(w * 0.52f, h * 0.54f),
        end = Offset(w * 0.64f, h * 0.44f),
        strokeWidth = (w * 0.06f).coerceAtLeast(1.4f),
        cap = StrokeCap.Round,
    )
}

@Stable
class DetailSheetDrag internal constructor(private val bind: (String) -> Modifier) {
    fun headerModifier(pageKey: String): Modifier = bind(pageKey)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AssetDetailSheetHost(
    activeKey: String?,
    onDismissRequest: (key: String) -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (shownKey: String, drag: DetailSheetDrag) -> Unit,
) {
    var retainedKey by remember { mutableStateOf<String?>(null) }
    SideEffect { if (activeKey != null) retainedKey = activeKey }
    val shownKey = activeKey ?: retainedKey ?: return
    val visible = activeKey != null
    val currentActive by rememberUpdatedState(activeKey)
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    val palette = LocalChanPalette.current
    val scrimColor = MaterialTheme.colorScheme.scrim
    val focusManager = LocalFocusManager.current

    BoxWithConstraints(modifier.fillMaxSize().testTag("detail-sheet-host")) {
        val density = LocalDensity.current
        val topInsetPx = WindowInsets.safeDrawing.getTop(density).toFloat()
        val containerPx = constraints.maxHeight.toFloat()
        val sheetTopPx = min(topInsetPx + with(density) { SheetTopPeek.toPx() }, containerPx)
        val sheetHeightPx = (containerPx - sheetTopPx).coerceAtLeast(1f)
        val dismissDistancePx = min(sheetHeightPx * 0.25f, with(density) { SheetDismissDistance.toPx() })
        val flingMinDistancePx = with(density) { SheetFlingMinDistance.toPx() }
        val flingVelocityPx = with(density) { SheetFlingVelocity.toPx() }

        val motion = remember { Animatable(sheetHeightPx) }
        var drag by remember { mutableFloatStateOf(0f) }
        val scope = rememberCoroutineScope()
        val settleJob = remember { arrayOfNulls<Job>(1) }

        LaunchedEffect(visible, sheetHeightPx) {
            if (visible) {
                focusManager.clearFocus(force = true)
                motion.animateTo(0f, tween(durationMillis = 300, easing = FastOutSlowInEasing))
            } else {
                settleJob[0]?.cancel()
                motion.snapTo((motion.value + drag).coerceAtMost(sheetHeightPx))
                drag = 0f
                motion.animateTo(sheetHeightPx, tween(durationMillis = 240, easing = FastOutSlowInEasing))
                retainedKey = null
            }
        }

        val draggableState = rememberDraggableState { delta -> drag = (drag + delta).coerceAtLeast(0f) }
        val dragInteractions = remember { LastInteractionRecorder() }
        fun settle(pageKey: String, velocity: Float) {
            settleJob[0]?.cancel()
            val cancelled = dragInteractions.last is DragInteraction.Cancel
            settleJob[0] = scope.launch {
                val farEnough = drag >= dismissDistancePx ||
                    (velocity >= flingVelocityPx && drag >= flingMinDistancePx)
                if (!cancelled && farEnough && currentActive == pageKey && currentDismiss(pageKey)) return@launch
                animate(drag, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> drag = value }
            }
        }
        val currentSettle = rememberUpdatedState<(String, Float) -> Unit> { key, velocity -> settle(key, velocity) }
        val dragBinding = remember(draggableState) {
            DetailSheetDrag { pageKey ->
                Modifier.draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    interactionSource = dragInteractions,
                    onDragStarted = { settleJob[0]?.cancel() },
                    onDragStopped = { velocity -> currentSettle.value(pageKey, velocity) },
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .testTag("detail-sheet-scrim")
                .drawBehind {
                    val progress = 1f - ((motion.value + drag) / sheetHeightPx).coerceIn(0f, 1f)
                    drawRect(scrimColor.copy(alpha = SheetScrimAlpha * progress))
                }
                .pointerInput(shownKey) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var moved = false
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                change.consume()
                            }
                            released = event.changes.none { it.pressed }
                        }
                        if (!moved && currentActive == shownKey) currentDismiss(shownKey)
                    }
                }
                .clearAndSetSemantics { },
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { sheetHeightPx.toDp() })
                .offset { IntOffset(0, (sheetTopPx + motion.value + drag).roundToInt()) }
                .focusProperties { canFocus = visible }
                .focusGroup()
                .semantics { paneTitle = "标的详情" }
                .testTag("detail-sheet-surface"),
            shape = ClarityRadius.sheet,
            color = palette.backgroundCanvas,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(dragBinding.headerModifier(shownKey))
                        .padding(top = 10.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(ClarityRadius.pill)
                            .background(palette.cardBorder),
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    content(shownKey, dragBinding)
                }
            }
        }
    }
}

private class LastInteractionRecorder : MutableInteractionSource {
    private val delegate = MutableInteractionSource()
    var last: Interaction? = null
        private set

    override val interactions: Flow<Interaction> = delegate.interactions

    override suspend fun emit(interaction: Interaction) {
        last = interaction
        delegate.emit(interaction)
    }

    override fun tryEmit(interaction: Interaction): Boolean {
        last = interaction
        return delegate.tryEmit(interaction)
    }
}
