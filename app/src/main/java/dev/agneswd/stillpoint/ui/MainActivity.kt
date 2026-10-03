package dev.agneswd.stillpoint.ui

import dev.agneswd.stillpoint.ui.design.Sound
import dev.agneswd.stillpoint.ui.design.Sfx
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.AppLimit
import dev.agneswd.stillpoint.data.Schedule
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.focus.Celebrations
import dev.agneswd.stillpoint.game.GameState
import dev.agneswd.stillpoint.game.applyStreakFreezes
import dev.agneswd.stillpoint.game.gameState
import dev.agneswd.stillpoint.ui.design.Sp
import dev.agneswd.stillpoint.ui.design.StillpointTheme
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: Int) {
    HOME("Home", R.drawable.ic_tab_home),
    PLANNER("Planner", R.drawable.ic_tab_planner),
    BLOCKS("Blocks", R.drawable.ic_tab_blocks),
    PROGRESS("Progress", R.drawable.ic_tab_progress),
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
        var draft by mutableStateOf(original ?: Schedule(name = "Evening focus", startMinute = 18 * 60, endMinute = 20 * 60))
    }

    data object Wardrobe : Route
    data object Held : Route
    data object Settings : Route
    data object Updates : Route
    data object FocusSetup : Route
    data object ShortVideos : Route
    data object Websites : Route
    data object Notifications : Route
    data object Strict : Route
}

/** Opens new screens. The tabs and editors get it instead of a navigation library. */
class Navigator : androidx.lifecycle.ViewModel() {
    val settingsScroll = androidx.compose.foundation.ScrollState(0)
    var showPermissions by mutableStateOf(false)
    var tab by mutableStateOf(Tab.HOME)
    val stack = mutableStateListOf<Route>()

    /** The limit in the limit dialog. It lives here so it survives a trip to the app picker. */
    var editingLimit by mutableStateOf<AppLimit?>(null)

    /** True when the user left a running session to look at the tabs. */
    var focusMinimized by mutableStateOf(false)

    fun push(route: Route) {
        if (stack.lastOrNull() != route) stack.add(route)
    }

    fun pop() {
        stack.removeLastOrNull()
    }

    /** Closes focus setup and the screens opened from it. Screens under it stay. */
    fun closeFocusSetup() {
        val index = stack.indexOf(Route.FocusSetup)
        if (index >= 0) stack.removeRange(index, stack.size)
    }
}

class MainActivity : ComponentActivity() {
    private val navigator by viewModels<Navigator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openFrom(intent)
        setContent {
            val themeSettings by app.dao.settings().collectAsState(null)
            val themeMode = themeSettings?.themeMode ?: return@setContent
            StillpointTheme(themeMode = themeMode) {
                KeyboardDismissHost { App(navigator) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            applyStreakFreezes(app.dao)
            dev.agneswd.stillpoint.focus.Focus.recover(this@MainActivity)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (app.usage.hasAccess()) app.usage.recentDays(7) else emptyList()
            }.forEach { usage ->
                app.dao.recordUsage(dev.agneswd.stillpoint.data.UsageDay(usage.date.toString(), usage.perApp.toMap(), usage.unlocks))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    private fun openFrom(intent: Intent) {
        if (intent.getBooleanExtra(dev.agneswd.stillpoint.update.UpdateScheduler.SHOW_UPDATES, false)) {
            navigator.focusMinimized = true
            navigator.stack.clear()
            navigator.push(Route.Updates)
            return
        }
        when (val target = intent.getStringExtra(EXTRA_TAB)) {
            null -> Unit
            FOCUS -> {
                navigator.focusMinimized = false
                navigator.stack.clear()
                // Without a running session, this opens the setup controls.
                lifecycleScope.launch {
                    // Only the plan notification can start a plan. Other apps cannot open the private alias.
                    val planId = if (intent.component?.className == PLAN_START) intent.getLongExtra(EXTRA_PLAN, 0) else 0
                    val plan = app.dao.allSchedules().firstOrNull { it.id == planId && it.enabled && it.startFocus }
                    if (plan != null) dev.agneswd.stillpoint.focus.Focus.start(this@MainActivity, plan.name, plan = plan)
                    else if (app.dao.activeFocus() == null) navigator.push(Route.FocusSetup)
                }
            }
            "INBOX" -> {
                navigator.focusMinimized = true
                navigator.stack.clear()
                navigator.push(Route.Held)
            }
            else -> Tab.entries.firstOrNull { it.name == target }?.let {
                navigator.tab = it
                navigator.focusMinimized = true
                navigator.stack.clear()
            }
        }
    }

    companion object {
        private const val EXTRA_TAB = "tab"
        private const val FOCUS = "FOCUS"
        private const val EXTRA_PLAN = "planId"
        private const val PLAN_START = "dev.agneswd.stillpoint.ui.PlanStart"

        fun intent(context: Context, target: String): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(EXTRA_TAB, target)

        /** Opens the running session, or the home tab when nothing runs. */
        fun focusIntent(context: Context) = intent(context, FOCUS)

        fun pendingFocus(context: Context): PendingIntent =
            PendingIntent.getActivity(context, 10, focusIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun pendingInbox(context: Context): PendingIntent =
            PendingIntent.getActivity(context, 12, intent(context, "INBOX"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun pendingPlan(context: Context, id: Long): PendingIntent =
            PendingIntent.getActivity(
                context, 100 + id.toInt(),
                focusIntent(context).setClassName(context, PLAN_START).putExtra(EXTRA_PLAN, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun pendingHome(context: Context): PendingIntent =
            PendingIntent.getActivity(context, 11, intent(context, Tab.HOME.name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

@Composable
private fun App(navigator: Navigator) {
    val context = LocalContext.current
    val dao = context.app.dao
    val settings by dao.settings().collectAsState(null)
    val focus by dao.activeFocusFlow().collectAsState(null)
    val sessions by dao.sessions().collectAsState(emptyList())
    val celebrate by Celebrations.pending.collectAsState()
    var today by remember { mutableStateOf(java.time.LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val date = java.time.LocalDate.now()
            if (date != today) {
                applyStreakFreezes(dao, date)
                today = date
            }
            kotlinx.coroutines.delay(30_000)
        }
    }
    val game = remember(sessions, settings, today) { settings?.let { gameState(sessions, it, today) } }

    // A session can start from setup, a plan, or a widget. Setup must not offer a second one.
    val setupOpen = Route.FocusSetup in navigator.stack
    LaunchedEffect(focus == null, setupOpen) {
        if (focus == null) navigator.focusMinimized = false
        else if (setupOpen) {
            navigator.closeFocusSetup()
            navigator.focusMinimized = false
        }
    }

    val s = settings
    val dark = Sp.colors.dark
    val focusVisible = focus != null && !navigator.focusMinimized && navigator.stack.isEmpty() && celebrate == null
    SideEffect {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark && !focusVisible
                isAppearanceLightNavigationBars = !dark && !focusVisible
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        dev.agneswd.stillpoint.ui.design.LocalPebbleStyle provides
            dev.agneswd.stillpoint.game.PebbleStyles.resolve(s?.pebbleItems.orEmpty(), game?.level?.number ?: 1, s?.petTapCount ?: 0),
    ) {
        Box(Modifier.fillMaxSize().background(Sp.colors.background)) {
            SecretReveal()
            val running = focus
            val celebrateId = celebrate
            val route = navigator.stack.lastOrNull()
            when {
                s == null -> Unit
                !s.onboarded -> Onboarding(onDone = { navigator.tab = Tab.HOME })
                celebrateId != null -> Celebration(celebrateId, onDone = Celebrations::consume)
                else -> {
                    // Keep the outgoing route until the focus screen covers it.
                    val fullFocus = running?.takeIf { !navigator.focusMinimized && route == null }
                    AnimatedContent(
                        targetState = fullFocus to route,
                        contentKey = { (session, _) -> session != null },
                        transitionSpec = {
                            (fadeIn(tween(420)) + scaleIn(
                                tween(420, easing = FastOutSlowInEasing), initialScale = 0.985f,
                            )) togetherWith fadeOut(tween(260))
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "focusScreen",
                    ) { (session, visibleRoute) ->
                        if (session != null) {
                            FocusSession(session, onMinimize = { navigator.focusMinimized = true })
                        } else {
                            Box(Modifier.fillMaxSize()) {
                                BackHandler(enabled = visibleRoute != null) { navigator.pop() }
                                AnimatedContent(
                                    visibleRoute,
                                    transitionSpec = { (slideInVertically(tween(260)) { it / 8 } + fadeIn(tween(260))) togetherWith fadeOut(tween(160)) },
                                    label = "route",
                                ) { current ->
                                    when (current) {
                                        is Route.PickApps -> Page { AppPicker(current, onClose = navigator::pop) }
                                        is Route.EditSchedule -> Page { ScheduleEditor(current, onClose = navigator::pop, navigator = navigator) }
                                        Route.Wardrobe -> Page { game?.let { WardrobeScreen(it, navigator::pop) } }
                                        Route.Held -> Page { HeldScreen(onClose = navigator::pop) }
                                        Route.Settings -> Page { SettingsScreen(navigator, onClose = navigator::pop) }
                                        Route.Updates -> Page { UpdatesScreen(onClose = navigator::pop) }
                                        Route.FocusSetup -> FocusSetup(navigator, onClose = navigator::pop)
                                        Route.ShortVideos -> Page { ShortVideosPage(onClose = navigator::pop) }
                                        Route.Websites -> Page { WebsitesPage(onClose = navigator::pop) }
                                        Route.Notifications -> Page { NotificationsPage(navigator, onClose = navigator::pop) }
                                        Route.Strict -> Page { StrictPage(onClose = navigator::pop) }
                                        null -> Tabs(navigator, game)
                                    }
                                }
                                if (running != null && visibleRoute == null) {
                                    FocusChip(
                                        running,
                                        onOpen = { navigator.focusMinimized = false },
                                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Sp.colors.background).statusBarsPadding().navigationBarsPadding()) { content() }
}

@Composable
private fun Tabs(navigator: Navigator, game: GameState?) {
    Column(Modifier.fillMaxSize().background(Sp.colors.background)) {
        Box(Modifier.weight(1f).statusBarsPadding()) {
            AnimatedContent(navigator.tab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "tab") { tab ->
                when (tab) {
                    Tab.HOME -> HomeScreen(navigator, game)
                    Tab.PLANNER -> PlannerScreen(navigator)
                    Tab.BLOCKS -> BlocksScreen(navigator)
                    Tab.PROGRESS -> ProgressScreen(navigator, game)
                }
            }
        }
        TabBar(navigator)
    }
}

/** A bottom bar in the style of learning games: big icons, the chosen one in a soft outlined box. */
@Composable
private fun TabBar(navigator: Navigator) {
    Column(Modifier.fillMaxWidth().background(Sp.colors.background)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Sp.colors.border))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Tab.entries.forEach { tab ->
                val on = navigator.tab == tab
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            navigator.tab = tab
                        }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(58.dp, 40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) Sp.colors.brandSoft else Sp.colors.background)
                            .border(2.dp, if (on) Sp.colors.brand else Sp.colors.background, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(tab.icon), tab.label, tint = if (on) Sp.colors.brand else Sp.colors.textDim, modifier = Modifier.size(26.dp))
                    }
                    Text(tab.label, style = MaterialTheme.typography.labelSmall, color = if (on) Sp.colors.brand else Sp.colors.textDim)
                }
            }
        }
    }
}
