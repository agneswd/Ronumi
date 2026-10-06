package dev.agneswd.ronumi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.Settings
import dev.agneswd.ronumi.data.settings
import dev.agneswd.ronumi.data.updateSettings
import dev.agneswd.ronumi.ui.design.ChunkyButton
import dev.agneswd.ronumi.ui.design.Sp
import dev.agneswd.ronumi.update.UpdateCheck
import dev.agneswd.ronumi.update.UpdateClient
import dev.agneswd.ronumi.update.UpdateException
import dev.agneswd.ronumi.update.UpdateRelease
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val ReleaseSaver = listSaver<UpdateRelease?, String>(
    save = { value -> value?.let { listOf(it.tag, it.title, it.assetName, it.assetUrl, it.bytes.toString(), it.sha256.orEmpty()) } ?: emptyList() },
    restore = { if (it.isEmpty()) null else UpdateRelease(it[0], it[1], it[2], it[3], it[4].toLong(), it[5].ifEmpty { null }) },
)

/** The GitHub update check. Downloads and installs start only when the user chooses. */
@Composable
fun UpdateSettings(settings: Settings, showTitle: Boolean = true) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var release by rememberSaveable(stateSaver = ReleaseSaver) { mutableStateOf<UpdateRelease?>(null) }
    var downloadedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0) }

    if (showTitle) SectionTitle(stringResource(R.string.update_title))
    else Spacer(Modifier.height(12.dp))
    Group {
        SwitchRow(stringResource(R.string.update_auto), stringResource(R.string.update_auto_detail), settings.autoUpdateChecks) { on ->
            context.app.scope.launch { context.app.dao.updateSettings { it.copy(autoUpdateChecks = on) } }
        }
        ListRow(stringResource(R.string.update_check), if (busy) stringResource(R.string.update_wait) else stringResource(R.string.update_source),
            onClick = if (busy) null else ({
                scope.launch {
                    busy = true
                    try {
                        when (val result = UpdateClient.check(context)) {
                            is UpdateCheck.Available -> {
                                release = result.release
                                downloadedPath = null
                                status = resources.getString(R.string.update_available, result.release.tag)
                            }
                            UpdateCheck.UpToDate -> { release = null; downloadedPath = null; status = resources.getString(R.string.update_current) }
                            UpdateCheck.NoRelease -> { release = null; downloadedPath = null; status = resources.getString(R.string.update_no_release) }
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
                        if (busy) stringResource(R.string.update_progress, progress) else if (downloadedPath != null) stringResource(R.string.update_install) else stringResource(R.string.update_download),
                        modifier = Modifier.fillMaxWidth(), enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val ready = downloadedPath?.let(::File)
                                    if (ready == null) {
                                        progress = 0
                                        downloadedPath = UpdateClient.download(context, available) { percent -> scope.launch { progress = percent } }.absolutePath
                                        status = resources.getString(R.string.update_verified)
                                    } else if (!UpdateClient.canInstall(context)) {
                                        status = resources.getString(R.string.update_allow_installs)
                                        context.startActivity(UpdateClient.unknownSourcesIntent(context))
                                    } else {
                                        context.startActivity(UpdateClient.installIntent(context, ready))
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    downloadedPath = null
                                    status = resources.getString((error as? UpdateException)?.messageRes ?: R.string.update_failed)
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
        TopBar(stringResource(R.string.update_title), onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            settings?.let { UpdateSettings(it, showTitle = false) }
        }
    }
}
