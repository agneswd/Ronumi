package dev.agneswd.ronumi.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.size
import dev.agneswd.ronumi.ui.design.ChunkyProgress
import dev.agneswd.ronumi.R
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
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.currentSettings
import dev.agneswd.ronumi.data.settings
import dev.agneswd.ronumi.data.updateSettings
import dev.agneswd.ronumi.game.GameState
import dev.agneswd.ronumi.game.RonumiSlot
import dev.agneswd.ronumi.game.RonumiStyles
import dev.agneswd.ronumi.game.gameState
import dev.agneswd.ronumi.ui.design.ChunkyButton
import dev.agneswd.ronumi.ui.design.companionMood
import dev.agneswd.ronumi.ui.design.Mood
import dev.agneswd.ronumi.ui.design.Ronumi
import dev.agneswd.ronumi.ui.design.Sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WardrobeScreen(game: GameState, onClose: () -> Unit, onPlus: () -> Unit = {}) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val resources = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val app = context.app
    val settings by app.dao.settings().collectAsState(null)
    val hasPlus = rememberHasPlus()
    val saved = settings ?: return
    val catalog = RonumiStyles.visibleItems(saved.petTapCount)
    val worn = RonumiStyles.resolve(saved.pebbleItems, game.level.number, saved.petTapCount, hasPlus)
    var slot by rememberSaveable { mutableStateOf(RonumiSlot.OUTFIT) }
    var previewId by rememberSaveable(slot, worn, catalog.size) {
        mutableStateOf(catalog.firstOrNull { it.slot == slot && it.id in worn }?.id)
    }
    var saving by androidx.compose.runtime.remember { mutableStateOf(false) }
    val selected = catalog.firstOrNull { it.id == previewId }
    val slotIds = catalog.filter { it.slot == slot }.mapTo(mutableSetOf()) { it.id }
    val preview = (worn - slotIds) + listOfNotNull(previewId)
    val available = selected == null || RonumiStyles.isUnlocked(selected, game.level.number, saved.petTapCount, hasPlus)
    val equipped = preview == worn
    val unlocked = catalog.count { RonumiStyles.isUnlocked(it, game.level.number, saved.petTapCount, hasPlus) }
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
                    val plus = app.plus.has(dev.agneswd.ronumi.plus.PlusFeature.PLUS_WARDROBE)
                    if (!reset && requested != null && !RonumiStyles.isUnlocked(requested, level, fresh.petTapCount, plus)) {
                        if (requested.minimumTaps > fresh.petTapCount) resources.getString(R.string.wardrobe_hidden_message) else resources.getString(R.string.wardrobe_locked_message, requested.level)
                    } else {
                        val current = RonumiStyles.resolve(fresh.pebbleItems, level, fresh.petTapCount, plus)
                        val remove = RonumiStyles.items.filter { it.slot == requestedSlot }.map { it.id }.toSet()
                        val updated = if (reset) emptySet() else (current - remove) + listOfNotNull(requested?.id)
                        app.dao.updateSettings { it.copy(pebbleItems = updated) }
                        null
                    }
                }
            }
            if (result.isSuccess && result.getOrNull() == null) dev.agneswd.ronumi.widget.Widgets.refresh(context)
            withContext(Dispatchers.Main) {
                saving = false
                val message = result.getOrElse { resources.getString(R.string.wardrobe_save_failure) }
                if (message != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.wardrobe_title), onClose)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
            item(key = "preview") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ronumi(game.companionMood(), size = 126.dp, style = preview)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(selected?.let { stringResource(it.nameRes) } ?: originalLabel(slot), style = MaterialTheme.typography.titleLarge, color = Sp.colors.text)
                        Text(
                            when {
                                !available && selected.plus -> stringResource(R.string.plus_collection_title)
                                !available -> stringResource(R.string.wardrobe_preview_level_required, selected.level)
                                equipped -> stringResource(R.string.wardrobe_wearing_now)
                                else -> stringResource(R.string.wardrobe_ready_to_wear)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Sp.colors.textDim,
                        )
                        // A Plus item previews like a level item. Its button opens the paywall instead of a lock.
                        val plusLocked = !available && selected.plus
                        ChunkyButton(
                            when { saving -> stringResource(R.string.wardrobe_saving); equipped -> stringResource(R.string.wardrobe_equipped); plusLocked -> stringResource(R.string.plus_unlock_with); !available -> stringResource(R.string.wardrobe_locked); else -> stringResource(R.string.wardrobe_wear) },
                            onClick = { if (plusLocked) onPlus() else wear() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = (available || plusLocked) && !equipped && !saving,
                            height = 44.dp,
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.wardrobe_level, game.level.number), Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                        Text(pluralStringResource(R.plurals.wardrobe_unlocked, unlocked, unlocked, catalog.size),
                            style = MaterialTheme.typography.labelMedium, color = Sp.colors.textDim)
                    }
                    if (nextLevel != null) {
                        ChunkyProgress(game.level.fraction, Modifier.fillMaxWidth(), height = 12.dp)
                        Text(stringResource(R.string.wardrobe_more_items_at_level, nextLevel),
                            style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RonumiSlot.entries.forEach { category ->
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
                WardrobeItemRow(originalLabel(slot), if (worn.none { it in slotIds }) stringResource(R.string.wardrobe_equipped) else stringResource(R.string.wardrobe_always_available), previewId == null, worn - slotIds) {
                    previewId = null
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
            catalog.filter { it.slot == slot }.forEach { item ->
                // The Plus collection follows the level items under its own title. GitHub builds show no Plus label.
                if (item.plus && dev.agneswd.ronumi.Distribution.usesBilling && catalog.first { it.slot == slot && it.plus } == item) {
                    item(key = "plus-title-${slot.name}") { SectionTitle(stringResource(R.string.plus_collection_title)) }
                }
                item(key = item.id) {
                val plusLocked = item.plus && !hasPlus
                WardrobeItemRow(
                    stringResource(item.nameRes),
                    when {
                        item.id in worn -> stringResource(R.string.wardrobe_equipped)
                        plusLocked -> ""
                        !RonumiStyles.isUnlocked(item, game.level.number, saved.petTapCount, hasPlus) -> stringResource(R.string.wardrobe_unlocks_at_level, item.level)
                        item.minimumTaps > 0 -> stringResource(R.string.wardrobe_secret_discovered)
                        else -> stringResource(R.string.wardrobe_ready_to_wear)
                    },
                    previewId == item.id,
                    (worn - slotIds) + item.id,
                    locked = !RonumiStyles.isUnlocked(item, game.level.number, saved.petTapCount, hasPlus),
                    plusLocked = plusLocked,
                ) {
                    previewId = item.id
                    scope.launch { listState.animateScrollToItem(0) }
                }
                }
            }
            item {
                Text(
                    stringResource(R.string.wardrobe_unlock_help),
                    Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim,
                )
                dev.agneswd.ronumi.Distribution.RewardedXpOffer()
                Text(
                    stringResource(R.string.wardrobe_restore_original_look),
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
private fun WardrobeItemRow(name: String, detail: String, selected: Boolean, look: Set<String>, locked: Boolean = false, plusLocked: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)).selectable(selected, role = Role.RadioButton, onClick = onClick)
            .background(if (selected) Sp.colors.brandSoft else Sp.colors.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ronumi(Mood.IDLE, size = 58.dp, style = look)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
            if (plusLocked) PlusChip() else Text(detail, style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
        }
        when {
            selected -> Text(stringResource(R.string.wardrobe_selected), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, color = Sp.colors.brand)
            locked && !plusLocked -> Icon(painterResource(R.drawable.ic_lock), stringResource(R.string.wardrobe_locked), Modifier.size(20.dp), tint = Sp.colors.textDim)
        }
    }
}

@Composable
private fun slotLabel(slot: RonumiSlot) = when (slot) {
    RonumiSlot.COLOR -> stringResource(R.string.wardrobe_colors)
    RonumiSlot.OUTFIT -> stringResource(R.string.wardrobe_clothes)
    RonumiSlot.HAT -> stringResource(R.string.wardrobe_hats)
    RonumiSlot.ACCESSORY -> stringResource(R.string.wardrobe_extras)
}

@Composable
private fun originalLabel(slot: RonumiSlot) = when (slot) {
    RonumiSlot.COLOR -> stringResource(R.string.wardrobe_original_purple)
    RonumiSlot.OUTFIT -> stringResource(R.string.wardrobe_no_clothes)
    RonumiSlot.HAT -> stringResource(R.string.wardrobe_no_hat)
    RonumiSlot.ACCESSORY -> stringResource(R.string.wardrobe_no_extra)
}
