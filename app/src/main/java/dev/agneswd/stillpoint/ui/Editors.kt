package dev.agneswd.stillpoint.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import dev.agneswd.stillpoint.ui.design.ScheduleIcon
import dev.agneswd.stillpoint.ui.design.scheduleIconChoices
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import dev.agneswd.stillpoint.ui.design.ButtonKind
import dev.agneswd.stillpoint.ui.design.ChunkyButton
import dev.agneswd.stillpoint.ui.design.Sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.BlockMode
import dev.agneswd.stillpoint.guard.minuteText
import dev.agneswd.stillpoint.guard.time
import dev.agneswd.stillpoint.usage.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** A full screen with a title, a close button and an optional action. */
@Composable
private fun EditorFrame(
    title: String,
    onClose: () -> Unit,
    action: String? = null,
    onAction: () -> Unit = {},
    actionEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title, onClose, action, actionEnabled, onAction)
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun AppPicker(route: Route.PickApps, onClose: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(route.selected) }
    val apps by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) { context.app.catalog.launchableApps() }
    }
    val done = {
        route.onDone(selected)
        onClose()
    }
    EditorFrame(route.title, onClose, action = if (route.single) null else "Done (${selected.size})", onAction = done) {
        Column {
            OutlinedTextField(
                query,
                { query = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp).trackTextFieldFocus(),
            )
            val shown = apps.orEmpty().filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                // Chosen apps first, so the user sees the current choice at the top.
                .sortedByDescending { it.packageName in route.selected }
            LazyColumn {
                if (apps == null) item { ListRow("Loading apps") }
                items(shown, key = { it.packageName }) { item ->
                    val checked = item.packageName in selected
                    val toggle = {
                        if (route.single) {
                            route.onDone(setOf(item.packageName))
                            onClose()
                        } else {
                            selected = if (checked) selected - item.packageName else selected + item.packageName
                        }
                    }
                    ListRow(item.label, onClick = toggle, leading = { AppIcon(item.packageName) }) {
                        if (!route.single) Checkbox(checked, { toggle() }, colors = CheckboxDefaults.colors(checkedColor = Sp.colors.brand))
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleEditor(route: Route.EditSchedule, onClose: () -> Unit, navigator: Navigator) {
    val context = LocalContext.current
    val original = route.original
    var draft by route::draft
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    var pickingIcon by remember { mutableStateOf(false) }
    val save: () -> Unit = {
        context.app.scope.launch {
            if (dev.agneswd.stillpoint.guard.PolicyActions.saveSchedule(context, draft.copy(name = draft.name.trim()))) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onClose() }
            }
        }
    }
    if (pickingIcon) {
        AlertDialog(
            onDismissRequest = { pickingIcon = false },
            title = { Text("Choose a schedule icon") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Automatic follows the start time. Pick another icon for your own routine.", color = Sp.colors.textDim)
                    scheduleIconChoices.chunked(3).forEach { choices ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            choices.forEach { (id, label) ->
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                        .background(if (draft.icon == id) Sp.colors.brandSoft else Sp.colors.background)
                                        .selectable(draft.icon == id, role = Role.RadioButton) {
                                            draft = draft.copy(icon = id)
                                            pickingIcon = false
                                        }.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    ScheduleIcon(id, draft.startMinute, size = 34.dp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = Sp.colors.text,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                            repeat(3 - choices.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingIcon = false }) { Text("Close") } },
        )
    }
    EditorFrame(
        if (original == null) "New schedule" else "Edit schedule",
        onClose,
        action = "Save",
        onAction = save,
        actionEnabled = draft.name.isNotBlank() && draft.days != 0,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                draft.name,
                { draft = draft.copy(name = it) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp).trackTextFieldFocus(),
            )
            ListRow(
                "Schedule icon", scheduleIconChoices.firstOrNull { it.first == draft.icon }?.second ?: "Automatic",
                onClick = { pickingIcon = true },
            ) { ScheduleIcon(draft.icon, draft.startMinute, size = 40.dp) }
            ListRow("Starts", minuteText(draft.startMinute), onClick = { picking = true })
            ListRow(
                "Ends",
                minuteText(draft.endMinute) + when {
                    draft.startMinute == draft.endMinute -> ", blocks all day"
                    draft.endMinute < draft.startMinute -> ", next day"
                    else -> ""
                },
                onClick = { picking = false },
            )

            SectionTitle("Days")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                DayOfWeek.entries.forEach { day ->
                    val bit = 1 shl (day.value - 1)
                    val on = draft.days and bit != 0
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (on) Sp.colors.brand else Sp.colors.surfaceHigh)
                            .clickable { draft = draft.copy(days = draft.days xor bit) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.getDisplayName(TextStyle.NARROW, androidx.compose.ui.platform.LocalLocale.current.platformLocale),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (on) Sp.colors.onFill else Sp.colors.textDim,
                        )
                    }
                }
            }

            SectionTitle("Planned focus")
            SwitchRow("Start focus at this time", "Allow alarms in Settings for automatic start. Otherwise you receive a reminder.", draft.startFocus) { on -> draft = draft.copy(startFocus = on) }
            if (draft.startFocus) Stepper("Focus length", draft.focusMinutes, 5..240, 5, { dev.agneswd.stillpoint.guard.formatMinutes(it) }) { value -> draft = draft.copy(focusMinutes = value) }

            SectionTitle("Apps")
            ListRow(
                if (draft.mode == BlockMode.LISTED) "Blocked apps" else "Allowed apps",
                appCount(draft.packages.size),
                onClick = {
                    navigator.push(
                        Route.PickApps(if (draft.mode == BlockMode.LISTED) "Block during ${draft.name}" else "Allow during ${draft.name}", draft.packages, single = false) {
                            draft = draft.copy(packages = it)
                        },
                    )
                },
            ) { AppSelectionPreview(draft.packages) }
            SwitchRow("Block every other app", "Only the chosen apps work during this schedule.", draft.mode == BlockMode.ALL_EXCEPT) { on ->
                draft = draft.copy(mode = if (on) BlockMode.ALL_EXCEPT else BlockMode.LISTED)
            }

            if (original != null) {
                Spacer(Modifier.height(24.dp))
                ChunkyButton(
                    "Delete schedule",
                    {
                        context.app.scope.launch {
                            if (dev.agneswd.stillpoint.guard.PolicyActions.saveSchedule(context, original, delete = true)) {
                                kotlinx.coroutines.withContext(Dispatchers.Main) { onClose() }
                            }
                        }
                    },
                    Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                    kind = ButtonKind.DANGER,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    picking?.let { start ->
        val minute = if (start) draft.startMinute else draft.endMinute
        TimeDialog(minute, onDismiss = { picking = null }) { picked ->
            draft = if (start) draft.copy(startMinute = picked) else draft.copy(endMinute = picked)
            picking = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(minute: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(minute / 60, minute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun HeldScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val dao = context.app.dao
    val held by dao.held().collectAsState(emptyList())
    EditorFrame("Held notifications", onClose, action = "Clear all", onAction = { context.app.scope.launch {
        dao.clearHeld()
        context.getSharedPreferences("delivery", android.content.Context.MODE_PRIVATE).edit().remove("lastDelivered").apply()
    } }, actionEnabled = held.isNotEmpty()) {
        LazyColumn {
            if (held.isEmpty()) item { ListRow("Nothing held", "Notifications from your chosen apps show here.") }
            items(held, key = { it.id }) { item ->
                ListRow(
                    title = item.title.ifBlank { context.app.catalog.label(item.packageName) },
                    subtitle = listOf(item.text, "${context.app.catalog.label(item.packageName)}, ${time(item.postedAt)}")
                        .filter { it.isNotBlank() }.joinToString("\n"),
                    leading = { AppIcon(item.packageName, 32.dp) },
                    onClick = {
                        context.packageManager.getLaunchIntentForPackage(item.packageName)
                            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ?.let(context::startActivity)
                    },
                )
            }
        }
    }
}

/** At least one active day is required. */
@Composable
fun DayChoices(mask: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val bit = 1 shl (day.value - 1)
            val on = mask and bit != 0
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp))
                .background(if (on) Sp.colors.brand else Sp.colors.surfaceHigh).clickable {
                    val next = mask xor bit
                    if (next != 0) onChange(next)
                }, contentAlignment = Alignment.Center) {
                Text(day.getDisplayName(TextStyle.NARROW, androidx.compose.ui.platform.LocalLocale.current.platformLocale), color = if (on) Sp.colors.onFill else Sp.colors.textDim)
            }
        }
    }
}
