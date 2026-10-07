package dev.agneswd.ronumi.ui

import androidx.compose.ui.res.stringResource
import dev.agneswd.ronumi.ui.design.Sound
import dev.agneswd.ronumi.ui.design.Sfx
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import dev.agneswd.ronumi.Distribution
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.AppLimit
import dev.agneswd.ronumi.data.Schedule
import dev.agneswd.ronumi.data.settings
import dev.agneswd.ronumi.focus.Celebrations
import dev.agneswd.ronumi.game.GameState
import dev.agneswd.ronumi.game.applyStreakFreezes
import dev.agneswd.ronumi.game.gameState
import dev.agneswd.ronumi.ui.design.Nunito
import dev.agneswd.ronumi.ui.design.Sp
import dev.agneswd.ronumi.ui.design.RonumiTheme
import kotlinx.coroutines.launch

enum class Tab(@param:androidx.annotation.StringRes val labelRes: Int, val icon: Int) {
    HOME(R.string.navigation_home, R.drawable.ic_tab_home),
    PLANNER(R.string.navigation_planner, R.drawable.ic_tab_planner),
    BLOCKS(R.string.navigation_blocks, R.drawable.ic_tab_blocks),
    PROGRESS(R.string.navigation_progress, R.drawable.ic_tab_progress),
}

/** A screen on top of the tabs. The back button removes it. */
sealed interface Route {
    /** [limit] caps the selection without Plus. A tap past it shows [onLimit] in the list, not a dialog. */
    data class PickApps(
        val title: String,
        val selected: Set<String>,
        val single: Boolean,
        val limit: Int? = null,
        val onLimit: () -> Unit = {},
        val onDone: (Set<String>) -> Unit,
    ) : Route

    /** The draft lives in the route, so it survives a trip to the app picker. */
    class EditSchedule(val original: Schedule?) : Route {
        var draft by mutableStateOf(original ?: Schedule(name = dev.agneswd.ronumi.ui.design.DayPart.EVENING.scheduleId, startMinute = 18 * 60, endMinute = 20 * 60))
    }

    data object Wardrobe : Route
    data object Held : Route
    data object Settings : Route
    data object Legal : Route
    /** The Ronumi Plus paywall. [first] is the feature that opened it, or null from Settings. */
    data class Plus(val first: dev.agneswd.ronumi.plus.PlusFeature?) : Route
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
            RonumiTheme(themeMode = themeMode) {
                KeyboardDismissHost { App(navigator) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            applyStreakFreezes(app.dao)
            dev.agneswd.ronumi.focus.Focus.recover(this@MainActivity)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                app.usageRefresh.refresh(7)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    private fun openFrom(intent: Intent) {
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
                    if (plan != null) dev.agneswd.ronumi.focus.Focus.start(this@MainActivity, plan.name, plan = plan)
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
        private const val PLAN_START = "dev.agneswd.ronumi.ui.PlanStart"

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
        dev.agneswd.ronumi.ui.design.LocalRonumiStyle provides
            dev.agneswd.ronumi.game.RonumiStyles.resolve(s?.pebbleItems.orEmpty(), game?.level?.number ?: 1, s?.petTapCount ?: 0, rememberHasPlus()),
    ) {
        Box(Modifier.fillMaxSize().background(Sp.colors.background)) {
            SecretReveal()
            val importing = Distribution.ImportOffer()
            val running = focus
            val celebrateId = celebrate
            val route = navigator.stack.lastOrNull()
            when {
                importing != false -> Unit
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
                                        Route.Wardrobe -> Page { game?.let { WardrobeScreen(it, navigator::pop, onPlus = { navigator.push(Route.Plus(dev.agneswd.ronumi.plus.PlusFeature.PLUS_WARDROBE)) }) } }
                                        Route.Held -> Page { HeldScreen(onClose = navigator::pop) }
                                        Route.Settings -> Page { SettingsScreen(navigator, onClose = navigator::pop) }
                                        Route.Legal -> Page { LegalNoticesScreen(onClose = navigator::pop) }
                                        is Route.Plus -> Page { PlusScreen(current.first, onClose = navigator::pop) }
                                        Route.FocusSetup -> FocusSetup(navigator, onClose = navigator::pop)
                                        Route.ShortVideos -> Page { ShortVideosPage(onClose = navigator::pop) }
                                        Route.Websites -> Page { WebsitesPage(onClose = navigator::pop) }
                                        Route.Notifications -> Page { NotificationsPage(navigator, onClose = navigator::pop) }
                                        Route.Strict -> Page { StrictPage(onClose = navigator::pop) }
                                        null -> Tabs(navigator, game, running)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            ConsentHost()
        }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Sp.colors.background).statusBarsPadding().navigationBarsPadding()) { content() }
}

@Composable
private fun Tabs(navigator: Navigator, game: GameState?, running: dev.agneswd.ronumi.data.ActiveFocus?) {
    Column(Modifier.fillMaxSize().background(Sp.colors.background)) {
        // A minimized session keeps the chip in the layout, so it does not cover the tab.
        if (running != null) {
            Box(
                Modifier.fillMaxWidth().statusBarsPadding().padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                FocusChip(running, onOpen = { navigator.focusMinimized = false })
            }
        }
        Box(Modifier.weight(1f).then(if (running == null) Modifier.statusBarsPadding() else Modifier)) {
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

private val TabLabel = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.W700, fontSize = 12.sp, lineHeight = 16.sp)

/** Bottom tabs. The active one is a soft pill. The row is the touch target. */
@Composable
private fun TabBar(navigator: Navigator) {
    Column(Modifier.fillMaxWidth().background(Sp.colors.background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Sp.colors.border))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp)) {
            Tab.entries.forEach { tab ->
                val on = navigator.tab == tab
                Column(
                    Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .semantics { selected = on }
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                            navigator.tab = tab
                        }
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(56.dp, 32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) Sp.colors.brandSoft else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(tab.icon), null, tint = if (on) Sp.colors.brand else Sp.colors.textDim, modifier = Modifier.size(24.dp))
                    }
                    Text(
                        stringResource(tab.labelRes),
                        style = TabLabel,
                        color = if (on) Sp.colors.brand else Sp.colors.textDim,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
