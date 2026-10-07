package dev.agneswd.ronumi.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.Network
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.updateSettings
import dev.agneswd.ronumi.game.RonumiSlot
import dev.agneswd.ronumi.game.RonumiStyles
import dev.agneswd.ronumi.plus.Entitlement
import dev.agneswd.ronumi.plus.PlusFeature
import dev.agneswd.ronumi.plus.PlusStatus
import dev.agneswd.ronumi.ui.design.ButtonKind
import dev.agneswd.ronumi.ui.design.ChunkyButton
import dev.agneswd.ronumi.ui.design.Mood
import dev.agneswd.ronumi.ui.design.Ronumi
import dev.agneswd.ronumi.ui.design.Sp
import dev.agneswd.ronumi.ui.design.Tag
import kotlinx.coroutines.launch

/** True when Plus is unlocked. The GitHub version is always unlocked. */
@Composable
fun rememberHasPlus(): Boolean {
    val plus by LocalContext.current.app.plus.state.collectAsState()
    return plus.entitlement == Entitlement.UNLOCKED
}

/** The "Plus" label. It replaces a switch or a chevron on a locked row. */
@Composable
fun PlusChip(modifier: Modifier = Modifier) {
    Tag(stringResource(R.string.plus_chip), Sp.colors.brand, modifier)
}

/** One line on the paywall. The order here is the order on screen. */
private enum class PlusPerk(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val line: Int, val features: Set<PlusFeature>) {
    NO_ADS(R.drawable.ic_check, R.string.plus_perk_no_ads, R.string.plus_perk_no_ads_line, emptySet()),
    BLOCKS(R.drawable.ic_tab_blocks, R.string.plus_perk_blocks, R.string.plus_perk_blocks_line,
        setOf(PlusFeature.UNLIMITED_FOCUS_APPS, PlusFeature.UNLIMITED_SCHEDULES, PlusFeature.UNLIMITED_APP_LIMITS)),
    WEBSITES(R.drawable.ic_globe, R.string.plus_perk_websites, R.string.plus_perk_websites_line, setOf(PlusFeature.WEBSITES)),
    SHORTS(R.drawable.ic_video, R.string.plus_perk_shorts, R.string.plus_perk_shorts_line, setOf(PlusFeature.SHORT_VIDEOS)),
    STRICT(R.drawable.ic_lock, R.string.plus_perk_strict, R.string.plus_perk_strict_line, setOf(PlusFeature.STRICT_MODE)),
    INBOX(R.drawable.ic_bell, R.string.plus_perk_inbox, R.string.plus_perk_inbox_line, setOf(PlusFeature.NOTIFICATION_INBOX)),
    STUDY(R.drawable.ic_activity_study, R.string.plus_perk_study, R.string.plus_perk_study_line, setOf(PlusFeature.YOUTUBE_STUDY)),
    HISTORY(R.drawable.ic_tab_progress, R.string.plus_perk_history, R.string.plus_perk_history_line, setOf(PlusFeature.FULL_REPORTS)),
    COLLECTION(R.drawable.ic_activity_sprout, R.string.plus_perk_collection, R.string.plus_perk_collection_line,
        setOf(PlusFeature.PLUS_WARDROBE, PlusFeature.PLUS_SCENES)),
}

private val PlusLook = setOf("plus_accessory_lantern", "plus_hat_fox")

/**
 * The paywall. It opens only from a Plus feature or from Settings. [first] moves that feature's line to the top.
 * There is no trial, no subscription, and no timer. Closing it changes nothing.
 */
@Composable
fun PlusScreen(first: PlusFeature?, onClose: () -> Unit) {
    val context = LocalContext.current
    val plus = context.app.plus
    val state by plus.state.collectAsState()
    // Remember whether this screen opened while locked, so an unlock shows the thank-you view.
    val openedLocked = rememberSaveable { state.entitlement != Entitlement.UNLOCKED }
    var restoring by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        plus.restore()
        onPauseOrDispose { }
    }
    // Without a price, try again when a network comes back. Nothing polls.
    DisposableEffect(state.price == null) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = plus.restore()
        }
        val registered = state.price == null && manager != null &&
            runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess
        onDispose { if (registered) runCatching { manager?.unregisterNetworkCallback(callback) } }
    }

    if (openedLocked && state.entitlement == Entitlement.UNLOCKED) {
        PlusThanks(onClose)
        return
    }

    val perks = PlusPerk.entries.sortedBy { if (first != null && first in it.features) 0 else 1 }
    Column(Modifier.fillMaxSize()) {
        TopBar("", onClose)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Ronumi(Mood.HAPPY, size = 150.dp, style = PlusLook)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.plus_title), style = MaterialTheme.typography.headlineMedium, color = Sp.colors.text)
            Text(stringResource(R.string.plus_tagline), style = MaterialTheme.typography.bodyLarge, color = Sp.colors.textDim, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            perks.forEach { perk ->
                val highlighted = first != null && first in perk.features
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (highlighted) Sp.colors.brandSoft else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile(perk.icon, Sp.colors.brand)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(perk.title), style = MaterialTheme.typography.titleSmall, color = Sp.colors.text)
                        Text(stringResource(perk.line), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(top = 8.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                state.entitlement == Entitlement.PENDING -> Text(
                    stringResource(R.string.plus_pending), style = MaterialTheme.typography.bodyLarge, color = Sp.colors.text, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                else -> {
                    val failed = state.status == PlusStatus.ERROR && !restoring
                    val nothingToRestore = restoring && state.status == PlusStatus.IDLE && state.entitlement == Entitlement.LOCKED
                    if (failed) Text(stringResource(R.string.plus_error), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.text, textAlign = TextAlign.Center)
                    if (nothingToRestore) Text(stringResource(R.string.plus_restore_none), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    val price = state.price
                    ChunkyButton(
                        if (price != null) stringResource(R.string.plus_unlock_for, price) else stringResource(R.string.plus_unlock),
                        { context.findActivity()?.let { restoring = false; plus.purchase(it) } },
                        Modifier.fillMaxWidth(),
                        kind = ButtonKind.MINT,
                        enabled = price != null && state.status != PlusStatus.BUSY,
                    )
                    Text(
                        stringResource(if (price != null) R.string.plus_one_payment else R.string.plus_no_price),
                        style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    TextButton(onClick = { restoring = true; plus.restore() }) {
                        Text(stringResource(R.string.plus_restore), style = MaterialTheme.typography.titleSmall, color = Sp.colors.brand)
                    }
                }
            }
        }
    }
}

/** Shown once after a purchase unlocks Plus. The new look goes on only if the user asks. */
@Composable
private fun PlusThanks(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    var tried by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { dev.agneswd.ronumi.ui.design.Sfx.play(dev.agneswd.ronumi.ui.design.Sound.LEVEL_UP) }
    Column(
        Modifier.fillMaxSize().padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Ronumi(Mood.CELEBRATE, size = 170.dp, style = if (tried) PlusLook else dev.agneswd.ronumi.ui.design.LocalRonumiStyle.current)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.plus_thanks_title), style = MaterialTheme.typography.headlineMedium, color = Sp.colors.text)
        Text(stringResource(R.string.plus_thanks_line), style = MaterialTheme.typography.bodyLarge, color = Sp.colors.textDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        ChunkyButton(stringResource(R.string.plus_done), onDone, Modifier.fillMaxWidth())
        if (!tried) TextButton(onClick = {
            tried = true
            app.scope.launch {
                app.dao.updateSettings { s ->
                    val replaced = RonumiStyles.items.filter { it.slot == RonumiSlot.HAT || it.slot == RonumiSlot.ACCESSORY }.map { it.id }.toSet()
                    s.copy(pebbleItems = s.pebbleItems - replaced + PlusLook)
                }
            }
        }) { Text(stringResource(R.string.plus_try_on), style = MaterialTheme.typography.titleSmall, color = Sp.colors.brand) }
    }
}

/** The activity behind a Compose context. Play needs it to show the payment sheet. */
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
