package dev.agneswd.ronumi.ui

import dev.agneswd.ronumi.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.exportBackup
import dev.agneswd.ronumi.data.importBackup
import dev.agneswd.ronumi.ui.design.Sp
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps one backup operation active across navigation and configuration changes. */
private object BackupWork {
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun start(context: android.content.Context, source: String, exporting: Boolean, secret: CharArray) {
        if (!busy.compareAndSet(false, true)) { secret.fill('\u0000'); return }
        message.value = null
        val app = context.app
        app.scope.launch {
            try {
                val result = try {
                    if (exporting) exportBackup(app, app.dao, Uri.parse(source), secret)
                    else importBackup(app, app.dao, Uri.parse(source), secret)
                    if (exporting) context.getString(R.string.backup_export_success) else context.getString(R.string.backup_restore_success)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (error is dev.agneswd.ronumi.data.BackupTextException) context.getString(error.messageRes)
                    else error.message ?: context.getString(R.string.backup_failure_message)
                }
                message.value = result
                withContext(Dispatchers.Main) { Toast.makeText(app, result, Toast.LENGTH_LONG).show() }
            } finally {
                secret.fill('\u0000')
                busy.value = false
            }
        }
    }
}

@Composable
fun BackupSettings(restoreLocked: Boolean) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val busy by BackupWork.busy.collectAsState()
    val message by BackupWork.message.collectAsState()
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { saving = true; pendingUri = uri.toString() }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { saving = false; pendingUri = uri.toString() }
    }

    SectionTitle(stringResource(R.string.backup_section_title))
    Group {
        ListRow(stringResource(R.string.backup_save_a_backup), stringResource(R.string.backup_export_description),
            onClick = if (busy) null else ({ create.launch(resources.getString(R.string.backup_default_filename, LocalDate.now())) })) { Chevron() }
        ListRow(stringResource(R.string.backup_restore_a_backup),
            if (restoreLocked) stringResource(R.string.backup_restore_locked_description) else stringResource(R.string.backup_restore_description),
            onClick = if (busy || restoreLocked) null else ({ open.launch(arrayOf("application/octet-stream", "*/*")) })) {
            if (!restoreLocked && !busy) Chevron()
        }
        if (busy || message != null) {
            Text(if (busy) stringResource(R.string.backup_working_on_your_backup) else message.orEmpty(),
                modifier = Modifier.padding(horizontal = ScreenPadding).padding(bottom = 16.dp),
                style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
        }
    }

    pendingUri?.let { source ->
        // Passwords stay in this dialog's memory. They are never saved with UI state or preferences.
        var password by remember(source, saving) { mutableStateOf("") }
        var confirmation by remember(source, saving) { mutableStateOf("") }
        var visible by remember(source, saving) { mutableStateOf(false) }
        val valid = password.length in (if (saving) 12 else 1)..1024 && (!saving || password == confirmation)
        fun submit() {
            if (!valid || BackupWork.busy.value) return
            val secret = password.toCharArray()
            password = ""
            confirmation = ""
            pendingUri = null
            BackupWork.start(context.applicationContext, source, saving, secret)
        }
        AlertDialog(
            onDismissRequest = { password = ""; confirmation = ""; pendingUri = null },
            title = { Text(if (saving) stringResource(R.string.backup_protect_your_backup) else stringResource(R.string.backup_unlock_your_backup)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (saving) stringResource(R.string.backup_password_create_help)
                        else stringResource(R.string.backup_password_restore_help))
                    OutlinedTextField(password, { if (it.length <= 1024) password = it },
                        label = { Text(stringResource(R.string.backup_password)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (saving) ImeAction.Next else ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }))
                    if (saving) {
                        OutlinedTextField(confirmation, { if (it.length <= 1024) confirmation = it },
                            label = { Text(stringResource(R.string.backup_repeat_password)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }))
                    }
                    TextButton(onClick = { visible = !visible }) { Text(if (visible) stringResource(R.string.backup_hide_password) else stringResource(R.string.backup_show_password)) }
                }
            },
            confirmButton = { TextButton(onClick = { submit() }, enabled = valid) { Text(if (saving) stringResource(R.string.backup_save_backup) else stringResource(R.string.backup_restore_backup)) } },
            dismissButton = { TextButton(onClick = { password = ""; confirmation = ""; pendingUri = null }) { Text(stringResource(R.string.backup_cancel)) } },
        )
    }
}
