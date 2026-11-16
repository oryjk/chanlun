package cn.oryjk.changuan.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.oryjk.changuan.presentation.LocalChanPalette

object ClarityRadius {
    val card = RoundedCornerShape(16.dp)
    val field = RoundedCornerShape(14.dp)
    val tile = RoundedCornerShape(12.dp)
    val iconBox = RoundedCornerShape(10.dp)
    val pill = RoundedCornerShape(999.dp)
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
}

@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    titleSuffix: (@Composable RowScope.() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val palette = LocalChanPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (eyebrow != null) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (titleSuffix != null) {
                    titleSuffix()
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = palette.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            action()
        }
    }
}

@Composable
fun HeaderIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val palette = LocalChanPalette.current
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(ClarityRadius.tile)
            .background(palette.subtleSurface)
            .clickable(
                enabled = enabled && !loading,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = palette.primaryBrand,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = palette.inkPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun FixedHeaderPage(
    modifier: Modifier = Modifier,
    applyStatusBarPadding: Boolean = true,
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalChanPalette.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.backgroundCanvas),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (applyStatusBarPadding) {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    } else {
                        Modifier
                    },
                )
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        ) {
            header()
        }
        HorizontalDivider(color = palette.cardBorder, thickness = 0.5.dp)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableContent(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showIndicator: Boolean = isRefreshing,
    actionLabel: String = "刷新页面",
    content: @Composable BoxScope.() -> Unit,
) {
    if (!enabled) {
        Box(modifier.clipToBounds(), content = content)
        return
    }
    val palette = LocalChanPalette.current
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { if (!isRefreshing) onRefresh() },
        state = state,
        modifier = modifier
            .clipToBounds()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(actionLabel) {
                        if (isRefreshing) false else {
                            onRefresh()
                            true
                        }
                    },
                )
            },
        indicator = {
            PullToRefreshDefaults.Indicator(
                isRefreshing = showIndicator,
                state = state,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = palette.cardSurface,
                color = palette.inkPrimary,
            )
        },
        content = content,
    )
}

@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChanPalette.current
    Surface(
        color = if (selected) palette.inkPrimary else palette.cardSurface,
        shape = ClarityRadius.pill,
        border = if (selected) null else BorderStroke(1.dp, palette.cardBorder),
        modifier = modifier
            .clip(ClarityRadius.pill)
            .clickable(role = Role.Tab, onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) palette.backgroundCanvas else palette.inkSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
fun ActionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    warm: Boolean = false,
    outlined: Boolean = false,
    enabled: Boolean = true,
    large: Boolean = false,
    horizontalPadding: Dp = if (large) 16.dp else 12.dp,
    onClickLabel: String? = null,
    stateDesc: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val palette = LocalChanPalette.current
    val containerColor = when {
        warm -> palette.warmAmberSurface
        outlined -> palette.cardSurface
        active -> palette.actionPrimarySoft
        else -> palette.actionPrimary
    }
    val contentColor = when {
        warm -> palette.warmAmberText
        outlined -> palette.inkPrimary
        active -> palette.onActionPrimarySoft
        else -> palette.onActionPrimary
    }
    val border = when {
        warm -> BorderStroke(1.dp, palette.warmAmberBorder)
        outlined -> BorderStroke(1.dp, palette.cardBorder)
        else -> null
    }
    Surface(
        color = containerColor,
        shape = ClarityRadius.pill,
        border = border,
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.55f }
            .clip(ClarityRadius.pill)
            .clickable(enabled = enabled, onClickLabel = onClickLabel, role = Role.Button) { onClick() }
            .then(
                if (stateDesc != null) Modifier.semantics { stateDescription = stateDesc } else Modifier,
            ),
    ) {
        AdaptivePillContent(
            text = text,
            style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            color = contentColor,
            horizontalPadding = horizontalPadding,
            verticalPadding = if (large) 8.dp else 6.dp,
            adaptiveIcon = !large,
            leadingIcon = leadingIcon,
        )
    }
}

@Composable
internal fun AdaptivePillContent(
    text: String,
    style: TextStyle,
    color: Color,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    iconGap: Dp = 4.dp,
    adaptiveIcon: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Layout(
        content = {
            if (leadingIcon != null) leadingIcon()
            Text(
                text = text,
                style = style,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        },
    ) { measurables, constraints ->
        val hPadPx = horizontalPadding.roundToPx()
        val vPadPx = verticalPadding.roundToPx()
        val gapPx = iconGap.roundToPx()
        val hasIcon = leadingIcon != null && measurables.size == 2
        val textPlaceable = measurables.last().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val iconPlaceable = if (hasIcon) measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0)) else null

        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val showIcon = iconPlaceable != null &&
            (!adaptiveIcon || iconPlaceable.width + gapPx + textPlaceable.width + hPadPx * 2 <= maxW)

        val contentWidth = if (showIcon) {
            iconPlaceable!!.width + gapPx + textPlaceable.width
        } else {
            textPlaceable.width
        }
        val contentHeight = if (showIcon) {
            maxOf(iconPlaceable!!.height, textPlaceable.height)
        } else {
            textPlaceable.height
        }
        val width = maxOf(constraints.minWidth, minOf(maxW, contentWidth + hPadPx * 2))
        val height = minOf(constraints.maxHeight, maxOf(constraints.minHeight, contentHeight + vPadPx * 2))

        layout(width, height) {
            val startX = ((width - contentWidth) / 2).coerceAtLeast(0)
            if (showIcon) {
                val iconY = (height - iconPlaceable!!.height) / 2
                iconPlaceable.placeRelative(startX, iconY)
                val textY = (height - textPlaceable.height) / 2
                textPlaceable.placeRelative(startX + iconPlaceable.width + gapPx, textY)
            } else {
                val textY = (height - textPlaceable.height) / 2
                textPlaceable.placeRelative(startX, textY)
            }
        }
    }
}

@Composable
fun ClarityCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalChanPalette.current
    Surface(
        color = palette.cardSurface,
        shape = ClarityRadius.card,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .clip(ClarityRadius.card)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val palette = LocalChanPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.inkPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke(this)
    }
}

@Composable
fun StarkBadgeTag(
    text: String,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
) {
    val palette = LocalChanPalette.current
    Surface(
        color = if (inverted) palette.inkPrimary else palette.subtleSurface,
        shape = ClarityRadius.pill,
        border = if (inverted) null else BorderStroke(0.5.dp, palette.cardBorder),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (inverted) palette.backgroundCanvas else palette.inkSecondary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun InlineMetric(
    label: String,
    value: Double?,
    formatted: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChanPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.inkMuted,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = formatted,
            style = MaterialTheme.typography.labelMedium.tabular(),
            fontWeight = FontWeight.SemiBold,
            color = signedColor(value),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun StarkUnderlineTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    optionTag: ((Int) -> String?)? = null,
) {
    if (options.isEmpty()) return
    val palette = LocalChanPalette.current
    val haptics = LocalHapticFeedback.current
    val safeIndex = selectedIndex.coerceIn(0, options.lastIndex)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = index == safeIndex
                val tag = optionTag?.invoke(index)
                Column(
                    modifier = Modifier
                        .weight(1f)
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
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall.tabular(),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) palette.inkPrimary else palette.inkMuted,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(3.dp)
                            .clip(ClarityRadius.pill)
                            .background(if (isSelected) palette.inkPrimary else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.5.dp,
            color = palette.cardBorder,
        )
    }
}

@Composable
fun SortableColumnHeader(
    label: String,
    active: Boolean,
    descending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChanPalette.current
    val icon = when {
        !active -> Icons.Outlined.UnfoldMore
        descending -> Icons.Outlined.ExpandMore
        else -> Icons.Outlined.ExpandLess
    }
    val sortStateText = when {
        !active -> "未排序"
        descending -> "降序"
        else -> "升序"
    }
    val tint = if (active) palette.actionPrimary else palette.inkMuted
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = sortStateText }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(modifier = Modifier.width(3.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
    }
}
