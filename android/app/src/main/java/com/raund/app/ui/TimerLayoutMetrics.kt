package com.raund.app.ui

internal object TimerLayoutMetrics {
    const val controlsOuterPaddingDp = 24f
    const val controlsBottomSpacerDp = 16f
    const val primaryButtonHeightDp = 64f
    const val secondaryButtonHeightDp = 52f
    const val pausedButtonsSpacingDp = 12f
    const val topBarHeightDp = 72f
    const val roundTitleHeightDp = 88f
    const val roundMetaSpacingDp = 12f
    const val roundMetaHeightDp = 52f
    const val roundHeaderHeightDp = roundTitleHeightDp + roundMetaSpacingDp + roundMetaHeightDp
    const val ringTopSpacingDp = 32f
    const val maxRingSizeDp = 520f
    const val minRingAreaHeightDp = 120f

    fun fixedChromeHeightDp(): Float {
        return topBarHeightDp + roundHeaderHeightDp + ringTopSpacingDp
    }

    /** Height available for the ring so its size and position stay constant across rounds. */
    fun stableRingAreaHeightDp(contentHeightDp: Float): Float {
        return (contentHeightDp - fixedChromeHeightDp()).coerceAtLeast(minRingAreaHeightDp)
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
