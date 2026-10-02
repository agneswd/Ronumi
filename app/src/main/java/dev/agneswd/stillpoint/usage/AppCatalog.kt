package dev.agneswd.stillpoint.usage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

data class InstalledApp(val packageName: String, val label: String)

/** Looks up app names, icons and the system apps that blocks must never touch. */
class AppCatalog(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<String, Bitmap>()

    /** Apps with a launcher icon, sorted by name. This app is not in the list. */
    fun launchableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { InstalledApp(it, label(it)) }
            .sortedBy { it.label.lowercase() }
    }

    fun label(packageName: String): String = labels.getOrPut(packageName) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }
            .getOrDefault(packageName)
    }

    fun icon(packageName: String): Bitmap? = icons[packageName] ?: runCatching {
        pm.getApplicationIcon(packageName).toBitmap(96, 96).also { icons[packageName] = it }
    }.getOrNull()

    /** Home screen apps. Settings has a fallback home screen for boot, so it is left out. */
    fun launchers(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .filter { it != SETTINGS }
            .toSet()
    }

    /**
     * Apps that an "all apps except" block still allows. Without them the user
     * cannot answer a call, type, or reach this app to end the block.
     */
    fun essentials(): Set<String> {
        val imes = context.getSystemService(InputMethodManager::class.java)
            .enabledInputMethodList.map { it.packageName }
        val dialer = context.getSystemService(TelecomManager::class.java).defaultDialerPackage
        return launchers() + imes + listOfNotNull(dialer) + setOf(
            context.packageName,
            UsageReader.SYSTEM_UI,
            SETTINGS,
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )
    }

    private companion object {
        const val SETTINGS = "com.android.settings"
    }
}
