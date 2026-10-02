package dev.agneswd.stillpoint.focus

import android.os.SystemClock
import dev.agneswd.stillpoint.data.ActiveFocus

/** Counts only monotonic time in this phase. Pauses and civil clock changes add no time. */
fun ActiveFocus.elapsedPhaseMillis(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
    val duration = (phaseEndsAt - phaseStartedAt).coerceAtLeast(0)
    val saved = phaseElapsedMillis.coerceIn(0, duration)
    val delta = if (running && phaseAnchorElapsed >= 0) (nowElapsed - phaseAnchorElapsed).coerceAtLeast(0) else 0
    return saved + delta.coerceAtMost(duration - saved)
}

fun ActiveFocus.remainingMillis(nowElapsed: Long = SystemClock.elapsedRealtime()): Long =
    ((phaseEndsAt - phaseStartedAt).coerceAtLeast(0) - elapsedPhaseMillis(nowElapsed)).coerceAtLeast(0)

/** A new boot preserves the checkpoint and resumes without guessing time spent powered off. */
internal fun ActiveFocus.rebaseClock(wall: Long, elapsed: Long, boot: Int): ActiveFocus {
    val duration = (phaseEndsAt - phaseStartedAt).coerceAtLeast(0)
    val spent = if (bootCount == boot && phaseAnchorElapsed >= 0 && elapsed >= phaseAnchorElapsed) {
        elapsedPhaseMillis(elapsed)
    } else phaseElapsedMillis.coerceIn(0, duration)
    return copy(
        phaseElapsedMillis = spent,
        phaseAnchorElapsed = elapsed,
        bootCount = boot,
        phaseStartedAt = wall - spent,
        phaseEndsAt = wall + (duration - spent),
        pausedAt = if (running) 0 else wall.coerceAtLeast(1),
    )
}
