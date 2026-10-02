package dev.agneswd.stillpoint.ui

import androidx.compose.foundation.layout.size
import dev.agneswd.stillpoint.ui.design.ChunkyProgress
import dev.agneswd.stillpoint.R
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.currentSettings
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.data.updateSettings
import dev.agneswd.stillpoint.game.GameState
import dev.agneswd.stillpoint.game.PebbleSlot
import dev.agneswd.stillpoint.game.PebbleStyles
import dev.agneswd.stillpoint.game.gameState
import dev.agneswd.stillpoint.ui.design.ChunkyButton
import dev.agneswd.stillpoint.ui.design.companionMood
import dev.agneswd.stillpoint.ui.design.Mood
import dev.agneswd.stillpoint.ui.design.Pebble
import dev.agneswd.stillpoint.ui.design.Sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WardrobeScreen(game: GameState, onClose: () -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.app
    val settings by app.dao.settings().collectAsState(null)
    val saved = settings ?: return
    val catalog = PebbleStyles.visibleItems(saved.petTapCount)
    val worn = PebbleStyles.resolve(saved.pebbleItems, game.level.number, saved.petTapCount)
    var slot by rememberSaveable { mutableStateOf(PebbleSlot.OUTFIT) }
    var previewId by rememberSaveable(slot, worn, catalog.size) {
        mutableStateOf(catalog.firstOrNull { it.slot == slot && it.id in worn }?.id)
    }
    var saving by androidx.compose.runtime.remember { mutableStateOf(false) }
    val selected = catalog.firstOrNull { it.id == previewId }
    val slotIds = catalog.filter { it.slot == slot }.mapTo(mutableSetOf()) { it.id }
    val preview = (worn - slotIds) + listOfNotNull(previewId)
    val available = selected == null || PebbleStyles.isUnlocked(selected, game.level.number, saved.petTapCount)
    val equipped = preview == worn
    val unlocked = catalog.count { PebbleStyles.isUnlocked(it, game.level.number, saved.petTapCount) }
    val nextLevel = catalog.filter { it.level > game.level.number }.minOfOrNull { it.level }

    fun wear(reset: Boolean = false) {
        if (saving) return
        saving = true
        val requested = selected
        val requestedSlot = slot
        app.scope.launch {
            val result = runCatching {
                // Restore and equip share one transaction, so old unlock state cannot grant an item.
                app.database.withTransaction {
                    val fresh = app.dao.currentSettings()
                    val level = gameState(app.dao.allSessions(), fresh).level.number
                    if (!reset && requested != null && !PebbleStyles.isUnlocked(requested, level, fresh.petTapCount)) {
                        if (requested.minimumTaps > fresh.petTapCount) "This item is still hidden." else "This item unlocks at level ${requested.level}."
                    } else {
                        val current = PebbleStyles.resolve(fresh.pebbleItems, level, fresh.petTapCount)
                        val remove = PebbleStyles.items.filter { it.slot == requestedSlot }.map { it.id }.toSet()
                        val updated = if (reset) emptySet() else (current - remove) + listOfNotNull(requested?.id)
                        app.dao.updateSettings { it.copy(pebbleItems = updated) }
                        null
                    }
                }
            }
            if (result.isSuccess && result.getOrNull() == null) dev.agneswd.stillpoint.widget.Widgets.refresh(context)
            withContext(Dispatchers.Main) {
                saving = false
                val message = result.getOrElse { "Could not save Pebble's look. Try again." }
                if (message != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Pebble's wardrobe", onClose)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
            item(key = "preview") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pebble(game.companionMood(), size = 126.dp, style = preview)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(selected?.name ?: originalLabel(slot), style = MaterialTheme.typography.titleLarge, color = Sp.colors.text)
                        Text(
                            when {
                                !available -> "Preview - level ${selected.level} required"
                                equipped -> "Wearing now"
                                else -> "Ready to wear"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Sp.colors.textDim,
                        )
                        ChunkyButton(
                            when { saving -> "Saving..."; equipped -> "Equipped"; !available -> "Locked"; else -> "Wear" },
                            onClick = { wear() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = available && !equipped && !saving,
                            height = 44.dp,
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Level ${game.level.number}", Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                        Text("$unlocked / ${catalog.size} unlocked",
                            style = MaterialTheme.typography.labelMedium, color = Sp.colors.textDim)
                    }
                    if (nextLevel != null) {
                        ChunkyProgress(game.level.fraction, Modifier.fillMaxWidth(), height = 12.dp)
                        Text("More items at level $nextLevel",
                            style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PebbleSlot.entries.forEach { category ->
                        Text(
                            slotLabel(category),
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (slot == category) Sp.colors.brandSoft else Sp.colors.background)
                                .selectable(slot == category, role = Role.Tab) { slot = category }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (slot == category) Sp.colors.brand else Sp.colors.textDim,
                        )
                    }
                }
            }
            item(key = "original-${slot.name}") {
                WardrobeItemRow(originalLabel(slot), if (worn.none { it in slotIds }) "Equipped" else "Always available", previewId == null, worn - slotIds) {
                    previewId = null
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
            items(catalog.filter { it.slot == slot }, key = { it.id }) { item ->
                WardrobeItemRow(
                    item.name,
                    when {
                        item.id in worn -> "Equipped"
                        !PebbleStyles.isUnlocked(item, game.level.number, saved.petTapCount) -> "Unlocks at level ${item.level}"
                        item.minimumTaps > 0 -> "Secret discovered"
                        else -> "Ready to wear"
                    },
                    previewId == item.id,
                    (worn - slotIds) + item.id,
                    locked = !PebbleStyles.isUnlocked(item, game.level.number, saved.petTapCount),
                ) {
                    previewId = item.id
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
            item {
                Text(
                    "Try any item above. Focus sessions and daily quests earn XP. Unlocked items cost no XP and can be worn together.",
                    Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim,
                )
                Text(
                    "Restore original look",
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = worn.isNotEmpty() && !saving) { wear(reset = true) }.padding(12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (worn.isNotEmpty()) Sp.colors.brand else Sp.colors.textDim,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun WardrobeItemRow(name: String, detail: String, selected: Boolean, look: Set<String>, locked: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)).selectable(selected, role = Role.RadioButton, onClick = onClick)
            .background(if (selected) Sp.colors.brandSoft else Sp.colors.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pebble(Mood.IDLE, size = 58.dp, style = look)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
        }
        when {
            selected -> Text("Selected", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, color = Sp.colors.brand)
            locked -> Icon(painterResource(R.drawable.ic_lock), "Locked", Modifier.size(20.dp), tint = Sp.colors.textDim)
        }
    }
}

private fun slotLabel(slot: PebbleSlot) = when (slot) {
    PebbleSlot.COLOR -> "Colors"
    PebbleSlot.OUTFIT -> "Clothes"
    PebbleSlot.HAT -> "Hats"
    PebbleSlot.ACCESSORY -> "Extras"
}

private fun originalLabel(slot: PebbleSlot) = when (slot) {
    PebbleSlot.COLOR -> "Original purple"
    PebbleSlot.OUTFIT -> "No clothes"
    PebbleSlot.HAT -> "No hat"
    PebbleSlot.ACCESSORY -> "No extra"
}
