package dev.agneswd.stillpoint.ui

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
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.BlockMode
import dev.agneswd.stillpoint.data.Schedule
import dev.agneswd.stillpoint.data.updateSettings
import dev.agneswd.stillpoint.focus.Focus
import dev.agneswd.stillpoint.guard.minuteText
import dev.agneswd.stillpoint.ui.design.ButtonKind
import dev.agneswd.stillpoint.ui.design.ChunkyButton
import dev.agneswd.stillpoint.ui.design.ChunkyCard
import dev.agneswd.stillpoint.ui.design.ChunkyProgress
import dev.agneswd.stillpoint.ui.design.DayPart
import dev.agneswd.stillpoint.ui.design.DayPartIcon
import dev.agneswd.stillpoint.ui.design.FloatingDots
import dev.agneswd.stillpoint.ui.design.Mood
import dev.agneswd.stillpoint.ui.design.NotificationScene
import dev.agneswd.stillpoint.ui.design.Pebble
import dev.agneswd.stillpoint.ui.design.ShortsScene
import dev.agneswd.stillpoint.ui.design.Sp
import dev.agneswd.stillpoint.ui.design.StreakScene
import dev.agneswd.stillpoint.ui.design.StrictScene
import dev.agneswd.stillpoint.ui.design.appear
import dev.agneswd.stillpoint.ui.design.popIn
import dev.agneswd.stillpoint.usage.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Step { WELCOME, HELLO, ASK, PURPOSE, GOAL, DISTRACTIONS, WHEN, PLAN, SHORTS, NOTIFY, STRICT, STREAK, APPS, ACCESS, FIRST }

private data class Option(val emoji: String, val label: String, val value: String)

private val purposes = listOf(
    Option("📚", "Study", "study"),
    Option("💼", "Work", "work"),
    Option("📱", "Scroll less", "scroll"),
    Option("😴", "Sleep better", "sleep"),
    Option("🧘", "Feel calmer", "calm"),
)

private val goals = listOf(
    Option("☕", "Casual, 30 min a day", "30"),
    Option("🌱", "Regular, 1 hour a day", "60"),
    Option("🔥", "Serious, 2 hours a day", "120"),
    Option("🚀", "Intense, 4 hours a day", "240"),
)

private val distractions = listOf(
    Option("🎬", "Shorts and Reels", "shorts"),
    Option("📺", "YouTube rabbit holes", "youtube"),
    Option("💬", "Social media feeds", "social"),
    Option("🔔", "Notifications", "notifications"),
    Option("🎮", "Games", "games"),
    Option("🐢", "I can't get started", "start"),
)

/** Apps that most people find distracting. Onboarding picks the installed ones first. */
val commonDistractions = setOf(
    "com.google.android.youtube", "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
    "com.snapchat.android", "com.facebook.katana", "com.twitter.android", "com.reddit.frontpage",
    "com.pinterest", "tv.twitch.android.app", "com.netflix.mediaclient", "com.discord",
)

/** The first-launch flow. Pebble asks a few questions, sets up a plan, and asks for permissions. */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    var forward by remember { mutableStateOf(true) }
    var purpose by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var dayPart by remember { mutableStateOf<DayPart?>(null) }
    var noSchedule by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<Set<String>?>(null) }
    val installed by produceState<List<InstalledApp>>(emptyList()) {
        value = withContext(Dispatchers.IO) { app.catalog.launchableApps() }
    }
    val access = rememberAccess()

    fun go(next: Step) {
        forward = next.ordinal > step.ordinal
        step = next
    }
    fun next() = go(Step.entries[step.ordinal + 1])

    fun finish(firstFocus: Boolean) {
        val chosen = apps ?: installed.map { it.packageName }.filter { it in commonDistractions }.toSet()
        app.scope.launch {
            dayPart?.let { part ->
                app.dao.saveSchedule(Schedule(name = "${part.label} focus", startMinute = part.start, endMinute = part.end, packages = chosen, mode = BlockMode.LISTED))
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
                ) { Icon(painterResource(R.drawable.ic_close), "Back", tint = Sp.colors.textDim, modifier = Modifier.size(20.dp)) }
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
                Step.HELLO -> Chat("Hi! I'm Pebble. I help you scroll less and focus more.", Mood.WAVE, ::next)
                Step.ASK -> Chat("First, a few quick questions. Then I'll build a plan just for you.", Mood.THINK, ::next)
                Step.PURPOSE -> Question("What do you want help with?", purposes, setOf(purpose), multi = false, onPick = { purpose = it }, onNext = ::next)
                Step.GOAL -> Question("Pick a daily focus goal", goals, setOf(goal), multi = false, onPick = { goal = it }, onNext = ::next)
                Step.DISTRACTIONS -> Question(
                    "What pulls you away the most?", distractions, picked, multi = true,
                    onPick = { v -> picked = if (v in picked) picked - v else picked + v }, onNext = ::next,
                )
                Step.WHEN -> WhenStep(dayPart, onPick = { dayPart = it; noSchedule = false }, onLater = { dayPart = null; noSchedule = true }, onNext = ::next)
                Step.PLAN -> PlanStep(goal, picked, dayPart, ::next)
                Step.SHORTS -> Slide("Scrolling Shorts?", "Pebble closes the feed. The rest of the app still works.", ::next) { ShortsScene() }
                Step.NOTIFY -> Slide("Buzz, buzz, buzz?", "Notifications wait in a box until you finish.", ::next) { NotificationScene() }
                Step.STRICT -> Slide("Want to quit early?", "Strict mode won't let you. Future you says thanks.", ::next) { StrictScene() }
                Step.STREAK -> Slide("Build a streak", "Focus a little every day. Keep the flame alive.", ::next) { StreakScene() }
                Step.APPS -> AppsStep(installed, apps ?: installed.map { it.packageName }.filter { it in commonDistractions }.toSet(), onChange = { apps = it }, onNext = ::next)
                Step.ACCESS -> AccessStep(access, onNext = ::next)
                Step.FIRST -> FirstFocus(onStart = { finish(firstFocus = true) }, onSkip = { finish(firstFocus = false) })
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
            Pebble(Mood.WAVE, Modifier.popIn(), size = 200.dp)
            Spacer(Modifier.height(28.dp))
            Text("Stillpoint", style = MaterialTheme.typography.displayMedium, color = Sp.colors.brand, modifier = Modifier.appear(200))
            Spacer(Modifier.height(8.dp))
            Text(
                "Less scrolling. More living.",
                style = MaterialTheme.typography.titleLarge,
                color = Sp.colors.textDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.appear(350),
            )
            Spacer(Modifier.weight(1f))
            ChunkyButton("Get started", onNext, Modifier.fillMaxWidth().appear(500))
            Spacer(Modifier.height(12.dp))
            Text(
                "Free, offline and private. No account needed.",
                style = MaterialTheme.typography.bodySmall,
                color = Sp.colors.textDim,
                modifier = Modifier.appear(600),
            )
        }
    }
}

@Composable
private fun Chat(text: String, mood: Mood, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.weight(1f))
        PebbleSays(text, mood, Modifier.fillMaxWidth(), side = false, pebbleSize = 170.dp)
        Spacer(Modifier.weight(1f))
        ChunkyButton("Continue", onNext, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Question(title: String, options: List<Option>, selected: Set<String>, multi: Boolean, onPick: (String) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PebbleSays(title, Mood.THINK, Modifier.fillMaxWidth().padding(vertical = 12.dp), pebbleSize = 84.dp)
        if (multi) Text("Pick all that fit.", style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim, modifier = Modifier.padding(bottom = 8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEachIndexed { i, option ->
                ChunkyCard(Modifier.fillMaxWidth().appear(i * 60), onClick = { onPick(option.value) }, selected = option.value in selected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(option.emoji, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(16.dp))
                        Text(option.label, style = MaterialTheme.typography.titleMedium, color = Sp.colors.text, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        ChunkyButton("Continue", onNext, Modifier.fillMaxWidth().padding(vertical = 16.dp), enabled = selected.any { it.isNotBlank() })
    }
}

@Composable
private fun WhenStep(selected: DayPart?, onPick: (DayPart) -> Unit, onLater: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PebbleSays("When do you want to focus each day?", Mood.THINK, Modifier.fillMaxWidth().padding(vertical = 12.dp), pebbleSize = 84.dp)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DayPart.entries.forEachIndexed { i, part ->
                ChunkyCard(Modifier.fillMaxWidth().appear(i * 60), onClick = { onPick(part) }, selected = part == selected, contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DayPartIcon(part)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(part.label, style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                            Text("${minuteText(part.start)} to ${minuteText(part.end)}", style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
                        }
                    }
                }
            }
        }
        Text(
            "Your apps are blocked during this time. You can change it later.",
            style = MaterialTheme.typography.bodySmall,
            color = Sp.colors.textDim,
            modifier = Modifier.padding(top = 10.dp),
        )
        // Skipping stays visible on small screens instead of hiding under the list.
        ChunkyButton("Continue", onNext, Modifier.fillMaxWidth().padding(top = 16.dp), enabled = selected != null)
        ChunkyButton("I'll set it later", { onLater(); onNext() }, Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = ButtonKind.GHOST, height = 46.dp)
    }
}

@Composable
private fun PlanStep(goal: String, picked: Set<String>, part: DayPart?, onNext: () -> Unit) {
    val minutes = goal.toIntOrNull() ?: 60
    val items = buildList {
        add("🎯" to "Focus ${if (minutes < 60) "$minutes minutes" else "${minutes / 60} hour${if (minutes >= 120) "s" else ""}"} a day")
        if ("shorts" in picked) add("🎬" to "Close Shorts and Reels for you")
        if ("notifications" in picked) add("🔔" to "Hold notifications while you focus")
        if ("youtube" in picked || "social" in picked || "games" in picked) add("🛡️" to "Block your distracting apps during focus")
        if (part != null) add("⏰" to "${part.label} focus, ${minuteText(part.start)} to ${minuteText(part.end)}")
        add("🔥" to "A daily streak to keep you going")
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PebbleSays("Here's your plan. I think you'll love it!", Mood.PROUD, Modifier.fillMaxWidth().padding(vertical = 12.dp), pebbleSize = 84.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEachIndexed { i, (emoji, text) ->
                Row(Modifier.fillMaxWidth().appear(200 + i * 220), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Sp.colors.brandSoft), contentAlignment = Alignment.Center) {
                        Text(emoji, style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(text, style = MaterialTheme.typography.titleMedium, color = Sp.colors.text, modifier = Modifier.weight(1f))
                    Box(Modifier.popIn(400 + i * 220).size(28.dp).clip(RoundedCornerShape(14.dp)).background(Sp.colors.mint), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_check), null, tint = Sp.colors.onFill, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        ChunkyButton("Sounds great", onNext, Modifier.fillMaxWidth().padding(vertical = 16.dp))
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
        ChunkyButton("Continue", onNext, Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
}

@Composable
private fun AppsStep(installed: List<InstalledApp>, chosen: Set<String>, onChange: (Set<String>) -> Unit, onNext: () -> Unit) {
    // Suggested apps first, then the rest by name.
    val sorted = remember(installed) { installed.sortedBy { if (it.packageName in commonDistractions) 0 else 1 } }
    Column(Modifier.fillMaxSize()) {
        PebbleSays("Which apps steal your time?", Mood.THINK, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), pebbleSize = 84.dp)
        Text(
            "I'll block these while you focus. ${appCount(chosen.size)} chosen.",
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
        ChunkyButton("Continue", onNext, Modifier.fillMaxWidth().padding(20.dp))
    }
}

@Composable
private fun AccessStep(access: Access, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PebbleSays(
            if (access.ready) "All set! I can protect your focus now." else "One last step! Allow these so I can block distractions.",
            if (access.ready) Mood.CELEBRATE else Mood.IDLE,
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            pebbleSize = 84.dp,
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            AccessRows(access, includeOptional = true)
            Text(
                "Stillpoint has no internet access. What it sees stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = Sp.colors.textDim,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        ChunkyButton("Continue", onNext, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = access.ready)
        ChunkyButton("Skip for now", onNext, Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = ButtonKind.GHOST)
    }
}

@Composable
private fun FirstFocus(onStart: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.weight(1f))
        PebbleSays("Let's try a 2 minute focus together. You'll see how it feels!", Mood.HAPPY, Modifier.fillMaxWidth(), side = false, pebbleSize = 170.dp)
        Spacer(Modifier.weight(1f))
        ChunkyButton("Start 2 minute focus", onStart, Modifier.fillMaxWidth(), kind = ButtonKind.MINT, icon = painterResource(R.drawable.ic_play))
        ChunkyButton("Maybe later", onSkip, Modifier.fillMaxWidth().padding(top = 4.dp), kind = ButtonKind.GHOST)
    }
}
