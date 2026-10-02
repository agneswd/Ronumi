package dev.agneswd.stillpoint.game

import dev.agneswd.stillpoint.data.FocusSession
import dev.agneswd.stillpoint.data.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A gentle response to focus history. Session-specific poses take precedence in the UI. */
enum class PebbleFeeling { READY, QUIET, DOWN, HAPPY, PROUD, CELEBRATE }

data class PebbleDisposition(
    val feeling: PebbleFeeling = PebbleFeeling.READY,
    /** Consecutive missed study days within the last 28 calendar days. Today never counts as missed. */
    val missedDays: Int = 0,
)

/** Rest and frozen days do not reduce the mood. Even partial focus breaks an absence. */
fun pebbleDisposition(
    sessions: List<FocusSession>,
    settings: Settings,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): PebbleDisposition {
    val minutes = sessions.asSequence()
        .filter { it.focusedMillis > 0 }
        .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        .filterKeys { it <= today }
        .mapValues { (_, rows) -> rows.sumOf { it.focusedMillis } / 60_000 }
    val firstDay = minutes.keys.minOrNull() ?: return PebbleDisposition()
    val todayMinutes = minutes[today] ?: 0L
    val frozen = settings.frozenDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
    fun planned(date: LocalDate) = settings.goalDays and (1 shl (date.dayOfWeek.value - 1)) != 0
    val recent = (1L..28L).map { today.minusDays(it) }
        .filter { it >= firstDay && ((minutes[it] ?: 0L) > 0L || (planned(it) && it !in frozen)) }
    val missed = recent.takeWhile { (minutes[it] ?: 0L) == 0L }.size
    val consistent = recent.takeWhile { (minutes[it] ?: 0L) >= STREAK_MINUTES }.size +
        if (todayMinutes >= STREAK_MINUTES) 1 else 0

    val feeling = when {
        todayMinutes >= settings.focusGoalMinutes.coerceAtLeast(1) -> PebbleFeeling.CELEBRATE
        todayMinutes > 0L -> if (consistent >= 3) PebbleFeeling.PROUD else PebbleFeeling.HAPPY
        !planned(today) || today in frozen -> PebbleFeeling.READY
        consistent >= 3 -> PebbleFeeling.PROUD
        missed >= 3 -> PebbleFeeling.DOWN
        missed == 2 -> PebbleFeeling.QUIET
        else -> PebbleFeeling.READY
    }
    return PebbleDisposition(feeling, missed)
}
