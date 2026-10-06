package dev.agneswd.ronumi.insights

import dev.agneswd.ronumi.data.FocusSession
import dev.agneswd.ronumi.data.Settings
import dev.agneswd.ronumi.data.UsageDay
import dev.agneswd.ronumi.game.rewardDate
import dev.agneswd.ronumi.guard.blocks
import java.time.LocalDate

// Keep the existing grouping key for blank tags. The report screen resolves its display text.
const val UNTAGGED_REPORT_KEY = "Untagged"

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

enum class UsageCategory { PRODUCTIVE, DISTRACTING, OTHER }

/** Uses the same priority and essential-app exclusions as the focus report. */
fun usageCategory(pkg: String, settings: Settings, essentialPackages: Set<String> = emptySet()): UsageCategory = when {
    pkg in settings.productivePackages -> UsageCategory.PRODUCTIVE
    pkg !in essentialPackages && settings.focusMode.blocks(pkg, settings.focusPackages) -> UsageCategory.DISTRACTING
    else -> UsageCategory.OTHER
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
    val selected = sessions.filter { it.rewardDate() in start..today }
    val records = usage.filter { LocalDate.parse(it.day) in start..today }
    val total = selected.sumOf { it.focusedMillis } / 60_000
    val tags = selected.groupBy { it.tag.ifBlank { UNTAGGED_REPORT_KEY } }.map { (tag, list) -> tag to list.sumOf { it.focusedMillis } / 60_000 }.sortedByDescending { it.second }
    val productive = records.sumOf { record ->
        record.perApp.filterKeys { usageCategory(it, settings, essentialPackages) == UsageCategory.PRODUCTIVE }.values.sum()
    }
    val distracting = records.sumOf { record ->
        record.perApp.filterKeys { usageCategory(it, settings, essentialPackages) == UsageCategory.DISTRACTING }.values.sum()
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
