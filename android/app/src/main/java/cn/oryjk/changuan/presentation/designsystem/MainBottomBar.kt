package cn.oryjk.changuan.presentation.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.oryjk.changuan.domain.RootTab
import cn.oryjk.changuan.presentation.ChanPalette
import cn.oryjk.changuan.presentation.LocalChanPalette

private val MainBottomBarHorizontalInset = 12.dp
private val IndicatorWidth = 52.dp
private val IndicatorHeight = 28.dp
private val IconGlyphSize = 21.dp

private fun RootTab.icons(): Pair<ImageVector, ImageVector> = when (this) {
    RootTab.MARKET -> Icons.Outlined.CandlestickChart to Icons.Filled.CandlestickChart
    RootTab.RANKING -> Icons.Outlined.Leaderboard to Icons.Filled.Leaderboard
    RootTab.ACCOUNT -> Icons.Outlined.Menu to Icons.Filled.Menu
}

@Composable
fun MainBottomBar(
    currentTab: RootTab,
    onSelect: (RootTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChanPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.cardSurface)
            .testTag("main-bottom-bar"),
    ) {
        HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(horizontal = MainBottomBarHorizontalInset)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RootTab.entries.forEach { tab ->
                MainBottomBarItem(
                    tab = tab,
                    selected = tab == currentTab,
                    palette = palette,
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.MainBottomBarItem(
    tab: RootTab,
    selected: Boolean,
    palette: ChanPalette,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val indicatorProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "nav-indicator-${tab.rawValue}",
    )
    val (idleIcon, activeIcon) = tab.icons()
    val iconColor = if (selected) palette.cardSurface else palette.inkSecondary
    val labelColor = if (selected) palette.inkPrimary else palette.inkSecondary

    Column(
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 56.dp)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(top = 6.dp, bottom = 5.dp)
            .testTag("main-nav-${tab.rawValue}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = IndicatorWidth, height = IndicatorHeight)
                .clip(ClarityRadius.pill)
                .indication(interactionSource, ripple(bounded = true)),
            contentAlignment = Alignment.Center,
        ) {
            if (indicatorProgress > 0.01f) {
                Box(
                    modifier = Modifier
                        .size(width = IndicatorWidth, height = IndicatorHeight)
                        .graphicsLayer {
                            scaleX = 0.65f + 0.35f * indicatorProgress
                            alpha = indicatorProgress
                        }
                        .clip(ClarityRadius.pill)
                        .background(palette.inkPrimary),
                )
            }
            Icon(
                imageVector = if (selected) activeIcon else idleIcon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(IconGlyphSize),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun MainSideRail(
    currentTab: RootTab,
    onSelect: (RootTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChanPalette.current
    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(84.dp)
                .fillMaxHeight()
                .background(palette.cardSurface)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Left))
                .padding(vertical = 12.dp, horizontal = 8.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RootTab.entries.forEach { tab ->
                val selected = tab == currentTab
                val (idleIcon, activeIcon) = tab.icons()
                val iconColor = if (selected) palette.cardSurface else palette.inkSecondary
                val labelColor = if (selected) palette.inkPrimary else palette.inkSecondary
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ClarityRadius.tile)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(tab) },
                        )
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 28.dp)
                            .clip(ClarityRadius.pill)
                            .background(if (selected) palette.inkPrimary else palette.cardSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (selected) activeIcon else idleIcon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = labelColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(palette.cardBorder),
        )
    }
}
