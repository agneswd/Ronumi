package dev.agneswd.stillpoint.game

import dev.agneswd.stillpoint.data.StillpointDao
import dev.agneswd.stillpoint.data.updateSettings
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** The most streak freezes a user can hold. */
const val MAX_FREEZES = 2

/**
 * Uses a streak freeze for each day missed since the streak was last active,
 * and gives a new freeze at every 7 days of streak. Call it when the app opens.
 */
suspend fun applyStreakFreezes(dao: StillpointDao, today: LocalDate = LocalDate.now()) {
    val sessions = dao.sessions().first()
    val minutes = sessions.groupBy { day(it.startedAt) }.mapValues { (_, l) -> l.sumOf { it.focusedMillis } / 60_000 }
    dao.updateSettings { s ->
        var settings = s
        val frozen = s.frozenDays.toMutableSet()
        fun active(d: LocalDate) = (minutes[d] ?: 0) >= STREAK_MINUTES || d.toString() in frozen
        // Walk back over missed days until the last active day, at most a week.
        val missed = mutableListOf<LocalDate>()
        var d = today.minusDays(1)
        while (!active(d) && missed.size < 7) {
            missed += d
            d = d.minusDays(1)
        }
        val hadStreak = active(d)
        if (hadStreak && missed.isNotEmpty() && missed.size <= s.streakFreezes) {
            missed.forEach { frozen += it.toString() }
            settings = settings.copy(streakFreezes = s.streakFreezes - missed.size, frozenDays = frozen)
        }
        // Reward: one freeze for each full week of streak, up to the maximum.
        val streak = gameState(sessions, settings, today).streak
        val weeks = streak / 7
        if (weeks < settings.freezeWeeksRewarded) {
            // A broken streak starts counting weeks again.
            settings = settings.copy(freezeWeeksRewarded = weeks)
        } else if (weeks > settings.freezeWeeksRewarded) {
            settings = settings.copy(
                streakFreezes = (settings.streakFreezes + 1).coerceAtMost(MAX_FREEZES),
                freezeWeeksRewarded = weeks,
            )
        }
        settings
    }
}
