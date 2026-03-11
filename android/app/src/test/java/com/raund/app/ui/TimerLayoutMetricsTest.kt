package com.raund.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerLayoutMetricsTest {

    @Test
    fun `timer screen always reserves the tallest controls slot`() {
        assertEquals(192f, TimerLayoutMetrics.controlsReservedHeightDp(), 0.001f)
    }

    @Test
    fun `ring size shrinks when height is the tightest constraint`() {
        assertEquals(
            200f,
            TimerLayoutMetrics.ringMaxSizeDp(viewportWidthDp = 360f, availableHeightDp = 200f),
            0.001f
        )
    }

    @Test
    fun `stable ring area height subtracts reserved top and name chip`() {
        assertEquals(204f, TimerLayoutMetrics.stableRingAreaHeightDp(400f), 0.001f)
        assertEquals(120f, TimerLayoutMetrics.stableRingAreaHeightDp(250f), 0.001f)
    }

    @Test
    fun `ring size still respects width and max cap`() {
        assertEquals(
            280f,
            TimerLayoutMetrics.ringMaxSizeDp(viewportWidthDp = 280f, availableHeightDp = 500f),
            0.001f
        )
        assertEquals(
            320f,
            TimerLayoutMetrics.ringMaxSizeDp(viewportWidthDp = 500f, availableHeightDp = 640f),
            0.001f
        )
    }
}
