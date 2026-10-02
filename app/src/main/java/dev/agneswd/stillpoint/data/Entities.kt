package dev.agneswd.stillpoint.data

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

enum class FocusSound { OFF, WHITE, PINK, BROWN }

enum class FocusPhase { FOCUS, BREAK }

/** A daily time budget for one app. The day resets at local midnight. */
@Serializable
@Entity
data class AppLimit(
    @PrimaryKey val packageName: String,
    val minutesPerDay: Int,
    val mode: LimitMode = LimitMode.GENTLE,
    val enabled: Boolean = true,
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
)

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
)

/** A notification that the listener removed and kept for later. */
@Entity
data class HeldNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
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
)
