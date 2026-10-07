package dev.agneswd.ronumi.ui

import dev.agneswd.ronumi.app
import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/** Display text carried by computed models. Never saved in settings or backups. */
sealed interface ResourceText {
    data class Literal(val value: String) : ResourceText
    data class StringValue(@param:StringRes val id: Int, val arguments: List<Any> = emptyList()) : ResourceText
    data class Quantity(@param:PluralsRes val id: Int, val count: Int, val arguments: List<Any> = emptyList()) : ResourceText
}

fun textResource(@StringRes id: Int, vararg arguments: Any): ResourceText =
    ResourceText.StringValue(id, arguments.toList())

fun quantityResource(@PluralsRes id: Int, count: Int, vararg arguments: Any): ResourceText =
    ResourceText.Quantity(id, count, arguments.toList())

// Resolves through the app, so text from services and receivers uses the app language. See RonumiApp.getResources.
fun ResourceText.resolve(context: Context): String = when (this) {
    is ResourceText.Literal -> value
    is ResourceText.StringValue -> context.app.getString(id, *arguments.map { it.localizedArgument(context) }.toTypedArray())
    is ResourceText.Quantity -> context.app.resources.getQuantityString(id, count, *arguments.map { it.localizedArgument(context) }.toTypedArray())
}

@Composable
fun ResourceText.localized(): String = when (this) {
    is ResourceText.Literal -> value
    is ResourceText.StringValue -> stringResource(id, *arguments.map { if (it is ResourceText) it.localized() else it }.toTypedArray())
    is ResourceText.Quantity -> pluralStringResource(id, count, *arguments.map { if (it is ResourceText) it.localized() else it }.toTypedArray())
}

private fun Any.localizedArgument(context: Context): Any = when (this) {
    is ResourceText -> resolve(context)
    else -> this
}

@Composable
fun formatMinutes(minutes: Int): String = dev.agneswd.ronumi.guard.minutesResource(minutes).localized()

@Composable
fun formatDuration(millis: Long): String = formatMinutes((millis / 60_000).toInt())

/** Built-in names remain canonical in Room and backups. User-entered names pass through unchanged. */
fun String.nameResource(): ResourceText {
    val part = dev.agneswd.ronumi.ui.design.DayPart.entries.firstOrNull { it.scheduleId == this }
    return when {
        part != null -> textResource(part.scheduleNameRes)
        this == "First focus" -> textResource(dev.agneswd.ronumi.R.string.focus_first_session_tag)
        else -> ResourceText.Literal(this)
    }
}

fun String.displayName(context: Context): String = nameResource().resolve(context)

@Composable
fun String.displayName(): String = nameResource().localized()
