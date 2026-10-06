package dev.agneswd.ronumi.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import dev.agneswd.ronumi.ui.design.Sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.settings
import dev.agneswd.ronumi.ui.design.RonumiTheme

/** Opens the GitHub update notification without adding update routes to the Play app. */
class UpdatesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by app.dao.settings().collectAsState(null)
            RonumiTheme(themeMode = settings?.themeMode ?: "SYSTEM") {
                val dark = Sp.colors.dark
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                Box(Modifier.fillMaxSize().background(Sp.colors.background).statusBarsPadding().navigationBarsPadding()) {
                    UpdatesScreen(onClose = ::finish)
                }
            }
        }
    }
}
