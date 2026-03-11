package com.raund.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerLayoutMetricsTest {

    @Test
    fun `timer screen always reserves the tallest controls slot`() {
        assertEquals(192f, TimerLayoutMetrics.controlsReservedHeightDp(), 0.001f)
    }

    @Test
    fun `timer screen keeps fixed chrome height above the ring`() {
        assertEquals(256f, TimerLayoutMetrics.fixedChromeHeightDp(), 0.001f)
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
    fun `stable ring area height subtracts fixed page sections`() {
        assertEquals(144f, TimerLayoutMetrics.stableRingAreaHeightDp(400f), 0.001f)
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
            400f,
            TimerLayoutMetrics.ringMaxSizeDp(viewportWidthDp = 500f, availableHeightDp = 640f),
            0.001f
        )
    }
}
