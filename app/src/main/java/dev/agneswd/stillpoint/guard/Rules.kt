package dev.agneswd.stillpoint.guard

import dev.agneswd.stillpoint.data.ActiveFocus
import dev.agneswd.stillpoint.data.AppLimit
import dev.agneswd.stillpoint.data.BlockMode
import dev.agneswd.stillpoint.data.FocusPhase
import dev.agneswd.stillpoint.data.LimitMode
import dev.agneswd.stillpoint.data.Schedule
import dev.agneswd.stillpoint.data.Settings
import java.time.LocalDateTime

enum class BlockKind { FOCUS, SCHEDULE, LIMIT, SHORTS, SITE, PROTECTION, STUDY, MULTI_WINDOW }

/** Why the block screen shows. [gentle] blocks let the user take 5 more minutes. */
data class BlockReason(
    val kind: BlockKind,
    val title: String,
    val detail: String,
    val gentle: Boolean = false,
)

sealed interface Verdict {
    data object Allow : Verdict

    /** Bring the focus screen back, because the session locks the home screen. */
    data object ReturnToFocus : Verdict

    data class Block(val reason: BlockReason) : Verdict
}

/** Everything the guard needs to decide, read from the database in one place. */
data class Rules(
    val settings: Settings = Settings(),
    val limits: Map<String, AppLimit> = emptyMap(),
    val schedules: List<Schedule> = emptyList(),
    val sites: Set<String> = emptySet(),
    val focus: ActiveFocus? = null,
) {
    val focusing: Boolean get() = focus?.let { it.phase == FocusPhase.FOCUS && it.running } == true

    fun activeSchedules(now: LocalDateTime): List<Schedule> = schedules.filter { it.enabled && it.isActive(now) }

    /** True while a focus round or a schedule runs. Protection and held notifications use this. */
    fun locked(now: LocalDateTime): Boolean = focusing || activeSchedules(now).isNotEmpty()

    /**
     * Decides what happens when [pkg] comes to the front.
     * [usedToday] runs only when a limit applies, because it reads the usage log.
     */
    fun decide(
        pkg: String,
        label: String,
        now: LocalDateTime,
        essentials: Set<String>,
        launchers: Set<String>,
        allowedUntil: Long,
        use24Hour: Boolean = true,
        usedToday: () -> Long,
    ): Verdict {
        val focus = focus
        if (focus != null && focusing) {
            if (focus.lockHome && pkg in launchers) return Verdict.ReturnToFocus
            if (pkg !in essentials && focus.mode.blocks(pkg, focus.packages)) {
                return Verdict.Block(BlockReason(BlockKind.FOCUS, "$label is blocked during focus", "Your focus round ends at ${time(focus.phaseEndsAt, use24Hour)}."))
            }
        }

        // A pause that began before protection locked does not open a protected schedule.
        if (settings.pauseBlocksUntil > System.currentTimeMillis() && !(settings.protection && locked(now))) return Verdict.Allow

        if (pkg !in essentials) {
            activeSchedules(now).firstOrNull { it.mode.blocks(pkg, it.packages) }?.let { schedule ->
                return Verdict.Block(
                    BlockReason(BlockKind.SCHEDULE, "$label is blocked during ${schedule.name}", "The block ends at ${minuteText(schedule.endMinute, use24Hour)}."),
                )
            }
        }

        val limit = limits[pkg]?.takeIf { it.enabled } ?: return Verdict.Allow
        if (limit.mode == LimitMode.GENTLE && System.currentTimeMillis() < allowedUntil) return Verdict.Allow
        val usedMinutes = usedToday() / 60_000
        if (usedMinutes < limit.minutesPerDay) return Verdict.Allow
        return Verdict.Block(
            BlockReason(
                BlockKind.LIMIT,
                "Your ${formatMinutes(limit.minutesPerDay)} on $label is used up",
                if (limit.mode == LimitMode.STRICT) "The limit resets at midnight." else "Wait a moment if you really need it.",
                gentle = limit.mode == LimitMode.GENTLE,
            ),
        )
    }
}

fun BlockMode.blocks(pkg: String, packages: Set<String>): Boolean = when (this) {
    BlockMode.LISTED -> pkg in packages
    BlockMode.ALL_EXCEPT -> pkg !in packages
}

/** Bit for a day in [Schedule.days]. Monday is bit 0. */
fun dayBit(dayOfWeek: java.time.DayOfWeek): Int = 1 shl (dayOfWeek.value - 1)

fun Schedule.isActive(now: LocalDateTime): Boolean {
    val minute = now.hour * 60 + now.minute
    val today = days and dayBit(now.dayOfWeek) != 0
    val yesterday = days and dayBit(now.dayOfWeek.minus(1)) != 0
    return when {
        startMinute == endMinute -> today
        startMinute < endMinute -> today && minute in startMinute until endMinute
        else -> (today && minute >= startMinute) || (yesterday && minute < endMinute)
    }
}

/** True when [style] or the phone setting uses a 24-hour clock. */
fun android.content.Context.uses24HourClock(style: String): Boolean = when (style) {
    "H12" -> false
    "H24" -> true
    else -> android.text.format.DateFormat.is24HourFormat(this)
}

/** A clock time. 12-hour text uses AM and PM. */
fun minuteText(minuteOfDay: Int, use24: Boolean): String {
    val minute = minuteOfDay.coerceIn(0, 1439)
    val hour = minute / 60
    val mins = minute % 60
    if (use24) return "%02d:%02d".format(hour, mins)
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    val suffix = if (hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(hour12, mins, suffix)
}

fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

fun formatDuration(millis: Long): String = formatMinutes((millis / 60_000).toInt())

fun time(epochMillis: Long, use24: Boolean): String {
    val zoned = java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault())
    return minuteText(zoned.hour * 60 + zoned.minute, use24)
}
