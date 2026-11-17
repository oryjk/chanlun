package cn.oryjk.changuan

import cn.oryjk.changuan.domain.ChartViewport
import cn.oryjk.changuan.domain.PriceProjection
import cn.oryjk.changuan.domain.computeLabelPillBox
import cn.oryjk.changuan.domain.computePointBadgeBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartGeometryTest {
    @Test
    fun `viewport pan and zoom stay within candle bounds`() {
        val initial = ChartViewport.initial(count = 200, defaultBars = 90)
        assertEquals(110.0, initial.lower, 1e-6)
        assertEquals(200.0, initial.upper, 1e-6)

        val pannedRight = initial.panned(bars = 50.0, count = 200)
        assertEquals(200.0, pannedRight.upper, 1e-6)
        assertEquals(90.0, pannedRight.span, 1e-6)

        val pannedLeft = initial.panned(bars = -200.0, count = 200)
        assertEquals(0.0, pannedLeft.lower, 1e-6)
        assertEquals(90.0, pannedLeft.upper, 1e-6)

        val zoomedIn = initial.zoomed(scale = 2.0, anchor = 0.5, count = 200)
        assertEquals(45.0, zoomedIn.span, 1e-6)
        assertTrue(zoomedIn.lower >= 0.0 && zoomedIn.upper <= 200.0)
    }

    @Test
    fun `price projection converts price to y and back`() {
        val projection = PriceProjection(
            minimum = 100.0,
            maximum = 120.0,
            top = 10.0,
            height = 200.0,
        )
        val y = projection.y(110.0)
        val roundTrip = projection.priceAt(y)
        assertEquals(110.0, roundTrip, 1e-6)
    }

    @Test
    fun `badge boxes wrap full font metrics across 1x to 3_5x densities and stay inside bounds`() {
        for (density in listOf(1f, 2f, 3f, 3.5f)) {
            val textSize = 10f * density
            val ascent = -0.92f * textSize
            val descent = 0.24f * textSize
            val padX = 5f * density
            val padY = 2.5f * density
            val gap = 7f * density
            val tw = 16f * density
            val minX = 8f * density
            val maxX = 380f * density
            val minY = 8f * density
            val maxY = 210f * density

            val buyBox = computePointBadgeBox(
                anchorX = 60f * density,
                anchorY = 120f * density,
                isBelowAnchor = true,
                textWidth = tw,
                fontAscent = ascent,
                fontDescent = descent,
                padX = padX,
                padY = padY,
                anchorGap = gap,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
            )
            assertEquals(buyBox.top + padY, buyBox.textBaselineY + ascent, 1e-3f)
            assertEquals(buyBox.bottom - padY, buyBox.textBaselineY + descent, 1e-3f)
            assertTrue(buyBox.top >= 120f * density + gap - 1e-3f)
            assertTrue(buyBox.left >= minX && buyBox.right <= maxX)
            assertTrue(buyBox.top >= minY && buyBox.bottom <= maxY)

            val clampedSell = computePointBadgeBox(
                anchorX = 2f * density,
                anchorY = minY,
                isBelowAnchor = false,
                textWidth = tw,
                fontAscent = ascent,
                fontDescent = descent,
                padX = padX,
                padY = padY,
                anchorGap = gap,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
            )
            assertEquals(clampedSell.top + padY, clampedSell.textBaselineY + ascent, 1e-3f)
            assertEquals(clampedSell.bottom - padY, clampedSell.textBaselineY + descent, 1e-3f)
            assertTrue(clampedSell.left >= minX && clampedSell.right <= maxX)
            assertTrue(clampedSell.top >= minY && clampedSell.bottom <= maxY)

            val pill = computeLabelPillBox(
                anchorLeft = maxX,
                anchorTop = 40f * density,
                textWidth = tw,
                fontAscent = ascent,
                fontDescent = descent,
                padX = 4f * density,
                padY = 2f * density,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
            )
            assertEquals(pill.top + 2f * density, pill.textBaselineY + ascent, 1e-3f)
            assertEquals(pill.bottom - 2f * density, pill.textBaselineY + descent, 1e-3f)
            assertTrue(pill.left >= minX && pill.right <= maxX)
            assertTrue(pill.top >= minY && pill.bottom <= maxY)
        }
    }
}
