package dev.agneswd.stillpoint.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.guard.uses24HourClock


/** The clock format for the open screen. It updates when the phone setting changes. */
@Composable
fun rememberUse24Hour(): Boolean {
    val context = LocalContext.current
    val stored by context.app.dao.settings().collectAsState(initial = null)
    var system24 by remember { mutableStateOf(context.uses24HourClock("SYSTEM")) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                system24 = context.uses24HourClock("SYSTEM")
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return when (stored?.clockFormat ?: "SYSTEM") {
        "H12" -> false
        "H24" -> true
        else -> system24
    }
}
