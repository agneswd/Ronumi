package dev.agneswd.ronumi.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.consent.ConsentKind
import dev.agneswd.ronumi.consent.Consents
import dev.agneswd.ronumi.ui.design.ButtonKind
import dev.agneswd.ronumi.ui.design.ChunkyButton
import dev.agneswd.ronumi.ui.design.Mood
import dev.agneswd.ronumi.ui.design.Ronumi
import dev.agneswd.ronumi.ui.design.Sp

private const val PRIVACY_POLICY = "https://github.com/agneswd/Ronumi/blob/main/docs/privacy.md"

private class ConsentRequest(
    val kind: ConsentKind,
    val onAccepted: () -> Unit,
    val onDeclined: () -> Unit,
)

/**
 * The one open consent request. [ConsentHost] draws it above the current screen.
 * A second request is ignored while one is open. Process death drops it.
 */
private object ConsentPrompt {
    var request by mutableStateOf<ConsentRequest?>(null)

    fun ask(context: Context, kind: ConsentKind, onDeclined: () -> Unit, onAccepted: () -> Unit) {
        if (Consents.granted(context, kind)) {
            onAccepted()
            return
        }
        if (request != null) return
        request = ConsentRequest(kind, onAccepted, onDeclined)
    }

    /** Saves the version, then runs the action. Home during that action is not a decline. */
    fun accept(context: Context) {
        val current = request ?: return
        Consents.accept(context, current.kind)
        request = null
        current.onAccepted()
    }

    fun decline() {
        val current = request ?: return
        request = null
        current.onDeclined()
    }
}

/** Shows [kind] when it is not granted, then runs [onAccepted]. Decline runs [onDeclined]. */
fun Context.withConsent(kind: ConsentKind, onDeclined: () -> Unit = {}, onAccepted: () -> Unit) {
    ConsentPrompt.ask(this, kind, onDeclined, onAccepted)
}

/** Draws the open consent request. Place it last so Back closes it first. */
@Composable
fun ConsentHost() {
    val request = ConsentPrompt.request ?: return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    BackHandler { ConsentPrompt.decline() }
    DisposableEffect(lifecycleOwner, request) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_STOP) return@LifecycleEventObserver
            val activity = context as? Activity
            if (activity?.isChangingConfigurations == true) return@LifecycleEventObserver
            ConsentPrompt.decline()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    ConsentScreen(request.kind, onAccept = { ConsentPrompt.accept(context) }, onDecline = { ConsentPrompt.decline() })
}

/** The disclosure for [kind]. Scrolling the body does not accept. */
@Composable
private fun ConsentScreen(kind: ConsentKind, onAccept: () -> Unit, onDecline: () -> Unit) {
    val context = LocalContext.current
    val paragraphs = stringArrayResource(bodyOf(kind))
    Column(Modifier.fillMaxSize().background(Sp.colors.background).statusBarsPadding().navigationBarsPadding()) {
        TopBar("", onDecline)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding)) {
            Ronumi(Mood.CALM, size = 72.dp, thoughtDots = false)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(titleOf(kind)), style = MaterialTheme.typography.headlineSmall, color = Sp.colors.text)
            Spacer(Modifier.height(12.dp))
            paragraphs.forEach { paragraph ->
                Text(paragraph, style = MaterialTheme.typography.bodyLarge, color = Sp.colors.text)
                Spacer(Modifier.height(12.dp))
            }
            TextButton(onClick = {
                val view = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY))
                runCatching { context.startActivity(view) }
            }) {
                Text(stringResource(R.string.consent_privacy_policy), color = Sp.colors.brand)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(top = 8.dp, bottom = 12.dp)) {
            ChunkyButton(stringResource(acceptOf(kind)), onAccept, Modifier.fillMaxWidth())
            ChunkyButton(stringResource(R.string.consent_not_now), onDecline, Modifier.fillMaxWidth(), kind = ButtonKind.GHOST)
        }
    }
}

private fun titleOf(kind: ConsentKind) = when (kind) {
    ConsentKind.ACCESSIBILITY -> R.string.consent_accessibility_title
    ConsentKind.USAGE -> R.string.consent_usage_title
    ConsentKind.NOTIFICATION_ACCESS -> R.string.consent_notification_title
    ConsentKind.APP_LIST -> R.string.consent_app_list_title
}

private fun acceptOf(kind: ConsentKind) = when (kind) {
    ConsentKind.APP_LIST -> R.string.consent_choose_apps
    else -> R.string.consent_agree
}

private fun bodyOf(kind: ConsentKind) = when (kind) {
    ConsentKind.ACCESSIBILITY -> R.array.consent_accessibility_body
    ConsentKind.USAGE -> R.array.consent_usage_body
    ConsentKind.NOTIFICATION_ACCESS -> R.array.consent_notification_body
    ConsentKind.APP_LIST -> R.array.consent_app_list_body
}
