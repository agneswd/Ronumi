package dev.agneswd.stillpoint.guard

import dev.agneswd.stillpoint.ui.design.NumberStyle
import dev.agneswd.stillpoint.ui.design.StillpointTheme
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import dev.agneswd.stillpoint.app
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast

/** The full-screen block. The guard opens it over a blocked app, feed or site. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        render()
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
        // The E2E test reads this line. UI dumps would pause the guard, so it cannot read the screen.
        Log.i("Stillpoint", "block shown: $title")
        // Most blocks leave the user on the home screen. A Shorts block returns to the rest of the app.
        val leave = { if (kind in homeKinds) goHome() else finish() }
        setContent {
            StillpointTheme {
                BackHandler(onBack = leave)
                BlockScreen(
                    icon = remember(pkg) { app.catalog.icon(pkg) },
                    title = title,
                    detail = detail,
                    gentle = gentle,
                    onClose = leave,
                    onMore = {
                        app.scope.launch {
                            val granted = PolicyActions.grantExtra(this@BlockActivity, pkg)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
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

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_DETAIL = "detail"
        private const val EXTRA_GENTLE = "gentle"
        private const val WAIT_SECONDS = 10
        private val homeKinds = setOf(BlockKind.FOCUS, BlockKind.SCHEDULE, BlockKind.LIMIT, BlockKind.SITE, BlockKind.PROTECTION, BlockKind.MULTI_WINDOW)

        fun intent(context: Context, pkg: String, reason: BlockReason): Intent =
            Intent(context, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_PACKAGE, pkg)
                .putExtra(EXTRA_KIND, reason.kind.name)
                .putExtra(EXTRA_TITLE, reason.title)
                .putExtra(EXTRA_DETAIL, reason.detail)
                .putExtra(EXTRA_GENTLE, reason.gentle)
    }

    @Composable
    private fun BlockScreen(
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
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding().padding(horizontal = 32.dp, vertical = 48.dp)) {
                if (icon != null) {
                    Image(icon.asImageBitmap(), null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.height(28.dp))
                }
                Text(title, style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(16.dp))
                Text(detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Close")
                }
                if (gentle) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onMore, enabled = wait == 0, modifier = Modifier.fillMaxWidth()) {
                        Text(if (wait > 0) "Wait $wait s for 5 more minutes" else "Open for 5 more minutes")
                    }
                }
            }
        }
    }
}
