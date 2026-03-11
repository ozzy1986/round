package com.raund.app

const val MIN_ROUND_DURATION_SECONDS = 5
const val MAX_ROUND_DURATION_SECONDS = 7200
const val MAX_ROUNDS_PER_PROFILE = 30

/**
 * Parses a simple duration expression: digits, "+", "*", and spaces only.
 * Examples: "60*5" -> 300, "120+30" -> 150, "60*5+30" -> 330.
 * Returns null if the string is invalid or contains disallowed characters.
 */
fun parseDurationExpression(input: String): Int? {
    val s = input.trim().replace(" ", "")
    if (s.isEmpty()) return null
    for (c in s) {
        if (c !in '0'..'9' && c != '+' && c != '*') return null
    }
    val terms = s.split('+')
    var sum = 0
    for (term in terms) {
        val factors = term.split('*').map { it.toIntOrNull() ?: return null }
        if (factors.isEmpty()) return null
        var product = 1
        for (f in factors) {
            if (f < 0) return null
            product *= f
            if (product < 0) return null
        }
        sum += product
        if (sum < 0) return null
    }
    return sum
}

fun formatDuration(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
