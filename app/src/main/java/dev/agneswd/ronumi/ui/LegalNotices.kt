package dev.agneswd.ronumi.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agneswd.ronumi.Distribution
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.ui.design.Sp

/** A license text that ships in the app assets (the `licenses/` folder). */
private enum class LicenseFile(@StringRes val title: Int, val asset: String) {
    GPL(R.string.legal_gpl_title, "GPL-3.0.txt"),
    NUNITO(R.string.legal_nunito_title, "Nunito-OFL.txt"),
    VCSL(R.string.legal_vcsl_title, "VCSL-CC0.txt"),
    FREESOUND(R.string.legal_freesound_title, "Freesound-Focus-CC0.txt"),
    LIBRARIES(R.string.legal_libraries_title, "Apache-2.0.txt"),
}

/** One third-party entry on the screen: what it is, and the license that covers it. */
private class Notice(@StringRes val name: Int, @StringRes val terms: Int, val license: LicenseFile)

private val notices = listOf(
    Notice(R.string.legal_nunito_title, R.string.legal_nunito_terms, LicenseFile.NUNITO),
    Notice(R.string.legal_vcsl_title, R.string.legal_vcsl_terms, LicenseFile.VCSL),
    Notice(R.string.legal_freesound_title, R.string.legal_freesound_terms, LicenseFile.FREESOUND),
    Notice(R.string.legal_libraries_title, R.string.legal_libraries_terms, LicenseFile.LIBRARIES),
)

private const val BILLING_TERMS = "https://developer.android.com/studio/terms.html"

/** Copyright, warranty, license, source, and third-party notices. Opened from Settings, About. */
@Composable
fun LegalNoticesScreen(onClose: () -> Unit) {
    var reading by rememberSaveable { mutableStateOf<LicenseFile?>(null) }
    reading?.let { file ->
        BackHandler { reading = null }
        LicenseText(file) { reading = null }
        return
    }
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.legal_title), onClose)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            val side = Modifier.padding(horizontal = ScreenPadding)
            Text(stringResource(R.string.legal_app_version, version), side, style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
            Text(stringResource(R.string.legal_copyright), side, style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.legal_gpl_summary), side, style = MaterialTheme.typography.bodyLarge, color = Sp.colors.text)
            Spacer(Modifier.height(4.dp))
            LinkButton(stringResource(R.string.legal_read_license)) { reading = LicenseFile.GPL }
            LinkButton(stringResource(R.string.legal_source_for_version)) { open(sourceUrl(version)) }
            LinkButton(stringResource(R.string.consent_privacy_policy)) { open(PRIVACY_POLICY_URL) }

            SectionTitle(stringResource(R.string.legal_third_party))
            notices.forEach { notice ->
                Text(stringResource(notice.name), side, style = MaterialTheme.typography.titleSmall, color = Sp.colors.text)
                Text(stringResource(notice.terms), side, style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
                LinkButton(stringResource(R.string.legal_read_terms)) { reading = notice.license }
                Spacer(Modifier.height(8.dp))
            }
            if (Distribution.usesBilling) {
                Text(stringResource(R.string.legal_billing_title), side, style = MaterialTheme.typography.titleSmall, color = Sp.colors.text)
                Text(stringResource(R.string.legal_billing_terms), side, style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
                LinkButton(stringResource(R.string.legal_read_terms)) { open(BILLING_TERMS) }
            }
        }
    }
}

/** The release source for this exact version. Every release needs a matching `v<versionName>` tag. */
internal fun sourceUrl(versionName: String) = "https://github.com/agneswd/Ronumi/tree/v$versionName"

@Composable
private fun LicenseText(file: LicenseFile, onClose: () -> Unit) {
    val context = LocalContext.current
    val text = remember(file) { context.assets.open(file.asset).bufferedReader().use { it.readText() } }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(file.title), onClose)
        Text(
            text,
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding).padding(bottom = 32.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Sp.colors.text,
        )
    }
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    // No side padding inside the button, so the label lines up with the text above it.
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = ScreenPadding), contentPadding = PaddingValues(vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = Sp.colors.brand)
    }
}
