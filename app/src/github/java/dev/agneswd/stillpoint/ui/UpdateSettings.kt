package dev.agneswd.stillpoint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.Settings
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.ui.design.Sp

/** Replaces the update check. [settings] keeps the same call as the Play flavor. This release does not use it. */
@Composable
fun UpdateSettings(settings: Settings, showTitle: Boolean = true) {
    val context = LocalContext.current
    if (showTitle) SectionTitle(stringResource(R.string.bridge_settings_title))
    else Spacer(Modifier.height(12.dp))
    Group {
        Column(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(top = 8.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.bridge_notice), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.text)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { context.openRonumiRelease() }) {
                    Text(stringResource(R.string.bridge_get_ronumi))
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
        TopBar(stringResource(R.string.bridge_settings_title), onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            settings?.let { UpdateSettings(it, showTitle = false) }
        }
    }
}
