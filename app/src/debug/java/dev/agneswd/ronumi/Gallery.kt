package dev.agneswd.ronumi

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
import dev.agneswd.ronumi.ui.design.*

@Composable
fun Gallery(page: String) {
    when (page) {
        "ronumi" -> FlowRow(Modifier.fillMaxWidth()) {
            Mood.entries.forEach { mood ->
                Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ronumi(mood, size = 100.dp)
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
        // The Plus collection on Ronumi in four moods. Debug builds show it before the Plus gates do.
        "plus" -> dev.agneswd.ronumi.game.RonumiStyles.items.filter { it.plus }.forEach { item ->
            Text(item.id, style = MaterialTheme.typography.labelSmall, color = Sp.colors.textDim)
            androidx.compose.foundation.layout.Row {
                listOf(Mood.IDLE, Mood.HAPPY, Mood.CALM, Mood.CELEBRATE).forEach { mood ->
                    Ronumi(mood, size = 76.dp, style = setOf(item.id), animated = false)
                }
                Ronumi(Mood.IDLE, size = 36.dp, style = setOf(item.id), animated = false)
            }
        }
        "scenes" -> FlowRow {
            FocusTheme.entries.forEach { t ->
                FocusBackdrop(t, Modifier.padding(4.dp).size(170.dp, 300.dp))
            }
        }
        "says" -> Column(Modifier.padding(16.dp)) {
            dev.agneswd.ronumi.ui.RonumiSays("Pick a daily focus goal", Mood.THINK, Modifier.fillMaxWidth(), ronumiSize = 84.dp)
            dev.agneswd.ronumi.ui.RonumiSays("Which apps steal your time? Pick all that fit, I will remember.", Mood.THINK, Modifier.fillMaxWidth(), ronumiSize = 84.dp)
            dev.agneswd.ronumi.ui.RonumiSays("Hi there!", Mood.HAPPY, Modifier.fillMaxWidth(), ronumiSize = 96.dp)
            dev.agneswd.ronumi.ui.Stepper("Focus length", 25, 5..240, 5, { "$it min" }) {}
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
