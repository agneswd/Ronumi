package dev.agneswd.ronumi.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.BlockMode
import dev.agneswd.ronumi.data.Schedule
import dev.agneswd.ronumi.data.updateSettings
import dev.agneswd.ronumi.focus.Focus
import dev.agneswd.ronumi.guard.minuteText
import dev.agneswd.ronumi.ui.design.ButtonKind
import dev.agneswd.ronumi.ui.design.ChunkyButton
import dev.agneswd.ronumi.ui.design.ChunkyCard
import dev.agneswd.ronumi.ui.design.ChunkyProgress
import dev.agneswd.ronumi.ui.design.DayPart
import dev.agneswd.ronumi.ui.design.DayPartIcon
import dev.agneswd.ronumi.ui.design.FloatingDots
import dev.agneswd.ronumi.ui.design.Mood
import dev.agneswd.ronumi.ui.design.NotificationScene
import dev.agneswd.ronumi.ui.design.Ronumi
import dev.agneswd.ronumi.ui.design.ShortsScene
import dev.agneswd.ronumi.ui.design.Sp
import dev.agneswd.ronumi.ui.design.StreakScene
import dev.agneswd.ronumi.ui.design.StrictScene
import dev.agneswd.ronumi.ui.design.appear
import dev.agneswd.ronumi.ui.design.popIn
import dev.agneswd.ronumi.usage.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Step { WELCOME, HELLO, ASK, PURPOSE, GOAL, DISTRACTIONS, WHEN, PLAN, SHORTS, NOTIFY, STRICT, STREAK, APPS, ACCESS, FIRST }

private data class Option(val icon: Int, @param:androidx.annotation.StringRes val labelRes: Int, val value: String)

private val purposes = listOf(
    Option(R.drawable.ic_activity_study, R.string.onboarding_study, "study"),
    Option(R.drawable.ic_activity_work, R.string.onboarding_work, "work"),
    Option(R.drawable.ic_activity_phone, R.string.onboarding_scroll_less, "scroll"),
    Option(R.drawable.ic_activity_sleep, R.string.onboarding_sleep_better, "sleep"),
    Option(R.drawable.ic_activity_calm, R.string.onboarding_feel_calmer, "calm"),
)

private val goals = listOf(
    Option(R.drawable.ic_activity_coffee, R.string.onboarding_goal_casual, "30"),
    Option(R.drawable.ic_activity_sprout, R.string.onboarding_goal_regular, "60"),
    Option(R.drawable.ic_activity_flame, R.string.onboarding_goal_serious, "120"),
    Option(R.drawable.ic_activity_rocket, R.string.onboarding_goal_intense, "240"),
)

private val distractions = listOf(
    Option(R.drawable.ic_video, R.string.onboarding_shorts_and_reels, "shorts"),
    Option(R.drawable.ic_video, R.string.onboarding_youtube_rabbit_holes, "youtube"),
    Option(R.drawable.ic_activity_social, R.string.onboarding_social_media_feeds, "social"),
    Option(R.drawable.ic_bell, R.string.onboarding_notifications, "notifications"),
    Option(R.drawable.ic_activity_game, R.string.onboarding_games, "games"),
    Option(R.drawable.ic_activity_steps, R.string.onboarding_distraction_start, "start"),
)

/** Apps that most people find distracting. Onboarding picks the installed ones first. */
val commonDistractions = setOf(
    "com.google.android.youtube", "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
    "com.snapchat.android", "com.facebook.katana", "com.twitter.android", "com.reddit.frontpage",
    "com.pinterest", "tv.twitch.android.app", "com.netflix.mediaclient", "com.discord",
)

/** The first-launch flow. Ronumi asks a few questions, sets up a plan, and asks for permissions. */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val view = androidx.compose.ui.platform.LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var finishing by rememberSaveable { mutableStateOf(false) }
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    var forward by remember { mutableStateOf(true) }
    var purpose by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }
    // Rotation keeps the answers along with the slide.
    var picked by rememberSaveable { mutableStateOf(setOf<String>()) }
    var dayPart by rememberSaveable { mutableStateOf<DayPart?>(null) }
    var noSchedule by rememberSaveable { mutableStateOf(false) }
    var apps by rememberSaveable { mutableStateOf<Set<String>?>(null) }
    val installed by produceState<List<InstalledApp>>(emptyList()) {
        value = withContext(Dispatchers.IO) { app.catalog.launchableApps() }
    }
    val access = rememberAccess()

    // Cues stop when the slide changes or the app leaves the foreground.
    LaunchedEffect(step, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            fun tick() { view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
            fun confirm() { view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK) }
            when (step) {
                Step.PLAN -> {
                    val checks = 2 + listOf("shorts" in picked, "notifications" in picked,
                        picked.any { it in setOf("youtube", "social", "games") }, dayPart != null).count { it }
                    kotlinx.coroutines.delay(400)
                    repeat(checks) {
                        tick()
                        kotlinx.coroutines.delay(220)
                    }
                }
                Step.SHORTS -> {
                    kotlinx.coroutines.delay(320)
                    confirm()
                    kotlinx.coroutines.delay(400)
                    tick()
                }
                Step.NOTIFY -> {
                    kotlinx.coroutines.delay(650)
                    repeat(3) {
                        tick()
                        kotlinx.coroutines.delay(933)
                    }
                }
                else -> {
                    kotlinx.coroutines.delay(350)
                    tick()
                }
            }
        }
    }

    fun go(next: Step) {
        forward = next.ordinal > step.ordinal
        step = next
    }
    fun next() = go(Step.entries[step.ordinal + 1])

    fun finish(firstFocus: Boolean) {
        if (finishing) return
        finishing = true
        val chosen = apps ?: installed.map { it.packageName }.filter { it in commonDistractions }.toSet()
        app.scope.launch {
            dayPart?.let { part ->
                app.dao.saveSchedule(Schedule(name = part.scheduleId, startMinute = part.start, endMinute = part.end, packages = chosen, mode = BlockMode.LISTED))
            }
            app.dao.updateSettings {
                it.copy(
                    onboarded = true,
                    purpose = purpose,
                    distractions = picked,
                    focusGoalMinutes = goal.toIntOrNull() ?: 60,
                    focusPackages = chosen,
                    blockYoutubeShorts = "shorts" in picked,
                    blockInstagramReels = "shorts" in picked,
                    heldPackages = if ("notifications" in picked) chosen else emptySet(),
                )
            }
            if (firstFocus) Focus.start(context, "First focus", minutes = 2)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    val questionSteps = Step.PURPOSE.ordinal..Step.ACCESS.ordinal
    Column(Modifier.fillMaxSize().background(Sp.colors.background).statusBarsPadding().navigationBarsPadding()) {
        if (step.ordinal in questionSteps) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable { go(Step.entries[step.ordinal - 1]) },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.onboarding_back), tint = Sp.colors.textDim, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(8.dp))
                ChunkyProgress(
                    (step.ordinal - questionSteps.first + 1f) / (questionSteps.last - questionSteps.first + 1),
                    Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
            }
        }
        AnimatedContent(
            step,
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(320)) { it * dir / 3 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(240)) { -it * dir / 3 } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { current ->
            when (current) {
                Step.WELCOME -> Welcome(::next)
                Step.HELLO -> Chat(stringResource(R.string.onboarding_intro), Mood.WAVE, ::next)
                Step.ASK -> Chat(stringResource(R.string.onboarding_questions_intro), Mood.THINK, ::next)
                Step.PURPOSE -> Question(stringResource(R.string.onboarding_purpose_question), purposes, setOf(purpose), multi = false, onPick = { purpose = it }, onNext = ::next)
                Step.GOAL -> Question(stringResource(R.string.onboarding_goal_question), goals, setOf(goal), multi = false, onPick = { goal = it }, onNext = ::next)
                Step.DISTRACTIONS -> Question(
                    stringResource(R.string.onboarding_distractions_question), distractions, picked, multi = true,
                    onPick = { v -> picked = if (v in picked) picked - v else picked + v }, onNext = ::next,
                )
                Step.WHEN -> WhenStep(dayPart, onPick = { dayPart = it; noSchedule = false }, onLater = { dayPart = null; noSchedule = true }, onNext = ::next)
                Step.PLAN -> PlanStep(goal, picked, dayPart, ::next)
                Step.SHORTS -> Slide(stringResource(R.string.onboarding_shorts_title), stringResource(R.string.onboarding_shorts_description), ::next) { ShortsScene() }
                Step.NOTIFY -> Slide(stringResource(R.string.onboarding_inbox_title), stringResource(R.string.onboarding_inbox_description), ::next) { NotificationScene() }
                Step.STRICT -> Slide(stringResource(R.string.onboarding_strict_title), stringResource(R.string.onboarding_strict_description), ::next) { StrictScene() }
                Step.STREAK -> Slide(stringResource(R.string.onboarding_build_a_streak), stringResource(R.string.onboarding_streak_description), ::next) { StreakScene() }
                Step.APPS -> AppsStep(installed, apps ?: installed.map { it.packageName }.filter { it in commonDistractions }.toSet(), onChange = { apps = it }, onNext = ::next)
                Step.ACCESS -> AccessStep(access, onNext = ::next)
                Step.FIRST -> FirstFocus(enabled = !finishing, onStart = { finish(firstFocus = true) }, onSkip = { finish(firstFocus = false) })
            }
        }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        FloatingDots(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Ronumi(Mood.WAVE, Modifier.popIn(), size = 200.dp)
            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.onboarding_app_name), style = MaterialTheme.typography.displayMedium, color = Sp.colors.brand, modifier = Modifier.appear(200))
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_welcome_tagline),
                style = MaterialTheme.typography.titleLarge,
                color = Sp.colors.textDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.appear(350),
            )
            Spacer(Modifier.weight(1f))
            ChunkyButton(stringResource(R.string.onboarding_get_started), onNext, Modifier.fillMaxWidth().appear(500))
        }
    }
}

@Composable
private fun Chat(text: String, mood: Mood, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.weight(1f))
        RonumiSays(text, mood, Modifier.fillMaxWidth(), side = false, ronumiSize = 170.dp)
        Spacer(Modifier.weight(1f))
        ChunkyButton(stringResource(R.string.onboarding_continue), onNext, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Question(title: String, options: List<Option>, selected: Set<String>, multi: Boolean, onPick: (String) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        RonumiSays(title, Mood.THINK, Modifier.fillMaxWidth().padding(vertical = 12.dp), ronumiSize = 84.dp)
        if (multi) Text(stringResource(R.string.onboarding_pick_all_that_fit), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim, modifier = Modifier.padding(bottom = 8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEachIndexed { i, option ->
                ChunkyCard(Modifier.fillMaxWidth().appear(i * 60), onClick = { onPick(option.value) }, selected = option.value in selected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(option.icon), null, tint = Sp.colors.brand, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(option.labelRes), style = MaterialTheme.typography.titleMedium, color = Sp.colors.text, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        ChunkyButton(stringResource(R.string.onboarding_continue), onNext, Modifier.fillMaxWidth().padding(vertical = 16.dp), enabled = selected.any { it.isNotBlank() })
    }
}

@Composable
private fun WhenStep(selected: DayPart?, onPick: (DayPart) -> Unit, onLater: () -> Unit, onNext: () -> Unit) {
    val use24 = rememberUse24Hour()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        RonumiSays(stringResource(R.string.onboarding_schedule_question), Mood.THINK, Modifier.fillMaxWidth().padding(vertical = 12.dp), ronumiSize = 84.dp)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DayPart.entries.forEachIndexed { i, part ->
                ChunkyCard(Modifier.fillMaxWidth().appear(i * 60), onClick = { onPick(part) }, selected = part == selected, contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DayPartIcon(part)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(part.labelRes), style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                            Text(stringResource(R.string.onboarding_schedule_time_range, minuteText(part.start, use24), minuteText(part.end, use24)), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.onboarding_schedule_description),
            style = MaterialTheme.typography.bodySmall,
            color = Sp.colors.textDim,
            modifier = Modifier.padding(top = 10.dp),
        )
        // Skipping stays visible on small screens instead of hiding under the list.
        ChunkyButton(stringResource(R.string.onboarding_continue), onNext, Modifier.fillMaxWidth().padding(top = 16.dp), enabled = selected != null)
        ChunkyButton(stringResource(R.string.onboarding_schedule_skip_button), { onLater(); onNext() }, Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = ButtonKind.GHOST, height = 46.dp)
    }
}

@Composable
private fun PlanStep(goal: String, picked: Set<String>, part: DayPart?, onNext: () -> Unit) {
    val use24 = rememberUse24Hour()
    val minutes = goal.toIntOrNull() ?: 60
    val items = buildList {
        add(R.drawable.ic_activity_focus to if (minutes < 60) pluralStringResource(R.plurals.onboarding_goal_minutes, minutes, minutes) else pluralStringResource(R.plurals.onboarding_goal_hours, minutes / 60, minutes / 60))
        if ("shorts" in picked) add(R.drawable.ic_video to stringResource(R.string.onboarding_plan_shorts))
        if ("notifications" in picked) add(R.drawable.ic_bell to stringResource(R.string.onboarding_plan_inbox))
        if ("youtube" in picked || "social" in picked || "games" in picked) add(R.drawable.ic_tab_blocks to stringResource(R.string.onboarding_plan_apps))
        if (part != null) add(R.drawable.ic_timer to stringResource(R.string.onboarding_plan_schedule_summary, stringResource(part.scheduleNameRes), minuteText(part.start, use24), minuteText(part.end, use24)))
        add(R.drawable.ic_activity_flame to stringResource(R.string.onboarding_plan_streak))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        RonumiSays(stringResource(R.string.onboarding_plan_intro), Mood.PROUD, Modifier.fillMaxWidth().padding(vertical = 12.dp), ronumiSize = 84.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEachIndexed { i, (icon, text) ->
                Row(Modifier.fillMaxWidth().appear(200 + i * 220), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Sp.colors.brandSoft), contentAlignment = Alignment.Center) {
                        Icon(painterResource(icon), null, tint = Sp.colors.brand, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(text, style = MaterialTheme.typography.titleMedium, color = Sp.colors.text, modifier = Modifier.weight(1f))
                    Box(Modifier.popIn(400 + i * 220).size(28.dp).clip(RoundedCornerShape(14.dp)).background(Sp.colors.mint), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_check), null, tint = Sp.colors.onFill, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        ChunkyButton(stringResource(R.string.onboarding_sounds_great), onNext, Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
}

@Composable
private fun Slide(title: String, body: String, onNext: () -> Unit, art: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.6f))
        Box(Modifier.appear(0)) { art() }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, color = Sp.colors.text, textAlign = TextAlign.Center, modifier = Modifier.appear(120))
        Spacer(Modifier.height(10.dp))
        Text(body, style = MaterialTheme.typography.titleMedium, color = Sp.colors.textDim, textAlign = TextAlign.Center, modifier = Modifier.appear(220))
        Spacer(Modifier.weight(1f))
        ChunkyButton(stringResource(R.string.onboarding_continue), onNext, Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
}

@Composable
private fun AppsStep(installed: List<InstalledApp>, chosen: Set<String>, onChange: (Set<String>) -> Unit, onNext: () -> Unit) {
    // Suggested apps first, then the rest by name.
    val sorted = remember(installed) { installed.sortedBy { if (it.packageName in commonDistractions) 0 else 1 } }
    Column(Modifier.fillMaxSize()) {
        RonumiSays(stringResource(R.string.onboarding_apps_question), Mood.THINK, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), ronumiSize = 84.dp)
        Text(
            pluralStringResource(R.plurals.onboarding_apps_selected, chosen.size, chosen.size),
            style = MaterialTheme.typography.bodyMedium,
            color = Sp.colors.textDim,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(sorted, key = { it.packageName }) { item ->
                val on = item.packageName in chosen
                ListRow(item.label, onClick = { onChange(if (on) chosen - item.packageName else chosen + item.packageName) }, leading = { AppIcon(item.packageName) }) {
                    Checkbox(on, { onChange(if (on) chosen - item.packageName else chosen + item.packageName) }, colors = CheckboxDefaults.colors(checkedColor = Sp.colors.brand))
                }
            }
        }
        ChunkyButton(stringResource(R.string.onboarding_continue), onNext, Modifier.fillMaxWidth().padding(20.dp))
    }
}

@Composable
private fun AccessStep(access: Access, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        RonumiSays(
            if (access.ready) stringResource(R.string.onboarding_permissions_ready) else stringResource(R.string.onboarding_permissions_missing),
            if (access.ready) Mood.CELEBRATE else Mood.IDLE,
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ronumiSize = 84.dp,
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            AccessRows(access, includeOptional = true)
            Text(
                stringResource(R.string.onboarding_privacy_description),
                style = MaterialTheme.typography.bodySmall,
                color = Sp.colors.textDim,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        ChunkyButton(stringResource(R.string.onboarding_continue), onNext, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = access.ready)
        ChunkyButton(stringResource(R.string.onboarding_skip_for_now), onNext, Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = ButtonKind.GHOST)
    }
}

@Composable
private fun FirstFocus(enabled: Boolean, onStart: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.weight(1f))
        RonumiSays(stringResource(R.string.onboarding_first_session_intro), Mood.HAPPY, Modifier.fillMaxWidth(), side = false, ronumiSize = 170.dp)
        Spacer(Modifier.weight(1f))
        ChunkyButton(stringResource(R.string.onboarding_first_session_start_button), onStart, Modifier.fillMaxWidth(), kind = ButtonKind.MINT, icon = painterResource(R.drawable.ic_play), enabled = enabled)
        ChunkyButton(stringResource(R.string.onboarding_maybe_later), onSkip, Modifier.fillMaxWidth().padding(top = 4.dp), kind = ButtonKind.GHOST, enabled = enabled)
    }
}
