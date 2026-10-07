package dev.agneswd.ronumi.guard

import dev.agneswd.ronumi.data.BlockMode
import dev.agneswd.ronumi.data.LimitMode
import dev.agneswd.ronumi.plus.FreeLimits

/**
 * The rules the guard applies. Without Plus, saved rules stay saved but only the free part works:
 * the oldest enabled schedule, the first saved app limit (as a gentle limit), five apps per block list,
 * and no website, short-video, study, strict, protection, or notification-holding rules.
 * A strict focus session that already runs keeps its apps, its strict mode, and protection until it ends.
 */
fun Rules.forPlus(hasPlus: Boolean): Rules {
    if (hasPlus) return this
    val strictSession = focus?.strict == true
    val activeSchedule = schedules.filter { it.enabled }.minByOrNull { it.id }?.id
    return copy(
        settings = settings.copy(
            focusPackages = settings.focusPackages.firstFive(settings.focusMode),
            focusStrict = false,
            blockYoutubeShorts = false,
            blockInstagramReels = false,
            blockSnapchatSpotlight = false,
            blockFacebookReels = false,
            blockAdultSites = false,
            youtubeStudyMode = false,
            blockYoutubeHome = false,
            protection = settings.protection && strictSession,
            heldPackages = emptySet(),
        ),
        limits = limits.entries.filter { it.value.enabled }.take(FreeLimits.APP_LIMITS)
            .associate { (pkg, limit) -> pkg to limit.copy(mode = LimitMode.GENTLE) },
        schedules = schedules.map { schedule ->
            if (schedule.id != activeSchedule) schedule.copy(enabled = false)
            else schedule.copy(packages = schedule.packages.firstFive(schedule.mode))
        },
        sites = emptySet(),
        focus = focus?.let { if (it.strict) it else it.copy(packages = it.packages.firstFive(it.mode)) },
    )
}

/** The saved schedules that run, for alarms and planned focus. */
fun freeSchedules(schedules: List<dev.agneswd.ronumi.data.Schedule>, hasPlus: Boolean) =
    Rules(schedules = schedules).forPlus(hasPlus).schedules

/** An allow-list (all except these) is not cut. A block list keeps its first five apps in saved order. */
private fun Set<String>.firstFive(mode: BlockMode): Set<String> =
    if (mode == BlockMode.LISTED && size > FreeLimits.FOCUS_APPS) take(FreeLimits.FOCUS_APPS).toSet() else this
