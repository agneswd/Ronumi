package dev.agneswd.stillpoint.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.exportBackup
import dev.agneswd.stillpoint.data.importBackup
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.data.updateSettings
import dev.agneswd.stillpoint.guard.Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun SettingsScreen(navigator: Navigator, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val access = rememberAccess()
    val settings by app.dao.settings().collectAsState(null)
    val schedules by app.dao.schedules().collectAsState(emptyList())
    val focus by app.dao.activeFocusFlow().collectAsState(null)
    val locked = settings?.protection == true && Rules(schedules = schedules, focus = focus).locked(LocalDateTime.now())

    fun report(action: String, work: suspend () -> Unit) {
        app.scope.launch {
            val message = runCatching { work() }.fold({ "$action done" }, { "$action failed: ${it.message}" })
            withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { report("Backup") { exportBackup(context, app.dao, it) } }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { report("Restore") { importBackup(context, app.dao, it) } }
    }

    Column(Modifier.fillMaxSize()) {
      TopBar("Settings", onClose)
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        SectionTitle("Permissions")
        Column(Modifier.padding(horizontal = ScreenPadding)) { AccessRows(access, includeOptional = true) }

        val s = settings
        if (s != null) {
            SectionTitle("Daily goal")
            Stepper("Focus goal", s.focusGoalMinutes, 5..600, 5, { dev.agneswd.stillpoint.guard.formatMinutes(it) }) { value ->
                app.scope.launch { app.dao.updateSettings { it.copy(focusGoalMinutes = value) } }
            }
            DayChoices(s.goalDays) { value -> app.scope.launch { app.dao.updateSettings { it.copy(goalDays = value) } } }
            ListRow("Productive apps", appCount(s.productivePackages.size), onClick = {
                navigator.push(Route.PickApps("Productive apps", s.productivePackages, single = false) { picked ->
                    app.scope.launch { app.dao.updateSettings { it.copy(productivePackages = picked) } }
                })
            })
        }
        SectionTitle("Backup")
        ListRow("Save a backup", "Limits, schedules, sites, settings and focus history in one file.", onClick = {
            export.launch("stillpoint-${LocalDate.now()}.json")
        })
        ListRow(
            "Restore a backup",
            if (locked || focus != null) "Locked while a focus session or protected schedule runs." else "Replaces everything with the content of the file.",
            onClick = if (locked || focus != null) null else ({ import.launch(arrayOf("application/json", "*/*")) }),
        )

        SectionTitle("About")
        Text(
            "Stillpoint works offline. It has no internet permission, no account and no ads. " +
                "Everything it records stays on this phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ScreenPadding),
        )
        Spacer(Modifier.height(8.dp))
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        Text("Version $version", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = ScreenPadding))
        ListRow("GPLv3 source code", "github.com/agneswd/Stillpoint", onClick = {
            context.openFirst(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/agneswd/Stillpoint")))
        })
        Text("Nunito uses the SIL Open Font License. License texts are included in this app.",
            color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = ScreenPadding))
        Spacer(Modifier.height(32.dp))
      }
    }
}
