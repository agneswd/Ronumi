package dev.agneswd.stillpoint.game

import dev.agneswd.stillpoint.data.Settings
import java.time.LocalDate

/** A later real streak can earn freezes. Returning to an old date cannot earn them twice. */
internal fun rewardFreeze(settings: Settings, weeks: Int, milestone: LocalDate): Settings {
    val through = settings.freezeRewardedThrough.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
    if (through != null && milestone <= through) return settings
    return settings.copy(
        streakFreezes = (settings.streakFreezes + 1).coerceAtMost(2),
        freezeWeeksRewarded = maxOf(settings.freezeWeeksRewarded, weeks),
        freezeRewardedThrough = milestone.toString(),
    )
}
