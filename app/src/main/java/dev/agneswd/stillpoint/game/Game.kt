package dev.agneswd.stillpoint.game

import dev.agneswd.stillpoint.data.FocusSession
import dev.agneswd.stillpoint.data.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The game layer: XP, levels, streaks, daily quests and badges.
 * Everything here is computed from the focus history, so it never gets out of sync
 * and needs no extra reward ledger. [Settings] stores freezes and equipped items.
 */

/** A day counts for the streak with at least this much focus. */
const val STREAK_MINUTES = 10

/** XP for each focus minute, and a bonus for a session that ran to the end. */
private const val XP_PER_MINUTE = 1
private const val XP_COMPLETED = 15

data class Quest(val id: String, val title: String, val progress: Int, val target: Int, val xp: Int, val category: String = "Focus", val detail: String = "") {
    val done: Boolean get() = progress >= target
    val fraction: Float get() = (progress.toFloat() / target).coerceIn(0f, 1f)
}

data class Badge(val id: String, val title: String, val detail: String, val unlocked: Boolean, val progress: Float, val category: String = "Milestones")

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
    val disposition: PebbleDisposition = PebbleDisposition(),
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
    while (counts(cursor) || cursor in frozen || !studyDay(settings, cursor)) {
        if (counts(cursor)) streak++
        cursor = cursor.minusDays(1)
    }

    // Quest XP counts for every past day too, so XP never drops.
    val questXp = byDay.entries.sumOf { (d, list) -> questsFor(d, list, list.minBy { it.startedAt }.goalMinutes).filter { it.done }.sumOf { it.xp } }
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
        quests = questsFor(today, byDay[today].orEmpty(), byDay[today]?.minByOrNull { it.startedAt }?.goalMinutes ?: settings.focusGoalMinutes),
        badges = badges(sessions, bestStreak(minutesByDay, frozen, settings), total),
        week = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (minutesByDay[it] ?: 0) },
        totalMinutes = total,
        sessions = sessions.size,
        disposition = pebbleDisposition(sessions, settings, today),
    )
}

/** Legacy rules stay unchanged so an upgrade cannot remove earned XP. */
internal fun legacyQuestsFor(date: LocalDate, sessions: List<FocusSession>, goalMinutes: Int): List<Quest> {
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

/** Milestone badges use the best recorded streak, so a missed day cannot remove them. */
private fun bestStreak(minutes: Map<LocalDate, Int>, frozen: Set<LocalDate>, settings: Settings): Int {
    val days = (minutes.keys + frozen).sorted()
    var previous: LocalDate? = null
    var current = 0
    var best = 0
    for (d in days) {
        if ((minutes[d] ?: 0) < STREAK_MINUTES && d !in frozen) continue
        val prev = previous
        if (prev == null || generateSequence(prev.plusDays(1)) { it.plusDays(1) }.takeWhile { it < d }
                .any { studyDay(settings, it) && it !in frozen && (minutes[it] ?: 0) < STREAK_MINUTES }) current = 0
        if ((minutes[d] ?: 0) >= STREAK_MINUTES) current++
        best = maxOf(best, current)
        previous = d
    }
    return best
}

private fun badges(sessions: List<FocusSession>, streak: Int, totalMinutes: Int): List<Badge> {
    val hours = sessions.map { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).hour }
    val longest = (sessions.maxOfOrNull { it.focusedMillis } ?: 0L) / 60_000
    fun b(id: String, title: String, detail: String, value: Float, target: Float) =
        Badge(id, title, detail, value >= target, (value / target).coerceIn(0f, 1f))
    val completed = sessions.filter { it.completed }
    val days = sessions.groupBy { day(it.startedAt) }
    val activeDays = days.count { (_, rows) -> rows.sumOf { it.focusedMillis } >= STREAK_MINUTES * 60_000L }
    val goalDays = days.count { (_, rows) -> rows.sumOf { it.focusedMillis } >= rows.minBy { it.startedAt }.goalMinutes * 60_000L }
    val named = completed.count { it.tag.isNotBlank() }
    val reflected = completed.count { it.notes.isNotBlank() }
    val questDays = days.count { (date, rows) -> questsFor(date, rows, rows.minBy { it.startedAt }.goalMinutes).all { it.done } }
    val questCount = days.entries.sumOf { (date, rows) -> questsFor(date, rows, rows.minBy { it.startedAt }.goalMinutes).count { it.done } }
    return listOf(
        b("first", "First step", "Finish your first focus session", sessions.count { it.completed }.toFloat(), 1f),
        b("streak3", "Warming up", "Keep a 3 day streak", streak.toFloat(), 3f),
        b("streak7", "On fire", "Keep a 7 day streak", streak.toFloat(), 7f),
        b("streak30", "Unshakable", "Keep a 30 day streak", streak.toFloat(), 30f),
        b("hours10", "Deep diver", "Focus for 10 hours in total", totalMinutes / 60f, 10f),
        b("hours50", "Mountain mover", "Focus for 50 hours in total", totalMinutes / 60f, 50f),
        b("marathon", "Marathon", "Stay in one session for 2 hours", longest.toFloat(), 120f),
        b("early", "Early bird", "Start a session before 7 in the morning", if (hours.any { it < 7 }) 1f else 0f, 1f),
        b("night", "Night owl", "Start a session after 10 at night", if (hours.any { it >= 22 }) 1f else 0f, 1f),
        b("sessions50", "Habit builder", "Finish 50 sessions", completed.size.toFloat(), 50f),
        b("sessions5", "Finding a rhythm", "Finish 5 sessions", completed.size.toFloat(), 5f),
        b("sessions10", "A steady start", "Finish 10 sessions", completed.size.toFloat(), 10f),
        b("sessions25", "Practice makes progress", "Finish 25 sessions", completed.size.toFloat(), 25f),
        b("sessions100", "Here to stay", "Finish 100 sessions", completed.size.toFloat(), 100f),
        b("hours1", "Time well spent", "Focus for 1 hour in total", totalMinutes.toFloat(), 60f),
        b("hours5", "Room to grow", "Focus for 5 hours in total", totalMinutes.toFloat(), 300f),
        b("hours25", "Making room", "Focus for 25 hours in total", totalMinutes.toFloat(), 1500f),
        b("hours100", "A lasting practice", "Focus for 100 hours in total", totalMinutes.toFloat(), 6000f),
        b("streak14", "Two weeks together", "Keep a 14 day streak", streak.toFloat(), 14f),
        b("days7", "Seven small steps", "Focus for at least 10 minutes on 7 days", activeDays.toFloat(), 7f),
        b("days30", "Time after time", "Focus for at least 10 minutes on 30 days", activeDays.toFloat(), 30f),
        b("days100", "Always welcome back", "Focus for at least 10 minutes on 100 days", activeDays.toFloat(), 100f),
        b("goals1", "Your own pace", "Reach your daily goal once", goalDays.toFloat(), 1f),
        b("goals7", "Making space", "Reach your daily goal on 7 days", goalDays.toFloat(), 7f),
        b("goals30", "A plan that works", "Reach your daily goal on 30 days", goalDays.toFloat(), 30f),
        b("named1", "With purpose", "Finish a named session", named.toFloat(), 1f),
        b("named10", "Clear intentions", "Finish 10 named sessions", named.toFloat(), 10f),
        b("notes1", "A moment to reflect", "Add a note to a completed session", reflected.toFloat(), 1f),
        b("notes10", "Learning as you go", "Add notes to 10 completed sessions", reflected.toFloat(), 10f),
        b("quests10", "Curious Pebble", "Complete 10 daily quests", questCount.toFloat(), 10f),
        b("quests50", "Quest companion", "Complete 50 daily quests", questCount.toFloat(), 50f),
        b("questday1", "A full little day", "Complete every quest on one day", questDays.toFloat(), 1f),
        b("questday7", "Seven good days", "Complete every quest on 7 days", questDays.toFloat(), 7f),
    ).filter { it.id !in setOf("early", "night", "marathon") || it.unlocked }
}

fun studyDay(settings: Settings, date: LocalDate): Boolean = settings.goalDays and (1 shl (date.dayOfWeek.value - 1)) != 0
