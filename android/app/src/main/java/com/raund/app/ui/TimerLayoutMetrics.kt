package com.raund.app.ui

internal object TimerLayoutMetrics {
    const val controlsOuterPaddingDp = 16f
    const val controlsBottomSpacerDp = 8f
    const val primaryButtonHeightDp = 64f
    const val secondaryButtonHeightDp = 52f
    const val pausedButtonsSpacingDp = 12f
    const val topBarHeightDp = 80f
    const val topBarTitleHeightDp = 56f
    const val contentHorizontalPaddingDp = 16f
    const val roundTitleHeightDp = 96f
    const val roundMetaSpacingDp = 4f
    const val roundMetaHeightDp = 44f
    const val roundHeaderHeightDp = roundTitleHeightDp + roundMetaSpacingDp + roundMetaHeightDp
    const val ringTopSpacingDp = 8f
    const val roundHeaderVisualOffsetDp = -22f
    // Text/emoji glyph bounds look visually low when mathematically centered.
    const val ringContentVisualOffsetDp = -10f
    /** Extra nudge up for emoji so it appears centered in the ring. */
    const val ringEmojiNudgeUpDp = -4f
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
