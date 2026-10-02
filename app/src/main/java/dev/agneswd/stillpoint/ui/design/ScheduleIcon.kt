package dev.agneswd.stillpoint.ui.design

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.agneswd.stillpoint.R

/** Stable IDs match the saved schedule and backup values. */
val scheduleIconChoices = listOf(
    "auto" to "Automatic", "focus" to "Focus", "study" to "Study", "work" to "Work",
    "sleep" to "Sleep", "exercise" to "Exercise", "coffee" to "Coffee", "home" to "Home",
    "music" to "Music", "book" to "Reading",
)

@Composable
fun ScheduleIcon(id: String, startMinute: Int, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val resource = when (id) {
        "focus" -> R.drawable.ic_activity_focus
        "study" -> R.drawable.ic_activity_study
        "work" -> R.drawable.ic_activity_work
        "sleep" -> R.drawable.ic_activity_sleep
        "exercise" -> R.drawable.ic_activity_exercise
        "coffee" -> R.drawable.ic_activity_coffee
        "home" -> R.drawable.ic_activity_home
        "music" -> R.drawable.ic_music
        "book" -> R.drawable.ic_activity_book
        else -> null
    }
    if (resource == null) {
        val part = when {
            startMinute < 12 * 60 -> DayPart.MORNING
            startMinute < 17 * 60 -> DayPart.AFTERNOON
            startMinute < 20 * 60 -> DayPart.EVENING
            else -> DayPart.NIGHT
        }
        DayPartIcon(part, modifier, size)
    } else {
        Icon(painterResource(resource), contentDescription = null, modifier = modifier.size(size), tint = Sp.colors.brand)
    }
}
