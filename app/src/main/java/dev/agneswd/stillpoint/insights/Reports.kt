package dev.agneswd.stillpoint.insights

import dev.agneswd.stillpoint.data.FocusSession
import dev.agneswd.stillpoint.data.Settings
import dev.agneswd.stillpoint.data.UsageDay
import dev.agneswd.stillpoint.game.day
import dev.agneswd.stillpoint.guard.blocks
import java.time.LocalDate

/** A report uses stored usage, so it can include dates beyond Android's event retention. */
data class Report(
    val focusMinutes: Long,
    val averageMinutes: Long,
    val tags: List<Pair<String, Long>>,
    val productiveMillis: Long,
    val distractingMillis: Long,
    val notificationsHeld: Int,
    val timeSavedMillis: Long?,
    val uncategorizedMillis: Long = 0,
) {
    val screenMillis: Long get() = productiveMillis + distractingMillis + uncategorizedMillis
}

/** Only complete days with a saved budget count. Later budget edits do not rewrite them. */
fun limitStreak(usage: List<UsageDay>, pkg: String, today: LocalDate = LocalDate.now()): Int {
    val byDay = usage.associateBy { it.day }
    var date = today.minusDays(1)
    var count = 0
    while (true) {
        val record = byDay[date.toString()] ?: break
        val budget = record.limitMinutes[pkg] ?: break
        if ((record.perApp[pkg] ?: 0) > budget * 60_000) break
        count++
        date = date.minusDays(1)
    }
    return count
}

/** Productive choices take priority. Pass the guard's essential packages to exclude apps it cannot block. */
fun report(
    sessions: List<FocusSession>,
    usage: List<UsageDay>,
    settings: Settings,
    days: Int,
    today: LocalDate = LocalDate.now(),
    essentialPackages: Set<String> = emptySet(),
): Report {
    val start = today.minusDays(days.toLong() - 1)
    val selected = sessions.filter { day(it.startedAt) in start..today }
    val records = usage.filter { LocalDate.parse(it.day) in start..today }
    val total = selected.sumOf { it.focusedMillis } / 60_000
    val tags = selected.groupBy { it.tag.ifBlank { "Untagged" } }.map { (tag, list) -> tag to list.sumOf { it.focusedMillis } / 60_000 }.sortedByDescending { it.second }
    val productive = records.sumOf { record -> record.perApp.filterKeys { it in settings.productivePackages }.values.sum() }
    val distracting = records.sumOf { record ->
        record.perApp.filterKeys { pkg ->
            settings.focusMode.blocks(pkg, settings.focusPackages) && pkg !in settings.productivePackages && pkg !in essentialPackages
        }.values.sum()
    }
    val uncategorized = records.sumOf { it.perApp.values.sum() } - productive - distracting
    // Compare complete days with the first seven recorded complete days.
    val baseline = usage.filter { it.day < today.toString() && it.perApp.isNotEmpty() }.sortedBy { it.day }.take(7)
    val complete = records.filter { it.day < today.toString() }
    val saved = if (baseline.size < 7 || complete.isEmpty()) null else {
        val average = baseline.sumOf { it.perApp.values.sum() } / 7
        (average * complete.size - complete.sumOf { it.perApp.values.sum() }).coerceAtLeast(0)
    }
    return Report(total, total / days, tags, productive, distracting, records.sumOf { it.heldCount }, saved, uncategorized)
}
