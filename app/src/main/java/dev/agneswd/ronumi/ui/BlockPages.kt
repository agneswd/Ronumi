package dev.agneswd.ronumi.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.BlockedSite
import dev.agneswd.ronumi.data.Settings
import dev.agneswd.ronumi.data.settings
import dev.agneswd.ronumi.data.updateSettings
import dev.agneswd.ronumi.guard.PolicyActions
import dev.agneswd.ronumi.guard.Rules
import dev.agneswd.ronumi.guard.hostOf
import java.time.LocalDateTime
import dev.agneswd.ronumi.guard.minuteText
import dev.agneswd.ronumi.ui.design.ChunkyButton
import dev.agneswd.ronumi.ui.design.ChunkyCard
import dev.agneswd.ronumi.ui.design.Mood
import dev.agneswd.ronumi.ui.design.Sp
import dev.agneswd.ronumi.ui.design.appear
import kotlinx.coroutines.launch

/** The short-video apps that Stillpoint can close the feed of. */
data class ShortsApp(val pkg: String, @param:androidx.annotation.StringRes val nameRes: Int, val logo: Int, val get: (Settings) -> Boolean, val set: (Settings, Boolean) -> Settings)

val shortsApps = listOf(
    ShortsApp("com.google.android.youtube", R.string.blocks_youtube_shorts, R.drawable.logo_youtube, { it.blockYoutubeShorts }, { s, v -> s.copy(blockYoutubeShorts = v) }),
    ShortsApp("com.instagram.android", R.string.blocks_instagram_reels, R.drawable.logo_instagram, { it.blockInstagramReels }, { s, v -> s.copy(blockInstagramReels = v) }),
    ShortsApp("com.snapchat.android", R.string.blocks_snapchat_spotlight, R.drawable.logo_snapchat, { it.blockSnapchatSpotlight }, { s, v -> s.copy(blockSnapchatSpotlight = v) }),
    ShortsApp("com.facebook.katana", R.string.blocks_facebook_reels, R.drawable.logo_facebook, { it.blockFacebookReels }, { s, v -> s.copy(blockFacebookReels = v) }),
)

/**
 * The frame of a Blocks detail page: a top bar, Ronumi with a short line, then the rows.
 * The content gets the current settings and a function that saves a change.
 */
@Composable
private fun BlockPage(
    title: String,
    line: String,
    mood: Mood,
    onClose: () -> Unit,
    content: @Composable ColumnScope.(Settings, SettingsUpdate) -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val settings by app.dao.settings().collectAsState(null)
    val schedules by app.dao.schedules().collectAsState(emptyList())
    val focus by app.dao.activeFocusFlow().collectAsState(null)
    val update: SettingsUpdate = { change -> app.scope.launch { PolicyActions.changeBlocks(context) { app.dao.updateSettings(change) } } }
    Column(Modifier.fillMaxSize()) {
        TopBar(title, onClose)
        val s = settings ?: return@Column
        // This page can be open when a block starts. It locks like the Blocks tab.
        if (s.protection && Rules(schedules = schedules, focus = focus).locked(LocalDateTime.now())) {
            LockedNotice()
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            RonumiSays(line, mood, Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp), ronumiSize = 80.dp)
            content(s, update)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** A bordered panel that groups related rows. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    ChunkyCard(modifier.fillMaxWidth().padding(horizontal = ScreenPadding), contentPadding = 0.dp) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

/** Dim help text under a group. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim, modifier = modifier.padding(horizontal = ScreenPadding + 4.dp, vertical = 8.dp))
}

@Composable
fun ShortVideosPage(onClose: () -> Unit) {
    BlockPage(stringResource(R.string.blocks_short_videos), stringResource(R.string.blocks_shorts_description), Mood.GUARD, onClose) { s, update ->
        Group(Modifier.appear(0)) {
            shortsApps.forEach { item ->
                SwitchRow(stringResource(item.nameRes), null, item.get(s), leading = { AppIcon(item.pkg, name = stringResource(item.nameRes), logo = item.logo) }) { on -> update { item.set(it, on) } }
            }
        }
        Group(Modifier.padding(top = 12.dp).appear(60)) {
            SwitchRow(stringResource(R.string.blocks_allow_the_first_video), stringResource(R.string.blocks_first_video_description), s.allowFirstShort) { on ->
                update { it.copy(allowFirstShort = on) }
            }
            SwitchRow(stringResource(R.string.blocks_only_during_focus), stringResource(R.string.blocks_shorts_focus_scope), s.contentOnlyDuringFocus) { on ->
                update { it.copy(contentOnlyDuringFocus = on) }
            }
        }

        SectionTitle(stringResource(R.string.blocks_youtube))
        Group(Modifier.appear(120)) {
            SwitchRow(stringResource(R.string.blocks_hide_the_home_feed), stringResource(R.string.blocks_youtube_home_description), s.blockYoutubeHome) { on -> update { it.copy(blockYoutubeHome = on) } }
            SwitchRow(stringResource(R.string.blocks_study_mode), stringResource(R.string.blocks_study_description), s.youtubeStudyMode) { on -> update { it.copy(youtubeStudyMode = on) } }
        }
        if (s.youtubeStudyMode) {
            Column(Modifier.appear(0)) {
                SectionTitle(stringResource(R.string.blocks_allowed_channels))
                ChipList(s.allowedYoutubeChannels, empty = stringResource(R.string.blocks_channels_empty)) { channel ->
                    update { it.copy(allowedYoutubeChannels = it.allowedYoutubeChannels - channel) }
                }
                AddField(stringResource(R.string.blocks_channel_name_or_handle), KeyboardType.Text, clean = { it.trim().takeIf(String::isNotEmpty) }) { channel ->
                    update { it.copy(allowedYoutubeChannels = it.allowedYoutubeChannels + channel) }
                }
                Hint(stringResource(R.string.blocks_channel_input_help))
            }
        }
    }
}

@Composable
fun WebsitesPage(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val sites by app.dao.sites().collectAsState(emptyList())
    BlockPage(stringResource(R.string.blocks_websites), stringResource(R.string.blocks_websites_description), Mood.THINK, onClose) { s, update ->
        Group(Modifier.appear(0)) {
            SwitchRow(stringResource(R.string.blocks_block_adult_sites), stringResource(R.string.blocks_adult_filter_description), s.blockAdultSites) { on ->
                update { it.copy(blockAdultSites = on) }
            }
            SwitchRow(stringResource(R.string.blocks_only_during_focus), stringResource(R.string.blocks_websites_focus_scope), s.contentOnlyDuringFocus) { on ->
                update { it.copy(contentOnlyDuringFocus = on) }
            }
        }

        SectionTitle(stringResource(R.string.blocks_your_list))
        Row(Modifier.padding(horizontal = ScreenPadding).appear(60), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceButton(stringResource(R.string.blocks_block_these), !s.siteAllowList, Modifier.weight(1f)) { update { it.copy(siteAllowList = false) } }
            ChoiceButton(stringResource(R.string.blocks_allow_only_these), s.siteAllowList, Modifier.weight(1f)) { update { it.copy(siteAllowList = true) } }
        }
        Hint(
            if (s.siteAllowList) stringResource(R.string.blocks_allowlist_description)
            else stringResource(R.string.blocks_denylist_description),
        )
        AddField(stringResource(if (s.siteAllowList) R.string.blocks_allowlist_input_hint else R.string.blocks_denylist_input_hint, stringResource(R.string.blocks_example_domain)), KeyboardType.Uri, clean = ::hostOf) { domain ->
            app.scope.launch { PolicyActions.changeBlocks(context) { app.dao.addSite(BlockedSite(domain)) } }
        }
        ChipList(sites.map { it.domain }.toSet(), empty = if (s.siteAllowList) stringResource(R.string.blocks_allowlist_empty) else stringResource(R.string.blocks_no_sites_yet)) { domain ->
            sites.firstOrNull { it.domain == domain }?.let { app.scope.launch { PolicyActions.changeBlocks(context) { app.dao.deleteSite(it) } } }
        }
    }
}

@Composable
fun NotificationsPage(navigator: Navigator, onClose: () -> Unit) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val app = context.app
    val held by app.dao.held().collectAsState(emptyList())
    val access = rememberAccess()
    var picking by remember { mutableStateOf(false) }
    val use24 = rememberUse24Hour()
    BlockPage(stringResource(R.string.blocks_notifications), stringResource(R.string.blocks_inbox_description), Mood.CALM, onClose) { s, update ->
        if (!access.listener) {
            Group(Modifier.padding(bottom = 12.dp)) {
                ListRow(
                    stringResource(R.string.blocks_allow_notification_access),
                    stringResource(R.string.blocks_listener_description),
                    onClick = { navigator.push(Route.Settings) },
                    leading = { IconTile(R.drawable.ic_bell, Sp.colors.danger) },
                ) { Chevron() }
            }
        }
        Group(Modifier.appear(0)) {
            ListRow(
                stringResource(R.string.blocks_hold_notifications_from),
                if (s.heldPackages.isEmpty()) stringResource(R.string.blocks_no_apps) else appCount(s.heldPackages.size),
                onClick = {
                    navigator.push(Route.PickApps(resources.getString(R.string.blocks_hold_notifications_from), s.heldPackages, single = false) { picked -> update { it.copy(heldPackages = picked) } })
                },
            ) { AppSelectionPreview(s.heldPackages) }
            SwitchRow(stringResource(R.string.blocks_hold_all_day), if (s.holdAlways) stringResource(R.string.blocks_held_at_all_times) else stringResource(R.string.blocks_hold_focus_description), s.holdAlways) { on ->
                update { it.copy(holdAlways = on) }
            }
            ListRow(
                stringResource(R.string.blocks_inbox),
                if (held.isEmpty()) stringResource(R.string.blocks_nothing_waiting) else pluralStringResource(R.plurals.blocks_waiting, held.size, held.size),
                onClick = { navigator.push(Route.Held) },
            ) { Chevron() }
        }

        SectionTitle(stringResource(R.string.blocks_delivery_times))
        val times = s.notificationDeliveryTimes.mapNotNull(String::toIntOrNull).sorted()
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).appear(60),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            times.forEach { minute ->
                Chip(minuteText(minute, use24)) { update { it.copy(notificationDeliveryTimes = it.notificationDeliveryTimes - minute.toString()) } }
            }
            AddChip(stringResource(R.string.blocks_add_time)) { picking = true }
        }
        Hint(
            if (times.isEmpty()) stringResource(R.string.blocks_delivery_empty_description)
            else stringResource(R.string.blocks_delivery_times_description),
        )
    }
    if (picking) {
        TimeDialog(12 * 60, onDismiss = { picking = false }) { minute ->
            app.scope.launch { PolicyActions.changeBlocks(context) { app.dao.updateSettings { it.copy(notificationDeliveryTimes = it.notificationDeliveryTimes + minute.toString()) } } }
            picking = false
        }
    }
}

@Composable
fun StrictPage(onClose: () -> Unit) {
    BlockPage(stringResource(R.string.blocks_strict_mode), stringResource(R.string.blocks_protection_description), Mood.STRICT, onClose) { s, update ->
        Group(Modifier.appear(0)) {
            SwitchRow(
                stringResource(R.string.blocks_protection_switch),
                stringResource(R.string.blocks_protection_switch_description),
                s.protection,
            ) { on -> update { it.copy(protection = on) } }
            SwitchRow(
                stringResource(R.string.blocks_block_split_screen),
                stringResource(R.string.blocks_windows_description),
                s.blockMultiWindow,
            ) { on -> update { it.copy(blockMultiWindow = on) } }
        }

        SectionTitle(stringResource(R.string.blocks_extra_time))
        Group(Modifier.appear(60)) {
            Stepper(stringResource(R.string.blocks_passes_each_day), s.emergencyPassesPerDay, 0..10, 1, { if (it == 0) stringResource(R.string.blocks_none) else stringResource(R.string.blocks_pass_count, it) }) { value ->
                update { it.copy(emergencyPassesPerDay = value) }
            }
        }
        Hint(stringResource(R.string.blocks_pass_help))
    }
}

/** Removable chips, or a dim line when there are none. */
@Composable
private fun ChipList(values: Set<String>, empty: String, onRemove: (String) -> Unit) {
    if (values.isEmpty()) {
        Hint(empty)
        return
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.sorted().forEach { value -> Chip(value) { onRemove(value) } }
    }
}

/** A value with a remove button. */
@Composable
fun Chip(text: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Sp.colors.brandSoft)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = Sp.colors.brand)
        Spacer(Modifier.width(2.dp))
        Icon(
            painterResource(R.drawable.ic_close),
            stringResource(R.string.blocks_remove, text),
            tint = Sp.colors.brand,
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onRemove).padding(8.dp),
        )
    }
}

/** A dashed-looking chip that adds a value. */
@Composable
fun AddChip(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(2.dp, Sp.colors.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_plus), null, tint = Sp.colors.brand, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = Sp.colors.brand)
    }
}

/** A text field with an Add button. [clean] turns the text into a value, or null when it is not valid. */
@Composable
private fun AddField(placeholder: String, keyboard: KeyboardType, clean: (String) -> String?, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val value = clean(text)
    val submit = {
        if (value != null) {
            onAdd(value)
            text = ""
        }
    }
    Row(Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            text,
            { text = it },
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Sp.colors.border, focusedBorderColor = Sp.colors.brand),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f).trackTextFieldFocus(),
        )
        Spacer(Modifier.width(8.dp))
        ChunkyButton(stringResource(R.string.blocks_add), submit, enabled = value != null, height = 52.dp)
    }
}
