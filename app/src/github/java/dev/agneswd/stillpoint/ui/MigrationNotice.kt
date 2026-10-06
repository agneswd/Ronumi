package dev.agneswd.stillpoint.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.ui.design.ChunkyCard
import dev.agneswd.stillpoint.ui.design.Sp

/**
 * Ronumi 1.0.0 stays at this tag and is not the latest release.
 * This bridge release must stay latest so older apps can update to it.
 * After the repository is renamed to Ronumi, GitHub redirects this URL in the browser.
 */
const val RONUMI_RELEASE_URL = "https://github.com/agneswd/Stillpoint/releases/tag/v1.0.0"

/** Remains dismissed across launches. This preference does not belong in the migrated backup. */
@Composable
fun MigrationNotice() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("ronumi_bridge", Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(preferences.getBoolean("notice_dismissed", false)) }
    if (dismissed) return

    ChunkyCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp)) {
        Column {
            Text(stringResource(R.string.bridge_notice), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.text)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    preferences.edit().putBoolean("notice_dismissed", true).apply()
                    dismissed = true
                }) { Text(stringResource(R.string.bridge_dismiss)) }
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RONUMI_RELEASE_URL)))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.bridge_no_browser, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.bridge_get_ronumi)) }
            }
        }
    }
}
