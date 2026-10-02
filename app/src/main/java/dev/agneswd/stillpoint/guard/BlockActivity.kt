package dev.agneswd.stillpoint.guard

import dev.agneswd.stillpoint.ui.design.Sound
import dev.agneswd.stillpoint.ui.design.Sfx
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.agneswd.stillpoint.data.settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.currentSettings
import dev.agneswd.stillpoint.ui.PebbleSays
import dev.agneswd.stillpoint.ui.design.ButtonKind
import dev.agneswd.stillpoint.ui.design.ChunkyButton
import dev.agneswd.stillpoint.ui.design.ChunkyProgress
import dev.agneswd.stillpoint.ui.design.FloatingDots
import dev.agneswd.stillpoint.ui.design.Mood
import dev.agneswd.stillpoint.ui.design.Sp
import dev.agneswd.stillpoint.ui.design.StillpointTheme
import dev.agneswd.stillpoint.ui.design.appear
import dev.agneswd.stillpoint.ui.design.popIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** The full-screen block. The guard opens it over a blocked app, feed or site. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        enableEdgeToEdge()
        render()
    }

    override fun onDestroy() {
        if (isFinishing) {
            sendBroadcast(Intent(GuardService.ACTION_BLOCK_CLOSED).setPackage(packageName))
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    private fun render() {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val kind = BlockKind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: BlockKind.FOCUS.name)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val detail = intent.getStringExtra(EXTRA_DETAIL).orEmpty()
        val gentle = intent.getBooleanExtra(EXTRA_GENTLE, false)
        val reveal = intent.getBooleanExtra(EXTRA_REVEAL, false)
        // The E2E test reads this line. UI dumps would pause the guard, so it cannot read the screen.
        Log.i("Stillpoint", "block shown: $title")
        Sfx.play(Sound.BLOCK)
        // App and schedule blocks go to the phone home screen.
        // Shorts and study blocks stay inside the blocked app.
        // A home-feed block only closes, because search is already open underneath.
        val leave = {
            when {
                reveal -> closeWithoutAnimation()
                kind == BlockKind.SHORTS || kind == BlockKind.STUDY -> returnToApp()
                kind in homeKinds -> goHome()
                else -> closeWithoutAnimation()
            }
        }
        setContent {
            val settings by app.dao.settings().collectAsState(null)
            val themeMode = settings?.themeMode ?: return@setContent
            StillpointTheme(themeMode = themeMode) {
                // The guard's cover stays up until this screen has drawn. decorView.post is too early.
                LaunchedEffect(title) {
                    withFrameNanos { }
                    withFrameNanos { }
                    sendBroadcast(Intent(GuardService.ACTION_COVER_READY).setPackage(packageName))
                }
                val dark = Sp.colors.dark
                androidx.compose.runtime.SideEffect {
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                val sessions by app.dao.sessions().collectAsState(emptyList())
                val style = settings?.let {
                    dev.agneswd.stillpoint.game.PebbleStyles.resolve(it.pebbleItems, dev.agneswd.stillpoint.game.gameState(sessions, it).level.number, it.petTapCount)
                }.orEmpty()
                androidx.compose.runtime.CompositionLocalProvider(dev.agneswd.stillpoint.ui.design.LocalPebbleStyle provides style) {
                BackHandler(onBack = leave)
                BlockScreen(
                    kind = kind,
                    icon = remember(pkg) { app.catalog.icon(pkg) },
                    title = title,
                    detail = detail,
                    gentle = gentle,
                    onClose = leave,
                    onMore = {
                        app.scope.launch {
                            val granted = PolicyActions.grantExtra(this@BlockActivity, pkg)
                            withContext(Dispatchers.Main) {
                                if (granted) {
                                    packageManager.getLaunchIntentForPackage(pkg)?.let(::startActivity)
                                    finish()
                                } else Toast.makeText(this@BlockActivity, "No extra passes are available", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        closeWithoutAnimation()
    }

    /** Asks the guard to leave Shorts or a blocked player, then closes this screen. */
    private fun returnToApp() {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        sendBroadcast(Intent(GuardService.ACTION_RETURN).setPackage(packageName).putExtra(GuardService.EXTRA_BLOCKED_PACKAGE, pkg))
        window.decorView.postDelayed({ closeWithoutAnimation() }, 250)
    }

    private fun closeWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_DETAIL = "detail"
        private const val EXTRA_GENTLE = "gentle"
        private const val EXTRA_REVEAL = "reveal"
        private const val WAIT_SECONDS = 10
        private val homeKinds = setOf(BlockKind.FOCUS, BlockKind.SCHEDULE, BlockKind.LIMIT, BlockKind.SITE, BlockKind.PROTECTION, BlockKind.MULTI_WINDOW)

        fun intent(context: Context, pkg: String, reason: BlockReason, reveal: Boolean = false): Intent =
            Intent(context, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_PACKAGE, pkg)
                .putExtra(EXTRA_KIND, reason.kind.name)
                .putExtra(EXTRA_TITLE, reason.title)
                .putExtra(EXTRA_DETAIL, reason.detail)
                .putExtra(EXTRA_GENTLE, reason.gentle)
                .putExtra(EXTRA_REVEAL, reveal)
    }

    @Composable
    private fun BlockScreen(
        kind: BlockKind,
        icon: android.graphics.Bitmap?,
        title: String,
        detail: String,
        gentle: Boolean,
        onClose: () -> Unit,
        onMore: () -> Unit,
    ) {
        var wait by remember(title) { mutableIntStateOf(WAIT_SECONDS) }
        if (gentle) {
            LaunchedEffect(title) {
                while (wait > 0) {
                    delay(1_000)
                    wait--
                }
            }
        }
        // Passes left today. Null until the database answers.
        val passes by produceState<Int?>(null, gentle) {
            if (gentle) value = withContext(Dispatchers.IO) {
                val dao = app.dao
                (dao.currentSettings().emergencyPassesPerDay - dao.passesUsed(LocalDate.now().toString())).coerceAtLeast(0)
            }
        }
        val (mood, line) = remember(kind, title) { pebbleLine(kind) }
        Box(Modifier.fillMaxSize().background(Sp.colors.background)) {
            FloatingDots(Modifier.fillMaxSize())
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(0.5f))
                PebbleSays(line, mood, Modifier.fillMaxWidth().appear(0), side = false, pebbleSize = 150.dp)
                if (icon != null) {
                    // The blocked app sits on Pebble's shoulder with a stop badge.
                    Box(Modifier.offset(x = 70.dp, y = (-46).dp).popIn(300)) {
                        Image(icon.asImageBitmap(), null, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).border(3.dp, Sp.colors.background, RoundedCornerShape(14.dp)))
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp).size(24.dp).clip(CircleShape)
                                .background(Sp.colors.danger).border(3.dp, Sp.colors.background, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size(10.dp, 3.dp).background(Sp.colors.onFill)) }
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                }
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Sp.colors.text, textAlign = TextAlign.Center, modifier = Modifier.appear(120))
                Spacer(Modifier.height(8.dp))
                Text(detail, style = MaterialTheme.typography.titleMedium, color = Sp.colors.textDim, textAlign = TextAlign.Center, modifier = Modifier.appear(200))
                Spacer(Modifier.weight(1f))
                ChunkyButton(if (kind == BlockKind.SHORTS || kind == BlockKind.STUDY) "Back to the app" else "Close", onClose, Modifier.fillMaxWidth())
                if (gentle) {
                    Spacer(Modifier.height(14.dp))
                    MoreTime(wait, passes, onMore)
                }
            }
        }
    }

    /** The gentle way out: a bar fills during the wait, then a pass opens the app for 5 minutes. */
    @Composable
    private fun MoreTime(wait: Int, passes: Int?, onMore: () -> Unit) {
        val none = passes == 0
        if (wait > 0 && !none) {
            ChunkyProgress(1f - wait / WAIT_SECONDS.toFloat(), Modifier.fillMaxWidth().padding(horizontal = 8.dp), color = Sp.colors.flame, height = 12.dp)
            Spacer(Modifier.height(8.dp))
            Text("Take a breath. $wait s", style = MaterialTheme.typography.titleSmall, color = Sp.colors.textDim)
        } else {
            ChunkyButton("Open for 5 more minutes", onMore, Modifier.fillMaxWidth(), kind = ButtonKind.SECONDARY, enabled = !none)
            Spacer(Modifier.height(6.dp))
            Text(
                when (passes) {
                    null -> ""
                    0 -> "No passes left today."
                    1 -> "This is your last pass today."
                    else -> "$passes passes left today."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Sp.colors.textDim,
            )
        }
    }
}

/** What Pebble says on the block screen. A few lines per kind keep it fresh. */
private fun pebbleLine(kind: BlockKind): Pair<Mood, String> {
    val (mood, lines) = when (kind) {
        BlockKind.FOCUS -> Mood.GUARD to listOf("Not now. You're focusing!", "Back to it. I believe in you.", "Nice try! Your focus comes first.")
        BlockKind.SCHEDULE -> Mood.GUARD to listOf("It's not time for this yet.", "Your plan says no. I agree.")
        BlockKind.LIMIT -> Mood.SLEEPY to listOf("That's enough for today.", "Your time is used up. See you tomorrow!")
        BlockKind.SHORTS -> Mood.GUARD to listOf("No endless scrolling!", "Shorts are closed. The rest is yours.")
        BlockKind.STUDY -> Mood.THINK to listOf("Study mode is on.", "Let's find something useful.")
        BlockKind.SITE -> Mood.THINK to listOf("Hmm, not this site.", "This site is on your list.")
        BlockKind.PROTECTION -> Mood.STRICT to listOf("Nice try. Your blocks stay on.", "Not today. Strict mode is on.")
        BlockKind.MULTI_WINDOW -> Mood.GUARD to listOf("One app at a time.")
    }
    return mood to lines.random()
}
