package dev.agneswd.stillpoint.plus

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

enum class PlusFeature {
    UNLIMITED_FOCUS_APPS, UNLIMITED_SCHEDULES, UNLIMITED_APP_LIMITS, WEBSITES,
    SHORT_VIDEOS, STRICT_MODE, YOUTUBE_STUDY, NOTIFICATION_INBOX, FULL_REPORTS,
    PLUS_WARDROBE, PLUS_SCENES,
}

object FreeLimits {
    const val FOCUS_APPS = 5
    const val SCHEDULES = 1
    const val APP_LIMITS = 1
    const val REPORT_DAYS = 7
}

enum class Entitlement { UNLOCKED, LOCKED, PENDING }
enum class PlusStatus { IDLE, BUSY, CANCELED, UNAVAILABLE, ERROR }

data class PlusState(
    val entitlement: Entitlement,
    val price: String? = null,
    val status: PlusStatus = PlusStatus.IDLE,
)

/** Process-wide access through context.app.plus. Collect state in Compose; guards can read state.value. */
interface Plus {
    val state: StateFlow<PlusState>
    fun has(feature: PlusFeature): Boolean = state.value.entitlement == Entitlement.UNLOCKED
    /** Starts the store flow. Completion, cancellation, and errors arrive through state. */
    fun purchase(activity: Activity)
    /** Refreshes owned purchases. A failed query keeps the last confirmed entitlement. */
    fun restore()
}
