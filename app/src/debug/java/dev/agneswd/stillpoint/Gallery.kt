package dev.agneswd.stillpoint

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.agneswd.stillpoint.ui.design.*

@Composable
fun Gallery(page: String) {
    when (page) {
        "pebble" -> FlowRow(Modifier.fillMaxWidth()) {
            Mood.entries.forEach { mood ->
                Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Pebble(mood, size = 100.dp)
                    Text(mood.name, style = MaterialTheme.typography.labelSmall, color = Sp.colors.textDim)
                }
            }
        }
        "art" -> {
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Flame(size = 48.dp); Flame(size = 48.dp, lit = false); XpBolt(size = 40.dp)
                DayPart.entries.forEach { DayPartIcon(it) }
            }
            ShortsScene()
            NotificationScene()
        }
        "art2" -> {
            StrictScene()
            StreakScene()
        }
        "scenes" -> FlowRow {
            FocusTheme.entries.forEach { t ->
                FocusBackdrop(t, Modifier.padding(4.dp).size(170.dp, 300.dp))
            }
        }
        "says" -> Column(Modifier.padding(16.dp)) {
            dev.agneswd.stillpoint.ui.PebbleSays("Pick a daily focus goal", Mood.THINK, Modifier.fillMaxWidth(), pebbleSize = 84.dp)
            dev.agneswd.stillpoint.ui.PebbleSays("Which apps steal your time? Pick all that fit, I will remember.", Mood.THINK, Modifier.fillMaxWidth(), pebbleSize = 84.dp)
            dev.agneswd.stillpoint.ui.PebbleSays("Hi there!", Mood.HAPPY, Modifier.fillMaxWidth(), pebbleSize = 96.dp)
            dev.agneswd.stillpoint.ui.Stepper("Focus length", 25, 5..240, 5, { "$it min" }) {}
        }
        "components" -> {
            ScreenTitle("Components")
            ChunkyButton("Continue", {}, Modifier.fillMaxWidth())
            ChunkyButton("Start focus", {}, Modifier.fillMaxWidth(), kind = ButtonKind.MINT)
            ChunkyButton("Maybe later", {}, Modifier.fillMaxWidth(), kind = ButtonKind.SECONDARY)
            ChunkyButton("Disabled", {}, Modifier.fillMaxWidth(), enabled = false)
            ChunkyCard(Modifier.fillMaxWidth(), onClick = {}) { Text("A card", style = MaterialTheme.typography.titleMedium) }
            ChunkyCard(Modifier.fillMaxWidth(), onClick = {}, selected = true) { Text("Selected card", style = MaterialTheme.typography.titleMedium) }
            ChunkyProgress(0.6f)
            Tag("+20 XP", Sp.colors.gold)
        }
    }
}
