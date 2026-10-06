package dev.agneswd.ronumi.guard

import dev.agneswd.ronumi.ui.nameResource
import dev.agneswd.ronumi.ui.resolve
import dev.agneswd.ronumi.ui.quantityResource
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.ui.textResource
import dev.agneswd.ronumi.ui.ResourceText
import dev.agneswd.ronumi.data.ActiveFocus
import dev.agneswd.ronumi.data.AppLimit
import dev.agneswd.ronumi.data.BlockMode
import dev.agneswd.ronumi.data.FocusPhase
import dev.agneswd.ronumi.data.LimitMode
import dev.agneswd.ronumi.data.Schedule
import dev.agneswd.ronumi.data.Settings
import java.time.LocalDateTime

enum class BlockKind { FOCUS, SCHEDULE, LIMIT, SHORTS, SITE, PROTECTION, STUDY, MULTI_WINDOW }

/** Why the block screen shows. [gentle] blocks let the user take 5 more minutes. */
data class BlockReason(
    val kind: BlockKind,
    val title: ResourceText,
    val detail: ResourceText,
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
                return Verdict.Block(BlockReason(BlockKind.FOCUS, textResource(R.string.block_focus_title, label), textResource(R.string.block_focus_detail, time(focus.phaseEndsAt, use24Hour))))
            }
        }

        // A pause that began before protection locked does not open a protected schedule.
        if (settings.pauseBlocksUntil > System.currentTimeMillis() && !(settings.protection && locked(now))) return Verdict.Allow

        if (pkg !in essentials) {
            activeSchedules(now).firstOrNull { it.mode.blocks(pkg, it.packages) }?.let { schedule ->
                return Verdict.Block(
                    BlockReason(BlockKind.SCHEDULE, textResource(R.string.block_schedule_title, label, schedule.name.nameResource()), textResource(R.string.block_schedule_detail, minuteText(schedule.endMinute, use24Hour))),
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
                textResource(R.string.block_limit_title, minutesResource(limit.minutesPerDay), label),
                if (limit.mode == LimitMode.STRICT) textResource(R.string.block_limit_reset) else textResource(R.string.block_limit_wait),
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

/** A clock time using the selected hour cycle and the current locale. */
fun minuteText(minuteOfDay: Int, use24: Boolean): String {
    val minute = minuteOfDay.coerceIn(0, 1439)
    val locale = java.util.Locale.getDefault()
    val pattern = if (locale.language == "en") {
        if (use24) "HH:mm" else "h:mm a"
    } else android.text.format.DateFormat.getBestDateTimePattern(locale, if (use24) "Hm" else "hm")
    return java.time.LocalTime.of(minute / 60, minute % 60).format(
        java.time.format.DateTimeFormatter.ofPattern(pattern, locale)
            .withDecimalStyle(java.time.format.DecimalStyle.of(locale)),
    )
}

fun minutesResource(minutes: Int): ResourceText = when {
    minutes < 60 -> quantityResource(R.plurals.duration_minutes, minutes, minutes)
    minutes % 60 == 0 -> quantityResource(R.plurals.duration_hours, minutes / 60, minutes / 60)
    else -> quantityResource(R.plurals.duration_hours_minutes, minutes / 60, minutes / 60, minutes % 60)
}

fun formatMinutes(context: android.content.Context, minutes: Int): String = minutesResource(minutes).resolve(context)

fun formatDuration(context: android.content.Context, millis: Long): String = formatMinutes(context, (millis / 60_000).toInt())

fun time(epochMillis: Long, use24: Boolean): String {
    val zoned = java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault())
    return minuteText(zoned.hour * 60 + zoned.minute, use24)
}
