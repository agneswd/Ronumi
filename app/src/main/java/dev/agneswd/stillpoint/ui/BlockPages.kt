package dev.agneswd.stillpoint.ui

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
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.BlockedSite
import dev.agneswd.stillpoint.data.Settings
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.data.updateSettings
import dev.agneswd.stillpoint.guard.PolicyActions
import dev.agneswd.stillpoint.guard.Rules
import dev.agneswd.stillpoint.guard.hostOf
import java.time.LocalDateTime
import dev.agneswd.stillpoint.guard.minuteText
import dev.agneswd.stillpoint.ui.design.ChunkyButton
import dev.agneswd.stillpoint.ui.design.ChunkyCard
import dev.agneswd.stillpoint.ui.design.Mood
import dev.agneswd.stillpoint.ui.design.Sp
import dev.agneswd.stillpoint.ui.design.appear
import kotlinx.coroutines.launch

/** The short-video apps that Stillpoint can close the feed of. */
data class ShortsApp(val pkg: String, val name: String, val logo: Int, val get: (Settings) -> Boolean, val set: (Settings, Boolean) -> Settings)

val shortsApps = listOf(
    ShortsApp("com.google.android.youtube", "YouTube Shorts", R.drawable.logo_youtube, { it.blockYoutubeShorts }, { s, v -> s.copy(blockYoutubeShorts = v) }),
    ShortsApp("com.instagram.android", "Instagram Reels", R.drawable.logo_instagram, { it.blockInstagramReels }, { s, v -> s.copy(blockInstagramReels = v) }),
    ShortsApp("com.snapchat.android", "Snapchat Spotlight", R.drawable.logo_snapchat, { it.blockSnapchatSpotlight }, { s, v -> s.copy(blockSnapchatSpotlight = v) }),
    ShortsApp("com.facebook.katana", "Facebook Reels", R.drawable.logo_facebook, { it.blockFacebookReels }, { s, v -> s.copy(blockFacebookReels = v) }),
)

/**
 * The frame of a Blocks detail page: a top bar, Pebble with a short line, then the rows.
 * The content gets the current settings and a function that saves a change.
 */
@Composable
private fun BlockPage(
    title: String,
    pebble: String,
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
            PebbleSays(pebble, mood, Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp), pebbleSize = 80.dp)
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
    BlockPage("Short videos", "I close the endless feed. The rest of the app keeps working.", Mood.GUARD, onClose) { s, update ->
        Group(Modifier.appear(0)) {
            shortsApps.forEach { item ->
                SwitchRow(item.name, null, item.get(s), leading = { AppIcon(item.pkg, name = item.name, logo = item.logo) }) { on -> update { item.set(it, on) } }
            }
        }
        Group(Modifier.padding(top = 12.dp).appear(60)) {
            SwitchRow("Allow the first video", "On YouTube and Instagram, the first video of each visit plays, like one a friend sent. The next one is blocked.", s.allowFirstShort) { on ->
                update { it.copy(allowFirstShort = on) }
            }
            SwitchRow("Only during focus", "Also applies to websites and YouTube channels.", s.contentOnlyDuringFocus) { on ->
                update { it.copy(contentOnlyDuringFocus = on) }
            }
        }

        SectionTitle("YouTube")
        Group(Modifier.appear(120)) {
            SwitchRow("Hide the home feed", "Search and subscriptions still work.", s.blockYoutubeHome) { on -> update { it.copy(blockYoutubeHome = on) } }
            SwitchRow("Study mode", "Only videos from the channels below can play.", s.youtubeStudyMode) { on -> update { it.copy(youtubeStudyMode = on) } }
        }
        if (s.youtubeStudyMode) {
            Column(Modifier.appear(0)) {
                SectionTitle("Allowed channels")
                ChipList(s.allowedYoutubeChannels, empty = "No channels yet. Every video is blocked.") { channel ->
                    update { it.copy(allowedYoutubeChannels = it.allowedYoutubeChannels - channel) }
                }
                AddField("Channel name or @handle", KeyboardType.Text, clean = { it.trim().takeIf(String::isNotEmpty) }) { channel ->
                    update { it.copy(allowedYoutubeChannels = it.allowedYoutubeChannels + channel) }
                }
                Hint("Type the name exactly as YouTube shows it under the video.")
            }
        }
    }
}

@Composable
fun WebsitesPage(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val sites by app.dao.sites().collectAsState(emptyList())
    BlockPage("Websites", "I watch the address bar in your browsers.", Mood.THINK, onClose) { s, update ->
        Group(Modifier.appear(0)) {
            SwitchRow("Block adult sites", "A built-in list of adult domains and words.", s.blockAdultSites) { on ->
                update { it.copy(blockAdultSites = on) }
            }
            SwitchRow("Only during focus", "Also applies to short videos and YouTube channels.", s.contentOnlyDuringFocus) { on ->
                update { it.copy(contentOnlyDuringFocus = on) }
            }
        }

        SectionTitle("Your list")
        Row(Modifier.padding(horizontal = ScreenPadding).appear(60), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceButton("Block these", !s.siteAllowList, Modifier.weight(1f)) { update { it.copy(siteAllowList = false) } }
            ChoiceButton("Allow only these", s.siteAllowList, Modifier.weight(1f)) { update { it.copy(siteAllowList = true) } }
        }
        Hint(
            if (s.siteAllowList) "Every other website is blocked. Subdomains of a listed site work too."
            else "These sites and their subdomains are blocked.",
        )
        AddField(if (s.siteAllowList) "Allow a site, like example.com" else "Block a site, like example.com", KeyboardType.Uri, clean = ::hostOf) { domain ->
            app.scope.launch { PolicyActions.changeBlocks(context) { app.dao.addSite(BlockedSite(domain)) } }
        }
        ChipList(sites.map { it.domain }.toSet(), empty = if (s.siteAllowList) "No sites yet. Every website is blocked." else "No sites yet.") { domain ->
            sites.firstOrNull { it.domain == domain }?.let { app.scope.launch { PolicyActions.changeBlocks(context) { app.dao.deleteSite(it) } } }
        }
    }
}

@Composable
fun NotificationsPage(navigator: Navigator, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val held by app.dao.held().collectAsState(emptyList())
    val access = rememberAccess()
    var picking by remember { mutableStateOf(false) }
    val use24 = rememberUse24Hour()
    BlockPage("Notifications", "I keep them in a box until you finish.", Mood.CALM, onClose) { s, update ->
        if (!access.listener) {
            Group(Modifier.padding(bottom = 12.dp)) {
                ListRow(
                    "Allow notification access",
                    "Stillpoint needs it to hold notifications.",
                    onClick = { navigator.push(Route.Settings) },
                    leading = { IconTile(R.drawable.ic_bell, Sp.colors.danger) },
                ) { Chevron() }
            }
        }
        Group(Modifier.appear(0)) {
            ListRow(
                "Hold notifications from",
                if (s.heldPackages.isEmpty()) "No apps" else appCount(s.heldPackages.size),
                onClick = {
                    navigator.push(Route.PickApps("Hold notifications from", s.heldPackages, single = false) { picked -> update { it.copy(heldPackages = picked) } })
                },
            ) { AppSelectionPreview(s.heldPackages) }
            SwitchRow("Hold all day", if (s.holdAlways) "Held at all times." else "Held only during focus and schedules.", s.holdAlways) { on ->
                update { it.copy(holdAlways = on) }
            }
            ListRow(
                "Inbox",
                if (held.isEmpty()) "Nothing waiting" else "${held.size} waiting",
                onClick = { navigator.push(Route.Held) },
            ) { Chevron() }
        }

        SectionTitle("Delivery times")
        val times = s.notificationDeliveryTimes.mapNotNull(String::toIntOrNull).sorted()
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).appear(60),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            times.forEach { minute ->
                Chip(minuteText(minute, use24)) { update { it.copy(notificationDeliveryTimes = it.notificationDeliveryTimes - minute.toString()) } }
            }
            AddChip("Add time") { picking = true }
        }
        Hint(
            if (times.isEmpty()) "Held notifications wait in the inbox until you open it."
            else "At these times you get one quiet summary of what waited.",
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
    BlockPage("Strict mode", "When strict mode is on, nobody can switch me off in a weak moment. Not even you.", Mood.STRICT, onClose) { s, update ->
        Group(Modifier.appear(0)) {
            SwitchRow(
                "Lock Stillpoint while blocks run",
                "Locks the Blocks tab and supported settings pages during focus and schedules.",
                s.protection,
            ) { on -> update { it.copy(protection = on) } }
            SwitchRow(
                "Block split screen",
                "Closes split screen and floating windows during focus and schedules.",
                s.blockMultiWindow,
            ) { on -> update { it.copy(blockMultiWindow = on) } }
        }

        SectionTitle("Extra time")
        Group(Modifier.appear(60)) {
            Stepper("Passes each day", s.emergencyPassesPerDay, 0..10, 1, { if (it == 0) "None" else "$it" }) { value ->
                update { it.copy(emergencyPassesPerDay = value) }
            }
        }
        Hint("A pass opens an app with a gentle limit for 5 more minutes. Strict limits never take passes.")
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
            "Remove $text",
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
        ChunkyButton("Add", submit, enabled = value != null, height = 52.dp)
    }
}
