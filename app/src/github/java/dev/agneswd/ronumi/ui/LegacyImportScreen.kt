package dev.agneswd.ronumi.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.data.BackupTextException
import dev.agneswd.ronumi.import.LegacyImport
import dev.agneswd.ronumi.ui.design.ChunkyButton
import dev.agneswd.ronumi.ui.design.FloatingDots
import dev.agneswd.ronumi.ui.design.Mood
import dev.agneswd.ronumi.ui.design.Ronumi
import dev.agneswd.ronumi.ui.design.Sp
import dev.agneswd.ronumi.ui.design.popIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ImportPhase { Check, Hidden, Ask, Working, Success, Failed }

/**
 * Blank while the old app is checked. True while this screen covers setup.
 * False when setup or Home can show.
 */
@Composable
fun LegacyImportGate(): Boolean? {
    val context = LocalContext.current
    var phase by remember { mutableStateOf(ImportPhase.Check) }
    var detail by remember { mutableStateOf<String?>(null) }
    var restoreSource by remember { mutableStateOf<String?>(null) }
    var awaitingRestore by remember { mutableStateOf(false) }
    var sawRestoreBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val busy by BackupWork.busy.collectAsState()
    val backupMessage by BackupWork.message.collectAsState()
    val failedText = stringResource(R.string.import_failed)
    val restoredText = stringResource(R.string.backup_restore_success)
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreSource = uri.toString()
    }

    LaunchedEffect(phase) {
        if (phase != ImportPhase.Check) return@LaunchedEffect
        val offered = withContext(Dispatchers.IO) { !LegacyImport.decided(context) && LegacyImport.offerAvailable(context) }
        phase = if (offered) ImportPhase.Ask else ImportPhase.Hidden
    }
    LaunchedEffect(busy, backupMessage) {
        if (!awaitingRestore) return@LaunchedEffect
        if (busy) {
            sawRestoreBusy = true
            return@LaunchedEffect
        }
        if (!sawRestoreBusy) return@LaunchedEffect
        awaitingRestore = false
        sawRestoreBusy = false
        if (backupMessage == restoredText) {
            LegacyImport.remember(context)
            phase = ImportPhase.Hidden
        } else if (backupMessage != null) {
            detail = backupMessage
        }
    }

    fun bring() {
        phase = ImportPhase.Working
        scope.launch {
            try {
                LegacyImport.importNow(context)
                LegacyImport.remember(context)
                phase = ImportPhase.Success
            } catch (error: CancellationException) {
                phase = if (LegacyImport.decided(context)) ImportPhase.Success else ImportPhase.Ask
                throw error
            } catch (error: Exception) {
                detail = (error as? BackupTextException)?.let { resources.getString(it.messageRes) } ?: failedText
                phase = ImportPhase.Failed
            }
        }
    }

    fun fresh() {
        LegacyImport.remember(context)
        phase = ImportPhase.Hidden
    }

    when (phase) {
        ImportPhase.Check -> return null
        ImportPhase.Hidden -> return false
        else -> Unit
    }
    LegacyImportScreen(
        phase = phase,
        detail = detail,
        onBring = ::bring,
        onFresh = ::fresh,
        onLater = { phase = ImportPhase.Hidden },
        onUninstall = {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${LegacyImport.PACKAGE}"))
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                detail = failedText
            }
        },
        onRestore = { openBackup.launch(arrayOf("application/octet-stream", "*/*")) },
    )
    restoreSource?.let { source ->
        BackupPasswordDialog(source, saving = false, onDismiss = { restoreSource = null }) { secret ->
            restoreSource = null
            awaitingRestore = true
            sawRestoreBusy = false
            BackupWork.start(context.applicationContext, source, exporting = false, secret)
        }
    }
    return true
}

@Composable
private fun LegacyImportScreen(
    phase: ImportPhase,
    detail: String?,
    onBring: () -> Unit,
    onFresh: () -> Unit,
    onLater: () -> Unit,
    onUninstall: () -> Unit,
    onRestore: () -> Unit,
) {
    val message = when (phase) {
        ImportPhase.Success -> stringResource(R.string.import_success)
        ImportPhase.Failed -> detail ?: stringResource(R.string.import_failed)
        ImportPhase.Working -> stringResource(R.string.import_working)
        else -> stringResource(R.string.import_prompt)
    }
    Box(Modifier.fillMaxSize()) {
        FloatingDots(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Ronumi(Mood.WAVE, Modifier.popIn(), size = 200.dp)
            Spacer(Modifier.height(28.dp))
            Text(
                message,
                style = MaterialTheme.typography.titleLarge,
                color = Sp.colors.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            when (phase) {
                ImportPhase.Ask -> {
                    ChunkyButton(stringResource(R.string.import_bring), onBring, Modifier.fillMaxWidth())
                    TextButton(onClick = onFresh) { Text(stringResource(R.string.import_fresh)) }
                }
                ImportPhase.Working -> ChunkyButton(stringResource(R.string.import_bring), {}, Modifier.fillMaxWidth(), enabled = false)
                ImportPhase.Success -> {
                    ChunkyButton(stringResource(R.string.import_uninstall), onUninstall, Modifier.fillMaxWidth())
                    TextButton(onClick = onLater) { Text(stringResource(R.string.import_later)) }
                }
                ImportPhase.Failed -> {
                    ChunkyButton(stringResource(R.string.import_restore), onRestore, Modifier.fillMaxWidth())
                    TextButton(onClick = onFresh) { Text(stringResource(R.string.import_fresh)) }
                }
                else -> Unit
            }
        }
    }
}
