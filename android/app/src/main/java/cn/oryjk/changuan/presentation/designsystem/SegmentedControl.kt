package cn.oryjk.changuan.presentation.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cn.oryjk.changuan.presentation.LocalChanPalette
import kotlin.math.roundToInt

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    optionTag: ((Int) -> String?)? = null,
    compact: Boolean = false,
) {
    if (options.isEmpty()) return
    val palette = LocalChanPalette.current
    val haptics = LocalHapticFeedback.current
    val safeIndex = selectedIndex.coerceIn(0, options.lastIndex)
    val animatedIndex by animateFloatAsState(
        targetValue = safeIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "segmented-control-thumb",
    )
    val outerPadDp = if (compact) 2.dp else 4.dp
    val optionMinHeightDp = if (compact) 26.dp else 36.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ClarityRadius.pill)
            .background(palette.subtleSurface)
            .padding(outerPadDp),
    ) {
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                Box(
                    modifier = Modifier
                        .clip(ClarityRadius.pill)
                        .background(palette.inkPrimary),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    options.forEachIndexed { index, label ->
                        val isSelected = index == safeIndex
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) palette.backgroundCanvas else palette.inkSecondary,
                            animationSpec = tween(150),
                            label = "segmented-control-text-$index",
                        )
                        val tag = optionTag?.invoke(index)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .defaultMinSize(minHeight = optionMinHeightDp)
                                .clip(ClarityRadius.pill)
                                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.Tab,
                                    onClick = {
                                        if (!isSelected) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSelect(index)
                                        }
                                    },
                                )
                                .padding(horizontal = 2.dp, vertical = if (compact) 4.dp else 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = textColor,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val count = options.size.coerceAtLeast(1)
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            val minInnerHeight = optionMinHeightDp.roundToPx()
            val rowMinHeight = maxOf(
                minInnerHeight,
                (constraints.minHeight - (outerPadDp * 2).roundToPx()).coerceAtLeast(0),
            )
            val rowPlaceable = measurables[1].measure(
                Constraints(
                    minWidth = width,
                    maxWidth = if (constraints.hasBoundedWidth) width else Constraints.Infinity,
                    minHeight = rowMinHeight,
                    maxHeight = constraints.maxHeight,
                ),
            )
            val actualWidth = rowPlaceable.width
            val actualHeight = rowPlaceable.height
            val segmentWidth = actualWidth.toFloat() / count.toFloat()
            val thumbWidth = segmentWidth.roundToInt().coerceAtLeast(0)
            val thumbPlaceable = measurables[0].measure(
                Constraints.fixed(thumbWidth, actualHeight),
            )
            val thumbX = (animatedIndex * segmentWidth).roundToInt()
                .coerceIn(0, (actualWidth - thumbWidth).coerceAtLeast(0))
            layout(actualWidth, actualHeight) {
                thumbPlaceable.placeRelative(thumbX, 0)
                rowPlaceable.placeRelative(0, 0)
            }
        }
    }
}
