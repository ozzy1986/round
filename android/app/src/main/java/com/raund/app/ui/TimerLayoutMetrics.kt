package com.raund.app.ui

internal object TimerLayoutMetrics {
    const val controlsOuterPaddingDp = 24f
    const val controlsBottomSpacerDp = 16f
    const val primaryButtonHeightDp = 64f
    const val secondaryButtonHeightDp = 52f
    const val pausedButtonsSpacingDp = 12f
    const val ringTopSpacingDp = 48f
    const val refreshContentHeightRatio = 0.7f
    const val maxRingSizeDp = 320f

    fun controlsReservedHeightDp(activeWorkout: Boolean): Float {
        val singleButtonControlsHeight =
            (controlsOuterPaddingDp * 2f) +
                primaryButtonHeightDp +
                controlsBottomSpacerDp
        if (!activeWorkout) {
            return singleButtonControlsHeight
        }
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
