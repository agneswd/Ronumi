package dev.agneswd.stillpoint.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import dev.agneswd.stillpoint.data.settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.Settings
import dev.agneswd.stillpoint.data.updateSettings
import dev.agneswd.stillpoint.ui.design.ChunkyButton
import dev.agneswd.stillpoint.ui.design.Sp
import dev.agneswd.stillpoint.update.UpdateCheck
import dev.agneswd.stillpoint.update.UpdateClient
import dev.agneswd.stillpoint.update.UpdateRelease
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val ReleaseSaver = listSaver<UpdateRelease?, String>(
    save = { value -> value?.let { listOf(it.tag, it.title, it.assetName, it.assetUrl, it.bytes.toString(), it.sha256.orEmpty()) } ?: emptyList() },
    restore = { if (it.isEmpty()) null else UpdateRelease(it[0], it[1], it[2], it[3], it[4].toLong(), it[5].ifEmpty { null }) },
)

@Composable
fun UpdateSettings(settings: Settings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var release by rememberSaveable(stateSaver = ReleaseSaver) { mutableStateOf<UpdateRelease?>(null) }
    var downloadedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0) }

    SectionTitle("App updates")
    Group {
        SwitchRow("Check for updates automatically", "Checks GitHub about once a day. Downloads start only when you choose.", settings.autoUpdateChecks) { on ->
            context.app.scope.launch { context.app.dao.updateSettings { it.copy(autoUpdateChecks = on) } }
        }
        ListRow("Check for updates", if (busy) "Please wait..." else "Get new releases from agneswd/Stillpoint on GitHub.",
            onClick = if (busy) null else ({
                scope.launch {
                    busy = true
                    try {
                        when (val result = UpdateClient.check(context)) {
                            is UpdateCheck.Available -> {
                                release = result.release
                                downloadedPath = null
                                status = "Version ${result.release.tag} is available."
                            }
                            UpdateCheck.UpToDate -> { release = null; downloadedPath = null; status = "You have the latest release." }
                            UpdateCheck.NoRelease -> { release = null; downloadedPath = null; status = "No public release is available yet." }
                            is UpdateCheck.Failed -> { release = null; downloadedPath = null; status = result.message }
                        }
                    } finally { busy = false }
                }
            })) { Chevron() }
        if (status != null || release != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 16.dp)) {
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim) }
                release?.let { available ->
                    Spacer(Modifier.height(12.dp))
                    ChunkyButton(
                        if (busy) "Working... $progress%" else if (downloadedPath != null) "Install update" else "Download update",
                        modifier = Modifier.fillMaxWidth(), enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val ready = downloadedPath?.let(::File)
                                    if (ready == null) {
                                        progress = 0
                                        downloadedPath = UpdateClient.download(context, available) { percent -> scope.launch { progress = percent } }.absolutePath
                                        status = "Download verified. Select Install update to continue."
                                    } else if (!UpdateClient.canInstall(context)) {
                                        status = "Allow app installs on the next screen, then return here and select Install update."
                                        context.startActivity(UpdateClient.unknownSourcesIntent(context))
                                    } else {
                                        context.startActivity(UpdateClient.installIntent(context, ready))
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    downloadedPath = null
                                    status = error.message ?: "The update could not finish. Try again."
                                } finally { busy = false }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun UpdatesScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val settings by context.app.dao.settings().collectAsState(null)
    Column(Modifier.fillMaxSize()) {
        TopBar("App updates", onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            settings?.let { UpdateSettings(it) }
        }
    }
}
