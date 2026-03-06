package com.raund.app

const val MIN_ROUND_DURATION_SECONDS = 5
const val MAX_ROUND_DURATION_SECONDS = 7200
const val MAX_ROUNDS_PER_PROFILE = 30

fun formatDuration(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
