package dev.agneswd.ronumi.consent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One sensitive access that needs its own in-app agreement. */
enum class ConsentKind(val key: String) {
    ACCESSIBILITY("accessibility"),
    USAGE("usage"),
    NOTIFICATION_ACCESS("notification_access"),
    APP_LIST("app_list"),
}

/**
 * The accepted version of each sensitive access.
 * A kind is granted only when the stored version equals [VERSION].
 * Decline stores nothing. These values stay out of backup.
 */
object Consents {
    const val VERSION = 1
    private const val PREFS = "consents"

    private val changes = MutableStateFlow(0)

    /** Increases after [accept] or [clear], so open screens can reload. */
    val revision: StateFlow<Int> = changes

    fun granted(context: Context, kind: ConsentKind): Boolean =
        prefs(context).getInt(kind.key, 0) == VERSION

    fun accept(context: Context, kind: ConsentKind) {
        prefs(context).edit().putInt(kind.key, VERSION).commit()
        changes.value++
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
        changes.value++
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
