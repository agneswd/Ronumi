package dev.agneswd.ronumi.ui

import dev.agneswd.ronumi.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.room.withTransaction
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.updateSettings
import dev.agneswd.ronumi.game.RonumiPets
import dev.agneswd.ronumi.game.RonumiSlot
import dev.agneswd.ronumi.game.RonumiStyles
import dev.agneswd.ronumi.ui.design.ButtonKind
import dev.agneswd.ronumi.ui.design.ChunkyButton
import dev.agneswd.ronumi.ui.design.ChunkyCard
import dev.agneswd.ronumi.ui.design.Confetti
import dev.agneswd.ronumi.ui.design.LocalRonumiStyle
import dev.agneswd.ronumi.ui.design.Mood
import dev.agneswd.ronumi.ui.design.Ronumi
import dev.agneswd.ronumi.ui.design.Sfx
import dev.agneswd.ronumi.ui.design.Sound
import dev.agneswd.ronumi.ui.design.Sp
import dev.agneswd.ronumi.ui.design.popIn
import kotlinx.coroutines.launch

private const val SECRET_OUTFIT = "outfit_star_guardian"

/** Celebrates the hidden outfit once, right after the tap that unlocks it. Place it above the app content. */
@Composable
fun SecretReveal() {
    val show by RonumiPets.reveal.collectAsState()
    if (!show) return
    val context = LocalContext.current
    val app = context.app
    val outfits = RonumiStyles.items.filter { it.slot == RonumiSlot.OUTFIT }.map { it.id }.toSet()
    // Ronumi keeps its color, hat and extras, and tries the new outfit on.
    val preview = LocalRonumiStyle.current - outfits + SECRET_OUTFIT
    val close = { RonumiPets.reveal.value = false }
    LaunchedEffect(Unit) { Sfx.play(Sound.LEVEL_UP) }
    Dialog(onDismissRequest = close) {
        Box {
            ChunkyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.wardrobe_secret_secret_found), style = MaterialTheme.typography.titleMedium, color = if (Sp.colors.dark) Sp.colors.flame else Color(0xFFA64D00))
                    Spacer(Modifier.height(8.dp))
                    Ronumi(Mood.CELEBRATE, Modifier.popIn(150), size = 150.dp, style = preview)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.wardrobe_secret_star_guardian), style = MaterialTheme.typography.headlineSmall, color = Sp.colors.text)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.wardrobe_secret_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sp.colors.textDim,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    ChunkyButton(
                        stringResource(R.string.wardrobe_secret_wear_it_now),
                        {
                            close()
                            app.scope.launch {
                                app.database.withTransaction {
                                    app.dao.updateSettings { it.copy(pebbleItems = it.pebbleItems - outfits + SECRET_OUTFIT) }
                                }
                                dev.agneswd.ronumi.widget.Widgets.refresh(context)
                            }
                        },
                        Modifier.fillMaxWidth(),
                        sound = Sound.SELECT,
                    )
                    ChunkyButton(stringResource(R.string.wardrobe_secret_later), close, Modifier.fillMaxWidth(), kind = ButtonKind.GHOST, sound = null)
                }
            }
            Confetti(key = Unit, Modifier.matchParentSize())
        }
    }
}
