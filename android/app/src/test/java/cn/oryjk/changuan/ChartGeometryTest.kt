package cn.oryjk.changuan

import cn.oryjk.changuan.domain.ChartViewport
import cn.oryjk.changuan.domain.PriceProjection
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
}
