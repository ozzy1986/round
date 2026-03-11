package com.raund.app.ui

internal object TimerLayoutMetrics {
    const val controlsOuterPaddingDp = 24f
    const val controlsBottomSpacerDp = 16f
    const val primaryButtonHeightDp = 64f
    const val secondaryButtonHeightDp = 52f
    const val pausedButtonsSpacingDp = 12f
    const val ringTopSpacingDp = 48f
    const val maxRingSizeDp = 320f
    /** Reserved for top bar (padding + icon + title). */
    const val topBarReservedHeightDp = 56f
    /** Reserved for round name (2 lines) + spacer + chip row so ring size does not depend on round text. */
    const val roundNameChipReservedHeightDp = 140f

    /** Height available for the ring so its size stays constant across rounds (no shrink on longer round names). */
    fun stableRingAreaHeightDp(contentHeightDp: Float): Float {
        return (contentHeightDp - topBarReservedHeightDp - roundNameChipReservedHeightDp).coerceAtLeast(120f)
    }

    fun controlsReservedHeightDp(): Float {
        return (controlsOuterPaddingDp * 2f) +
            primaryButtonHeightDp +
            pausedButtonsSpacingDp +
            secondaryButtonHeightDp +
            controlsBottomSpacerDp
    }

    fun ringMaxSizeDp(viewportWidthDp: Float, availableHeightDp: Float): Float {
        return minOf(viewportWidthDp, availableHeightDp, maxRingSizeDp)
    }
}
