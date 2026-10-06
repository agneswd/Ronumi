package dev.agneswd.ronumi.data

import dev.agneswd.ronumi.R
import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** How a block treats the apps in its list. */
enum class BlockMode {
    /** Block only the listed apps. */
    LISTED,

    /** Block every app except the listed ones and the essential system apps. */
    ALL_EXCEPT,
}

/** What the user sees when a daily limit runs out. */
enum class LimitMode {
    /** A short wait, then the user can take 5 more minutes. */
    GENTLE,

    /** No way around the block until midnight. */
    STRICT,
}

enum class FocusSound(@param:StringRes val labelRes: Int) {
    OFF(R.string.focus_sound_off),
    WHITE(R.string.focus_sound_white),
    PINK(R.string.focus_sound_pink),
    BROWN(R.string.focus_sound_brown),
    RAIN(R.string.focus_sound_rain),
    WAVES(R.string.focus_sound_waves),
}

enum class FocusPhase { FOCUS, BREAK }

/** How a focus session counts time. */
enum class TimerMode {
    /** One countdown. */
    TIMER,

    /** Counts up until the user stops it. */
    STOPWATCH,

    /** Focus rounds with short breaks and a long break after every fourth round. */
    POMODORO,
}

/** A daily time budget for one app. The day resets at local midnight. */
@Serializable
@Entity
data class AppLimit(
    @PrimaryKey val packageName: String,
    val minutesPerDay: Int,
    val mode: LimitMode = LimitMode.GENTLE,
    val enabled: Boolean = true,
    val reminderMinutes: Int = 0,
)

/**
 * A recurring block window. [startMinute] and [endMinute] are minutes after midnight.
 * A window with end before start runs past midnight. Equal values block the whole day.
 * [days] is a bit mask where bit 0 is Monday and bit 6 is Sunday. It applies to the start day.
 */
@Serializable
@Entity
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startMinute: Int,
    val endMinute: Int,
    val days: Int = 0b1111111,
    val packages: Set<String> = emptySet(),
    val mode: BlockMode = BlockMode.LISTED,
    val enabled: Boolean = true,
    val startFocus: Boolean = false,
    val focusMinutes: Int = 25,
    val icon: String = "auto",
)

val SCHEDULE_ICONS = setOf("auto", "focus", "study", "work", "sleep", "exercise", "coffee", "home", "music", "book")

/** A blocked domain. It also blocks every subdomain. */
@Serializable
@Entity
data class BlockedSite(@PrimaryKey val domain: String)

/** One finished focus session. */
@Serializable
@Entity
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val focusedMillis: Long,
    val completed: Boolean,
    val tag: String = "",
    val notes: String = "",
    val goalMinutes: Int = 120,
    /** Keeps the quest rules used when this day began. */
    val questVersion: Int = 0,
    val rewardDay: String = "",
    val rewardStartHour: Int = -1,
)

/** A notification that the listener removed and kept for later. */
@Entity
data class HeldNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val notificationKey: String = "",
    val title: String,
    val text: String,
    val postedAt: Long,
)

/** The single row of user preferences. Its id is always 0. */
@Serializable
@Entity
data class Settings(
    @PrimaryKey val id: Int = 0,
    val focusGoalMinutes: Int = 120,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val focusRounds: Int = 4,
    val focusPackages: Set<String> = emptySet(),
    val focusMode: BlockMode = BlockMode.LISTED,
    val focusStrict: Boolean = false,
    val focusLockHome: Boolean = false,
    val focusSound: FocusSound = FocusSound.OFF,
    val blockYoutubeShorts: Boolean = false,
    val blockInstagramReels: Boolean = false,
    val blockSnapchatSpotlight: Boolean = false,
    val blockFacebookReels: Boolean = false,
    val blockAdultSites: Boolean = false,
    val protection: Boolean = false,
    val heldPackages: Set<String> = emptySet(),
    val holdAlways: Boolean = false,
    val timerMode: TimerMode = TimerMode.TIMER,
    val longBreakMinutes: Int = 15,
    val focusTheme: String = "LAKE",
    /** True after the first-launch setup. */
    val onboarded: Boolean = false,
    /** Answers from the setup questions. */
    val purpose: String = "",
    val distractions: Set<String> = emptySet(),
    /** Streak freezes the user holds. One is used up for each missed day. */
    val streakFreezes: Int = 1,
    /** Days (ISO dates) that a streak freeze covered. */
    val frozenDays: Set<String> = emptySet(),
    /** The streak week count that last earned a freeze, so each week pays once. */
    val freezeWeeksRewarded: Int = 0,
    val goalDays: Int = 0b1111111,
    val allowFirstShort: Boolean = false,
    val contentOnlyDuringFocus: Boolean = false,
    val siteAllowList: Boolean = false,
    val youtubeStudyMode: Boolean = false,
    val allowedYoutubeChannels: Set<String> = emptySet(),
    val blockYoutubeHome: Boolean = false,
    val blockMultiWindow: Boolean = false,
    val pauseBlocksUntil: Long = 0,
    val emergencyPassesPerDay: Int = 3,
    /** Local minutes after midnight, written as decimal strings. */
    val notificationDeliveryTimes: Set<String> = emptySet(),
    val productivePackages: Set<String> = emptySet(),
    /** Equipped item ids. The column name pebbleItems is the Stillpoint name and stays so old backups restore. */
    val pebbleItems: Set<String> = emptySet(),
    val notifyFocusEvents: Boolean = true,
    val notifyPlanReminders: Boolean = true,
    val notifyInboxSummaries: Boolean = true,
    val petTapCount: Int = 0,
    val themeMode: String = "SYSTEM",
    /** SYSTEM follows the phone. H12 uses AM and PM. H24 uses a 24-hour clock. */
    val clockFormat: String = "SYSTEM",
    val autoUpdateChecks: Boolean = true,
    /** Last calendar milestone that earned a freeze. Clock changes cannot replay it. */
    val freezeRewardedThrough: String = "",
    /** Asks Android 16 and later to show the running focus timer as a Live Update in the status bar. */
    val liveFocusTimer: Boolean = true,
)

/**
 * The running focus session. The row exists only while a session runs.
 * [focusedMillisBefore] is the focus time of the rounds that already ended.
 */
@Entity
data class ActiveFocus(
    @PrimaryKey val id: Int = 0,
    val startedAt: Long,
    val phase: FocusPhase,
    val phaseStartedAt: Long,
    val phaseEndsAt: Long,
    val round: Int,
    val rounds: Int,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val focusedMillisBefore: Long = 0,
    val packages: Set<String>,
    val mode: BlockMode,
    val strict: Boolean,
    val lockHome: Boolean,
    val sound: FocusSound,
    val tag: String = "",
    val timerMode: TimerMode = TimerMode.TIMER,
    /** When the user paused, or 0 while the timer runs. A pause works like a break. */
    val pausedAt: Long = 0,
    val longBreakMinutes: Int = 15,
    val theme: String = "LAKE",
    val goalMinutes: Int = 120,
    val phaseElapsedMillis: Long = 0,
    val phaseAnchorElapsed: Long = -1,
    val bootCount: Int = -1,
    val rewardDay: String = "",
    val rewardStartHour: Int = -1,
) {
    val running: Boolean get() = pausedAt == 0L
}

/** A temporary daily-limit pass. Database storage keeps its count across process restarts. */
@Entity(primaryKeys = ["day", "packageName"])
data class LimitPass(val day: String, val packageName: String, val expiresAt: Long, val uses: Int)

/** Stored daily usage remains available after Android removes old usage events. */
@Serializable
@Entity
data class UsageDay(
    @PrimaryKey val day: String,
    val perApp: Map<String, Long>,
    val unlocks: Int,
    val heldCount: Int = 0,
    /** The first saved enabled budget for each app that day, in minutes. */
    val limitMinutes: Map<String, Long> = emptyMap(),
)
