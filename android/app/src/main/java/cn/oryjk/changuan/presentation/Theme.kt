package cn.oryjk.changuan.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

@Immutable
data class ChanPalette(
    val isDark: Boolean,
    val backgroundCanvas: Color,
    val cardSurface: Color,
    val subtleSurface: Color,
    val cardBorder: Color,
    val primaryBrand: Color,
    val primaryBrandSoft: Color,
    val warmAmber: Color,
    val warmAmberSurface: Color,
    val warmAmberBorder: Color,
    val warmAmberText: Color,
    val positiveRed: Color,
    val positiveRedSurface: Color,
    val negativeGreen: Color,
    val negativeGreenSurface: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val actionPrimary: Color,
    val onActionPrimary: Color,
    val actionPrimarySoft: Color,
    val onActionPrimarySoft: Color,
    val penGold: Color,
    val segmentBlue: Color,
    val zhongshuViolet: Color,
    val fractalSlate: Color,
) {
    fun directionColor(value: Double): Color = when {
        value > 0.0 -> positiveRed
        value < 0.0 -> negativeGreen
        else -> inkSecondary
    }
}

val LightChanPalette = ChanPalette(
    isDark = false,
    backgroundCanvas = Color(0xFFFFFFFF),
    cardSurface = Color(0xFFFFFFFF),
    subtleSurface = Color(0xFFF5F5F5),
    cardBorder = Color(0xFFEAEAEA),
    primaryBrand = Color(0xFF2F5E8C),
    primaryBrandSoft = Color(0xFFE8EFF6),
    warmAmber = Color(0xFFF4A261),
    warmAmberSurface = Color(0xFFFFF6EC),
    warmAmberBorder = Color(0xFFF6DEC2),
    warmAmberText = Color(0xFF8A4F1D),
    positiveRed = Color(0xFFD94844),
    positiveRedSurface = Color(0xFFFCECEC),
    negativeGreen = Color(0xFF168A6A),
    negativeGreenSurface = Color(0xFFEAF7F2),
    inkPrimary = Color(0xFF1B2128),
    inkSecondary = Color(0xFF666666),
    inkMuted = Color(0xFF999999),
    actionPrimary = Color(0xFF1B2128),
    onActionPrimary = Color(0xFFFFFFFF),
    actionPrimarySoft = Color(0xFFF5F5F5),
    onActionPrimarySoft = Color(0xFF1B2128),
    penGold = Color(0xFFC58B2A),
    segmentBlue = Color(0xFF2F5E8C),
    zhongshuViolet = Color(0xFF7D68A8),
    fractalSlate = Color(0xFF6E8CA3),
)

val DarkChanPalette = ChanPalette(
    isDark = true,
    backgroundCanvas = Color(0xFF000000),
    cardSurface = Color(0xFF000000),
    subtleSurface = Color(0xFF16181C),
    cardBorder = Color(0xFF2F3336),
    primaryBrand = Color(0xFF6FA3D4),
    primaryBrandSoft = Color(0xFF162432),
    warmAmber = Color(0xFFF4A261),
    warmAmberSurface = Color(0xFF241C14),
    warmAmberBorder = Color(0xFF433222),
    warmAmberText = Color(0xFFF7C597),
    positiveRed = Color(0xFFF06A66),
    positiveRedSurface = Color(0xFF2B1617),
    negativeGreen = Color(0xFF38B892),
    negativeGreenSurface = Color(0xFF112922),
    inkPrimary = Color(0xFFE7E9EA),
    inkSecondary = Color(0xFFA8AEB5),
    inkMuted = Color(0xFF71767B),
    actionPrimary = Color(0xFFE7E9EA),
    onActionPrimary = Color(0xFF000000),
    actionPrimarySoft = Color(0xFF16181C),
    onActionPrimarySoft = Color(0xFFE7E9EA),
    penGold = Color(0xFFE8B44C),
    segmentBlue = Color(0xFF6FA3D4),
    zhongshuViolet = Color(0xFFA792D6),
    fractalSlate = Color(0xFF8CAAB9),
)

val LocalChanPalette = staticCompositionLocalOf { LightChanPalette }

private val ChanShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val ChanTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

@Composable
fun ChanGuanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkChanPalette else LightChanPalette
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.actionPrimary,
            onPrimary = palette.onActionPrimary,
            secondary = palette.warmAmber,
            background = palette.backgroundCanvas,
            surface = palette.cardSurface,
            onBackground = palette.inkPrimary,
            onSurface = palette.inkPrimary,
            outline = palette.cardBorder,
            error = palette.positiveRed,
        )
    } else {
        lightColorScheme(
            primary = palette.actionPrimary,
            onPrimary = palette.onActionPrimary,
            secondary = palette.warmAmber,
            background = palette.backgroundCanvas,
            surface = palette.cardSurface,
            onBackground = palette.inkPrimary,
            onSurface = palette.inkPrimary,
            outline = palette.cardBorder,
            error = palette.positiveRed,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            window.decorView.setBackgroundColor(palette.backgroundCanvas.toArgb())
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalChanPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ChanTypography,
            shapes = ChanShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
