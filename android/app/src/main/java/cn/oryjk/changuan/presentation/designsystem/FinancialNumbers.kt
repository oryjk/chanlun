package cn.oryjk.changuan.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.oryjk.changuan.R
import cn.oryjk.changuan.presentation.LocalChanPalette

const val TabularNums = "tnum"

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val FinancialNumberFont = FontFamily(
    Font(
        R.font.roboto_condensed,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.roboto_condensed,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.roboto_condensed,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.roboto_condensed,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

fun TextStyle.tabular(): TextStyle = copy(
    fontFamily = FinancialNumberFont,
    fontFeatureSettings = TabularNums,
    fontStyle = FontStyle.Normal,
    letterSpacing = 0.sp,
)

@Composable
fun signedColor(value: Double?): Color {
    val palette = LocalChanPalette.current
    return when {
        value == null || !value.isFinite() -> palette.inkMuted
        value > 0 -> palette.positiveRed
        value < 0 -> palette.negativeGreen
        else -> palette.inkSecondary
    }
}

data class SummaryItem(
    val label: String,
    val value: String,
    val signedValue: Double? = null,
    val a11yDescription: String? = null,
)

@Composable
fun FinancialNumber(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = LocalChanPalette.current.inkPrimary,
    softWrap: Boolean = false,
    textAlign: TextAlign? = null,
    alignEnd: Boolean = true,
) {
    Text(
        text = value,
        modifier = if (alignEnd) modifier.wrapContentWidth(Alignment.End) else modifier,
        style = style.tabular().copy(lineHeight = style.fontSize * 1.2f),
        color = color,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Normal,
        maxLines = if (softWrap) Int.MAX_VALUE else 1,
        softWrap = softWrap,
        textAlign = textAlign,
    )
}

@Composable
fun FinancialMetric(
    item: SummaryItem,
    modifier: Modifier = Modifier,
    alignStart: Boolean = false,
) {
    val palette = LocalChanPalette.current
    Column(
        modifier = modifier.then(
            if (item.a11yDescription != null) {
                Modifier.semantics { contentDescription = item.a11yDescription }
            } else {
                Modifier
            },
        ),
        horizontalAlignment = if (alignStart) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FinancialNumber(
            value = item.value,
            style = MaterialTheme.typography.titleSmall,
            alignEnd = !alignStart,
            color = when {
                item.signedValue != null -> signedColor(item.signedValue)
                item.value == "—" || item.value == "--" -> signedColor(null)
                else -> palette.inkPrimary
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FinancialMetricGroup(
    items: List<SummaryItem>,
    modifier: Modifier = Modifier,
    alignStart: Boolean = false,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { FinancialMetric(item = it, alignStart = alignStart) }
    }
}

@Composable
fun CenteredFinancialMetricGrid(
    items: List<SummaryItem>,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    val palette = LocalChanPalette.current
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            items.forEach { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(IntrinsicSize.Max),
                    )
                    FinancialNumber(
                        value = item.value,
                        modifier = Modifier.width(IntrinsicSize.Max),
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            item.signedValue != null -> signedColor(item.signedValue)
                            item.value == "—" || item.value == "--" -> signedColor(null)
                            else -> palette.inkPrimary
                        },
                        softWrap = true,
                        textAlign = TextAlign.Center,
                        alignEnd = false,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        val rowGap = 10.dp.roundToPx()
        val width = constraints.maxWidth
        val preferredWidth = measurables.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        val fittingColumns = ((width + gap) / (preferredWidth + gap).coerceAtLeast(1)).coerceAtLeast(1)
        val columnCount = minOf(columns.coerceAtLeast(1), fittingColumns)
        val cellWidth = ((width - gap * (columnCount - 1)) / columnCount).coerceAtLeast(0)
        val cells = measurables.map { it.measure(Constraints(maxWidth = cellWidth)) }
        val rows = cells.chunked(columnCount)
        val rowHeights = rows.map { row -> row.maxOf { it.height } }
        val height = rowHeights.sum() + rowGap * (rows.size - 1).coerceAtLeast(0)
        layout(width, constraints.constrainHeight(height)) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { columnIndex, cell ->
                    val x = columnIndex * (cellWidth + gap) + (cellWidth - cell.width) / 2
                    cell.placeRelative(x, y)
                }
                y += rowHeights[rowIndex] + rowGap
            }
        }
    }
}

@Composable
fun FinancialValueRow(item: SummaryItem, modifier: Modifier = Modifier) {
    val palette = LocalChanPalette.current
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Text(item.label, style = MaterialTheme.typography.labelSmall, color = palette.inkMuted)
            FinancialNumber(
                value = item.value,
                modifier = Modifier.width(IntrinsicSize.Max),
                style = MaterialTheme.typography.titleSmall,
                color = item.signedValue?.let { signedColor(it) } ?: palette.inkPrimary,
                softWrap = true,
                textAlign = TextAlign.End,
                alignEnd = false,
            )
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = 12.dp.roundToPx()
        val stackedGap = 4.dp.roundToPx()
        val label = measurables[0].measure(Constraints(maxWidth = width))
        val value = measurables[1].measure(Constraints(maxWidth = width))
        val stacked = label.width + gap + value.width > width
        val height = if (stacked) label.height + stackedGap + value.height else maxOf(label.height, value.height)
        layout(width, constraints.constrainHeight(height)) {
            label.placeRelative(0, if (stacked) 0 else (height - label.height) / 2)
            value.placeRelative(width - value.width, if (stacked) label.height + stackedGap else (height - value.height) / 2)
        }
    }
}

@Composable
fun SummaryStrip(
    items: List<SummaryItem>,
    modifier: Modifier = Modifier,
    centeredColumns: Int? = null,
) {
    val palette = LocalChanPalette.current
    Surface(
        color = palette.cardSurface,
        shape = ClarityRadius.card,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (centeredColumns == null) {
            FinancialMetricGroup(
                items = items,
                alignStart = true,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        } else {
            CenteredFinancialMetricGrid(
                items = items,
                columns = centeredColumns,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}
