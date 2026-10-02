package dev.agneswd.stillpoint.game

import dev.agneswd.stillpoint.data.FocusSession
import dev.agneswd.stillpoint.data.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The game layer: XP, levels, streaks, daily quests and badges.
 * Everything here is computed from the focus history, so it never gets out of sync
 * and needs no extra storage. Only streak freezes are stored, in [Settings].
 */

/** A day counts for the streak with at least this much focus. */
const val STREAK_MINUTES = 10

/** XP for each focus minute, and a bonus for a session that ran to the end. */
private const val XP_PER_MINUTE = 1
private const val XP_COMPLETED = 15

data class Quest(val id: String, val title: String, val progress: Int, val target: Int, val xp: Int) {
    val done: Boolean get() = progress >= target
    val fraction: Float get() = (progress.toFloat() / target).coerceIn(0f, 1f)
}

data class Badge(val id: String, val title: String, val detail: String, val unlocked: Boolean, val progress: Float)

data class Level(val number: Int, val xpInLevel: Int, val xpForNext: Int) {
    val fraction: Float get() = xpInLevel.toFloat() / xpForNext
}

data class GameState(
    val xp: Int,
    val level: Level,
    val streak: Int,
    /** True when today already counts for the streak. */
    val streakSafeToday: Boolean,
    val freezes: Int,
    val todayMinutes: Int,
    val goalMinutes: Int,
    val quests: List<Quest>,
    val badges: List<Badge>,
    /** Focus minutes for each of the last 7 days, oldest first. */
    val week: List<Pair<LocalDate, Int>>,
    val totalMinutes: Int,
    val sessions: Int,
)

fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/** Total XP needed to reach [level]. Level 1 starts at 0; each level needs 100 more than the last. */
fun xpToReach(level: Int): Int = 50 * level * (level - 1)

fun levelFor(xp: Int): Level {
    var n = 1
    while (xp >= xpToReach(n + 1)) n++
    return Level(n, xp - xpToReach(n), xpToReach(n + 1) - xpToReach(n))
}

fun gameState(sessions: List<FocusSession>, settings: Settings, today: LocalDate = LocalDate.now()): GameState {
    val byDay = sessions.groupBy { day(it.startedAt) }
    val minutesByDay = byDay.mapValues { (_, list) -> (list.sumOf { it.focusedMillis } / 60_000).toInt() }
    val frozen = settings.frozenDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()

    fun counts(d: LocalDate) = (minutesByDay[d] ?: 0) >= STREAK_MINUTES
    val safeToday = counts(today)
    var cursor = if (safeToday) today else today.minusDays(1)
    var streak = 0
    while (counts(cursor) || cursor in frozen) {
        if (counts(cursor)) streak++
        cursor = cursor.minusDays(1)
    }

    // Quest XP counts for every past day too, so XP never drops.
    val questXp = byDay.entries.sumOf { (d, list) -> questsFor(d, list, settings.focusGoalMinutes).filter { it.done }.sumOf { it.xp } }
    val sessionXp = sessions.sumOf { (it.focusedMillis / 60_000).toInt() * XP_PER_MINUTE + if (it.completed) XP_COMPLETED else 0 }
    val xp = sessionXp + questXp
    val total = (sessions.sumOf { it.focusedMillis } / 60_000).toInt()

    return GameState(
        xp = xp,
        level = levelFor(xp),
        streak = streak,
        streakSafeToday = safeToday,
        freezes = settings.streakFreezes,
        todayMinutes = minutesByDay[today] ?: 0,
        goalMinutes = settings.focusGoalMinutes,
        quests = questsFor(today, byDay[today].orEmpty(), settings.focusGoalMinutes),
        badges = badges(sessions, streak, total),
        week = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (minutesByDay[it] ?: 0) },
        totalMinutes = total,
        sessions = sessions.size,
    )
}

/**
 * Three quests for a day. The day picks them, so they stay the same all day
 * and change at midnight.
 */
fun questsFor(date: LocalDate, sessions: List<FocusSession>, goalMinutes: Int): List<Quest> {
    val minutes = (sessions.sumOf { it.focusedMillis } / 60_000).toInt()
    val completed = sessions.count { it.completed }
    val longest = (sessions.maxOfOrNull { it.focusedMillis } ?: 0L) / 60_000
    val morning = sessions.filter { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).hour < 12 }
        .sumOf { it.focusedMillis } / 60_000
    val pool = listOf(
        Quest("focus25", "Focus for 25 minutes", minutes.coerceAtMost(25), 25, 20),
        Quest("complete1", "Finish a session without giving up", completed.coerceAtMost(1), 1, 15),
        Quest("sessions2", "Do 2 focus sessions", sessions.size.coerceAtMost(2), 2, 20),
        Quest("goal", "Reach your daily goal", minutes.coerceAtMost(goalMinutes), goalMinutes, 40),
        Quest("morning", "Focus 20 minutes before noon", morning.toInt().coerceAtMost(20), 20, 25),
        Quest("long45", "Stay in one session for 45 minutes", longest.toInt().coerceAtMost(45), 45, 30),
        Quest("focus60", "Focus for 1 hour in total", minutes.coerceAtMost(60), 60, 30),
    )
    val seed = date.toEpochDay()
    // Always one easy quest first, then two more picked by the date.
    val rest = pool.drop(1).sortedBy { ((it.id.hashCode().toLong() * 31 + seed) % 97 + 97) % 97 }.take(2)
    return listOf(pool[0]) + rest
}

private fun badges(sessions: List<FocusSession>, streak: Int, totalMinutes: Int): List<Badge> {
    val hours = sessions.map { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).hour }
    val longest = (sessions.maxOfOrNull { it.focusedMillis } ?: 0L) / 60_000
    fun b(id: String, title: String, detail: String, value: Float, target: Float) =
        Badge(id, title, detail, value >= target, (value / target).coerceIn(0f, 1f))
    return listOf(
        b("first", "First step", "Finish your first focus session", sessions.size.toFloat(), 1f),
        b("streak3", "Warming up", "Keep a 3 day streak", streak.toFloat(), 3f),
        b("streak7", "On fire", "Keep a 7 day streak", streak.toFloat(), 7f),
        b("streak30", "Unshakable", "Keep a 30 day streak", streak.toFloat(), 30f),
        b("hours10", "Deep diver", "Focus for 10 hours in total", totalMinutes / 60f, 10f),
        b("hours50", "Mountain mover", "Focus for 50 hours in total", totalMinutes / 60f, 50f),
        b("marathon", "Marathon", "Stay in one session for 2 hours", longest.toFloat(), 120f),
        b("early", "Early bird", "Start a session before 7 in the morning", if (hours.any { it < 7 }) 1f else 0f, 1f),
        b("night", "Night owl", "Start a session after 10 at night", if (hours.any { it >= 22 }) 1f else 0f, 1f),
        b("sessions50", "Habit builder", "Finish 50 sessions", sessions.count { it.completed }.toFloat(), 50f),
    )
}
