package dev.agneswd.stillpoint.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.data.AppLimit
import dev.agneswd.stillpoint.data.Schedule

enum class Tab(val label: String, val icon: Int) {
    TODAY("Today", R.drawable.ic_tab_today),
    FOCUS("Focus", R.drawable.ic_tab_focus),
    BLOCKS("Blocks", R.drawable.ic_tab_blocks),
    SETUP("Setup", R.drawable.ic_tab_setup),
}

/** A screen on top of the tabs. The back button removes it. */
sealed interface Route {
    data class PickApps(
        val title: String,
        val selected: Set<String>,
        val single: Boolean,
        val onDone: (Set<String>) -> Unit,
    ) : Route

    /** The draft lives in the route, so it survives a trip to the app picker. */
    class EditSchedule(val original: Schedule?) : Route {
        var draft by mutableStateOf(original ?: Schedule(name = "Evening", startMinute = 21 * 60, endMinute = 7 * 60))
    }

    data object Held : Route
}

/** Opens new screens. The tabs and editors get it instead of a navigation library. */
class Navigator {
    var tab by mutableStateOf(Tab.TODAY)
    val stack = mutableStateListOf<Route>()

    /** The limit in the limit dialog. It lives here so it survives a trip to the app picker. */
    var editingLimit by mutableStateOf<AppLimit?>(null)

    fun push(route: Route) = stack.add(route)

    fun pop() {
        stack.removeLastOrNull()
    }
}

class MainActivity : ComponentActivity() {
    private val navigator = Navigator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openTabFrom(intent)
        setContent {
            StillpointTheme {
                App(navigator)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openTabFrom(intent)
    }

    private fun openTabFrom(intent: Intent) {
        intent.getStringExtra(EXTRA_TAB)?.let { name ->
            navigator.tab = Tab.valueOf(name)
            navigator.stack.clear()
        }
    }

    companion object {
        private const val EXTRA_TAB = "tab"

        fun intent(context: Context, tab: Tab): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(EXTRA_TAB, tab.name)

        fun focusIntent(context: Context) = intent(context, Tab.FOCUS)

        fun pendingFocus(context: Context): PendingIntent =
            PendingIntent.getActivity(context, 10, focusIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun pendingHome(context: Context): PendingIntent =
            PendingIntent.getActivity(context, 11, intent(context, Tab.TODAY), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

@Composable
private fun App(navigator: Navigator) {
    val route = navigator.stack.lastOrNull()
    BackHandler(enabled = route != null) { navigator.pop() }
    when (route) {
        is Route.PickApps -> AppPicker(route, onClose = navigator::pop)
        is Route.EditSchedule -> ScheduleEditor(route, onClose = navigator::pop, navigator = navigator)
        Route.Held -> HeldScreen(onClose = navigator::pop)
        null -> Tabs(navigator)
    }
}

@Composable
private fun Tabs(navigator: Navigator) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = navigator.tab == tab,
                        onClick = { navigator.tab = tab },
                        icon = { Icon(painterResource(tab.icon), null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (navigator.tab) {
                Tab.TODAY -> TodayScreen(navigator)
                Tab.FOCUS -> FocusScreen(navigator)
                Tab.BLOCKS -> BlocksScreen(navigator)
                Tab.SETUP -> SetupScreen(navigator)
            }
        }
    }
}
