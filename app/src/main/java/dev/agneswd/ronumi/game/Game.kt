package dev.agneswd.ronumi.game

import dev.agneswd.ronumi.ui.textResource
import dev.agneswd.ronumi.ui.ResourceText
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.data.FocusSession
import dev.agneswd.ronumi.data.Settings
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

data class Quest(val id: String, val title: ResourceText, val progress: Int, val target: Int, val xp: Int, val category: String = "Focus", val detail: ResourceText = textResource(R.string.quest_empty_detail)) {
    @get:androidx.annotation.StringRes
    val categoryRes: Int
        get() = when (category) {
            "Focus" -> R.string.quest_category_focus
            "Finish" -> R.string.quest_category_finish
            "Practice" -> R.string.quest_category_practice
            "Your pace" -> R.string.quest_category_your_pace
            "Morning" -> R.string.quest_category_morning
            "Steady focus" -> R.string.quest_category_steady_focus
            "Intention" -> R.string.quest_category_intention
            else -> R.string.quest_category_focus
        }
    val done: Boolean get() = progress >= target
    val fraction: Float get() = (progress.toFloat() / target).coerceIn(0f, 1f)
}

data class Badge(val id: String, @param:androidx.annotation.StringRes val titleRes: Int, @param:androidx.annotation.StringRes val detailRes: Int, val unlocked: Boolean, val progress: Float, val category: String = "Milestones")

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
    val disposition: RonumiDisposition = RonumiDisposition(),
)

fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/** New sessions keep their original reward date through travel and clock corrections. */
fun FocusSession.rewardDate(): LocalDate =
    runCatching { LocalDate.parse(rewardDay) }.getOrElse { day(startedAt) }

fun FocusSession.rewardHour(): Int = rewardStartHour.takeIf { it in 0..23 }
    ?: Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).hour

/** Bound each record before summing, including old databases and imported history. */
internal fun FocusSession.safeFocusMillis(): Long = focusedMillis.coerceIn(0, 48 * 60 * 60_000L)
internal fun List<FocusSession>.focusMinutesTotal(): Int =
    (sumOf { it.safeFocusMillis() } / 60_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

/** Total XP needed to reach [level]. Level 1 starts at 0; each level needs 100 more than the last. */
fun xpToReach(level: Int): Int = threshold(level).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

private fun threshold(level: Int): Long = 50L * level.coerceIn(1, 10_000) * (level.coerceIn(1, 10_000) - 1)

fun levelFor(xp: Int): Level {
    val safeXp = xp.coerceAtLeast(0).toLong()
    var n = 1
    while (n < 10_000 && safeXp >= threshold(n + 1)) n++
    return Level(n, (safeXp - threshold(n)).toInt(), (threshold(n + 1) - threshold(n)).toInt())
}

fun gameState(sessions: List<FocusSession>, settings: Settings, today: LocalDate = LocalDate.now()): GameState {
    val byDay = sessions.groupBy { it.rewardDate() }
    val minutesByDay = byDay.mapValues { (_, list) -> list.focusMinutesTotal() }
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
    val questXp = byDay.entries.sumOf { (d, list) -> questsFor(d, list, list.minBy { it.startedAt }.goalMinutes).filter { it.done }.sumOf { it.xp.toLong() } }
    val sessionXp = sessions.sumOf { it.safeFocusMillis() / 60_000 * XP_PER_MINUTE + if (it.completed) XP_COMPLETED else 0 }
    val xp = (sessionXp + questXp + settings.bonusXp).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    val total = sessions.focusMinutesTotal()

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
        disposition = ronumiDisposition(sessions, settings, today),
    )
}

/** Legacy rules stay unchanged so an upgrade cannot remove earned XP. */
internal fun legacyQuestsFor(date: LocalDate, sessions: List<FocusSession>, goalMinutes: Int): List<Quest> {
    val minutes = sessions.focusMinutesTotal()
    val completed = sessions.count { it.completed }
    val longest = (sessions.maxOfOrNull { it.safeFocusMillis() } ?: 0L) / 60_000
    val morning = sessions.filter { it.rewardHour() < 12 }
        .sumOf { it.safeFocusMillis() } / 60_000
    val pool = listOf(
        Quest("focus25", textResource(R.string.quest_legacy_focus25_title), minutes.coerceAtMost(25), 25, 20, "Focus", textResource(R.string.quest_legacy_focus25_detail)),
        Quest("complete1", textResource(R.string.quest_legacy_complete1_title), completed.coerceAtMost(1), 1, 15, "Finish", textResource(R.string.quest_legacy_complete1_detail)),
        Quest("sessions2", textResource(R.string.quest_legacy_sessions2_title), sessions.size.coerceAtMost(2), 2, 20, "Practice", textResource(R.string.quest_legacy_sessions2_detail)),
        Quest("goal", textResource(R.string.quest_legacy_goal_title), minutes.coerceAtMost(goalMinutes), goalMinutes, 40, "Your pace", dev.agneswd.ronumi.ui.quantityResource(R.plurals.quest_legacy_goal_detail, goalMinutes, goalMinutes)),
        Quest("morning", textResource(R.string.quest_legacy_morning_title), morning.toInt().coerceAtMost(20), 20, 25, "Morning", textResource(R.string.quest_legacy_morning_detail)),
        Quest("long45", textResource(R.string.quest_legacy_long45_title), longest.toInt().coerceAtMost(45), 45, 30, "Steady focus", textResource(R.string.quest_legacy_long45_detail)),
        Quest("focus60", textResource(R.string.quest_legacy_focus60_title), minutes.coerceAtMost(60), 60, 30, "Focus", textResource(R.string.quest_legacy_focus60_detail)),
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
    val hours = sessions.map { it.rewardHour() }
    val longest = (sessions.maxOfOrNull { it.safeFocusMillis() } ?: 0L) / 60_000
    fun b(id: String, @androidx.annotation.StringRes title: Int, @androidx.annotation.StringRes detail: Int, value: Float, target: Float) =
        Badge(id, title, detail, value >= target, (value / target).coerceIn(0f, 1f))
    val completed = sessions.filter { it.completed }
    val days = sessions.groupBy { it.rewardDate() }
    val activeDays = days.count { (_, rows) -> rows.sumOf { it.safeFocusMillis() } >= STREAK_MINUTES * 60_000L }
    val goalDays = days.count { (_, rows) -> rows.sumOf { it.safeFocusMillis() } >= rows.minBy { it.startedAt }.goalMinutes * 60_000L }
    val named = completed.count { it.tag.isNotBlank() }
    val reflected = completed.count { it.notes.isNotBlank() }
    val questDays = days.count { (date, rows) -> questsFor(date, rows, rows.minBy { it.startedAt }.goalMinutes).all { it.done } }
    val questCount = days.entries.sumOf { (date, rows) -> questsFor(date, rows, rows.minBy { it.startedAt }.goalMinutes).count { it.done } }
    return listOf(
        b("first", R.string.badge_first_title, R.string.badge_first_detail, sessions.count { it.completed }.toFloat(), 1f),
        b("streak3", R.string.badge_streak3_title, R.string.badge_streak3_detail, streak.toFloat(), 3f),
        b("streak7", R.string.badge_streak7_title, R.string.badge_streak7_detail, streak.toFloat(), 7f),
        b("streak30", R.string.badge_streak30_title, R.string.badge_streak30_detail, streak.toFloat(), 30f),
        b("hours10", R.string.badge_hours10_title, R.string.badge_hours10_detail, totalMinutes / 60f, 10f),
        b("hours50", R.string.badge_hours50_title, R.string.badge_hours50_detail, totalMinutes / 60f, 50f),
        b("marathon", R.string.badge_marathon_title, R.string.badge_marathon_detail, longest.toFloat(), 120f),
        b("early", R.string.badge_early_title, R.string.badge_early_detail, if (hours.any { it < 7 }) 1f else 0f, 1f),
        b("night", R.string.badge_night_title, R.string.badge_night_detail, if (hours.any { it >= 22 }) 1f else 0f, 1f),
        b("sessions50", R.string.badge_sessions50_title, R.string.badge_sessions50_detail, completed.size.toFloat(), 50f),
        b("sessions5", R.string.badge_sessions5_title, R.string.badge_sessions5_detail, completed.size.toFloat(), 5f),
        b("sessions10", R.string.badge_sessions10_title, R.string.badge_sessions10_detail, completed.size.toFloat(), 10f),
        b("sessions25", R.string.badge_sessions25_title, R.string.badge_sessions25_detail, completed.size.toFloat(), 25f),
        b("sessions100", R.string.badge_sessions100_title, R.string.badge_sessions100_detail, completed.size.toFloat(), 100f),
        b("hours1", R.string.badge_hours1_title, R.string.badge_hours1_detail, totalMinutes.toFloat(), 60f),
        b("hours5", R.string.badge_hours5_title, R.string.badge_hours5_detail, totalMinutes.toFloat(), 300f),
        b("hours25", R.string.badge_hours25_title, R.string.badge_hours25_detail, totalMinutes.toFloat(), 1500f),
        b("hours100", R.string.badge_hours100_title, R.string.badge_hours100_detail, totalMinutes.toFloat(), 6000f),
        b("streak14", R.string.badge_streak14_title, R.string.badge_streak14_detail, streak.toFloat(), 14f),
        b("days7", R.string.badge_days7_title, R.string.badge_days7_detail, activeDays.toFloat(), 7f),
        b("days30", R.string.badge_days30_title, R.string.badge_days30_detail, activeDays.toFloat(), 30f),
        b("days100", R.string.badge_days100_title, R.string.badge_days100_detail, activeDays.toFloat(), 100f),
        b("goals1", R.string.badge_goals1_title, R.string.badge_goals1_detail, goalDays.toFloat(), 1f),
        b("goals7", R.string.badge_goals7_title, R.string.badge_goals7_detail, goalDays.toFloat(), 7f),
        b("goals30", R.string.badge_goals30_title, R.string.badge_goals30_detail, goalDays.toFloat(), 30f),
        b("named1", R.string.badge_named1_title, R.string.badge_named1_detail, named.toFloat(), 1f),
        b("named10", R.string.badge_named10_title, R.string.badge_named10_detail, named.toFloat(), 10f),
        b("notes1", R.string.badge_notes1_title, R.string.badge_notes1_detail, reflected.toFloat(), 1f),
        b("notes10", R.string.badge_notes10_title, R.string.badge_notes10_detail, reflected.toFloat(), 10f),
        b("quests10", R.string.badge_quests10_title, R.string.badge_quests10_detail, questCount.toFloat(), 10f),
        b("quests50", R.string.badge_quests50_title, R.string.badge_quests50_detail, questCount.toFloat(), 50f),
        b("questday1", R.string.badge_questday1_title, R.string.badge_questday1_detail, questDays.toFloat(), 1f),
        b("questday7", R.string.badge_questday7_title, R.string.badge_questday7_detail, questDays.toFloat(), 7f),
    ).filter { it.id !in setOf("early", "night", "marathon") || it.unlocked }
}

fun studyDay(settings: Settings, date: LocalDate): Boolean = settings.goalDays and (1 shl (date.dayOfWeek.value - 1)) != 0
