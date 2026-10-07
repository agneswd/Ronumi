package dev.agneswd.ronumi.focus

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.provider.Settings.Global
import java.time.Instant
import java.time.ZoneId
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import dev.agneswd.ronumi.data.Schedule
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.RonumiApp
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.ActiveFocus
import dev.agneswd.ronumi.data.FocusPhase
import dev.agneswd.ronumi.data.FocusSession
import dev.agneswd.ronumi.data.TimerMode
import dev.agneswd.ronumi.data.currentSettings
import dev.agneswd.ronumi.guard.formatDuration
import dev.agneswd.ronumi.ui.MainActivity
import dev.agneswd.ronumi.widget.Widgets

/**
 * Starts, moves and ends focus sessions. The [ActiveFocus] row is the only state.
 * The service, the screens and the guard all read that row.
 */
object Focus {
    private val lock = Mutex()

    /** Commits each transition once. Service collection cancellation cannot split the writes. */
    private suspend fun mutate(context: Context, action: suspend () -> Unit) = withContext(NonCancellable) {
        lock.withLock { context.app.database.withTransaction { action() } }
        // Widgets read the database on another connection, so they refresh after the commit.
        Widgets.refresh(context)
    }

    /** Called from the visible app to reconnect a persisted session to its foreground service. */
    suspend fun recover(context: Context) {
        checkpoint(context)
        if (context.app.dao.activeFocus() != null) {
            ContextCompat.startForegroundService(context, FocusService.intent(context))
        }
    }

    private fun boot(context: Context): Int = Global.getInt(context.contentResolver, Global.BOOT_COUNT, -1)

    private fun ActiveFocus.current(context: Context): ActiveFocus =
        rebaseClock(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(context))

    /** Saves earned phase time at transitions and every 30 seconds while the service runs. */
    suspend fun checkpoint(context: Context) = mutate(context) {
        val dao = context.app.dao
        dao.activeFocus()?.let { focus -> dao.saveActiveFocus(focus.current(context)) }
    }

    /** A stopwatch runs at most this long, so a forgotten session ends by itself. */
    private const val STOPWATCH_LIMIT_MILLIS = 12 * 60 * 60_000L

    /**
     * Starts a session with the saved focus settings. It does nothing if a session runs.
     * [minutes] overrides the focus length, for example for the 2 minute first session.
     */
    suspend fun start(context: Context, tag: String, minutes: Int? = null, plan: Schedule? = null) = mutate(context) {
        val dao = context.app.dao
        if (dao.activeFocus() != null) return@mutate
        val s = dao.currentSettings()
        val now = System.currentTimeMillis()
        val mode = if (minutes != null || plan != null) TimerMode.TIMER else s.timerMode
        val focusMinutes = (minutes ?: plan?.focusMinutes ?: s.focusMinutes).coerceIn(1, 240)
        // A strict session and a Plus scene need Plus when the session starts. Saved choices stay as they are.
        val plus = context.app.plus
        val strict = s.focusStrict && plus.has(dev.agneswd.ronumi.plus.PlusFeature.STRICT_MODE)
        val theme = s.focusTheme.takeIf { name ->
            dev.agneswd.ronumi.ui.themeOf(name).let { !it.plus || plus.has(dev.agneswd.ronumi.plus.PlusFeature.PLUS_SCENES) }
        } ?: dev.agneswd.ronumi.ui.design.FocusTheme.LAKE.name
        dao.saveActiveFocus(
            ActiveFocus(
                startedAt = now,
                phaseAnchorElapsed = SystemClock.elapsedRealtime(),
                bootCount = boot(context),
                rewardDay = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                rewardStartHour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour,
                phase = FocusPhase.FOCUS,
                phaseStartedAt = now,
                phaseEndsAt = if (mode == TimerMode.STOPWATCH) now + STOPWATCH_LIMIT_MILLIS else now + focusMinutes * 60_000L,
                round = 1,
                rounds = if (mode == TimerMode.POMODORO) s.focusRounds.coerceAtLeast(1) else 1,
                focusMinutes = focusMinutes,
                breakMinutes = s.breakMinutes,
                packages = plan?.packages ?: s.focusPackages,
                mode = plan?.mode ?: s.focusMode,
                strict = strict,
                lockHome = s.focusLockHome,
                sound = s.focusSound,
                tag = tag.trim(),
                timerMode = mode,
                longBreakMinutes = s.longBreakMinutes,
                theme = theme,
                goalMinutes = s.focusGoalMinutes,
            ),
        )
        ContextCompat.startForegroundService(context, FocusService.intent(context))
    }

    /** Moves to the next phase. The last focus round ends the session. */
    suspend fun advance(context: Context) = mutate(context) {
        val dao = context.app.dao
        val focus = dao.activeFocus()?.current(context) ?: return@mutate
        if (!focus.running || focus.remainingMillis() > 0) {
            dao.saveActiveFocus(focus)
            return@mutate
        }
        val now = focus.phaseEndsAt
        when (focus.phase) {
            FocusPhase.FOCUS -> {
                val done = focus.copy(focusedMillisBefore = focus.focusedMillisBefore + (focus.phaseEndsAt - focus.phaseStartedAt))
                val breakMinutes = focus.breakAfterRoundMinutes()
                when {
                    focus.round >= focus.rounds -> finish(context, done, completed = true)
                    breakMinutes == 0 -> {
                        dao.saveActiveFocus(done.nextRound(now))
                        announce(context, context.getString(R.string.focus_round_notification, focus.round + 1, focus.rounds), context.getString(R.string.focus_round_continue))
                    }
                    else -> {
                        dao.saveActiveFocus(done.copy(phase = FocusPhase.BREAK, phaseStartedAt = now, phaseEndsAt = now + breakMinutes * 60_000L, phaseElapsedMillis = 0, phaseAnchorElapsed = SystemClock.elapsedRealtime()))
                        announce(context, context.resources.getQuantityString(R.plurals.focus_break_notification, breakMinutes, breakMinutes), context.getString(R.string.focus_break_advice))
                    }
                }
            }

            FocusPhase.BREAK -> {
                dao.saveActiveFocus(focus.nextRound(now))
                announce(context, context.getString(R.string.focus_round_notification, focus.round + 1, focus.rounds), context.getString(R.string.focus_round_resume))
            }
        }
    }

    /** Pauses a session that is not strict. Blocks stop until it resumes. */
    suspend fun pause(context: Context) = mutate(context) {
        val dao = context.app.dao
        val focus = dao.activeFocus()?.current(context) ?: return@mutate
        if (focus.strict || !focus.running) return@mutate
        dao.saveActiveFocus(focus.copy(pausedAt = System.currentTimeMillis().coerceAtLeast(1)))
    }

    suspend fun resume(context: Context) = mutate(context) {
        val dao = context.app.dao
        val focus = dao.activeFocus()?.current(context) ?: return@mutate
        if (focus.running) return@mutate
        dao.saveActiveFocus(focus.copy(pausedAt = 0))
    }

    /** Ends a stopwatch session as a finished session. */
    suspend fun stopStopwatch(context: Context) = mutate(context) {
        val focus = context.app.dao.activeFocus()?.current(context) ?: return@mutate
        if (focus.timerMode != TimerMode.STOPWATCH) return@mutate
        finish(context, focus.copy(focusedMillisBefore = focus.focusedMillisBefore + focus.elapsedPhaseMillis()), completed = true)
    }

    /** Ends the session before the last round. Strict sessions cannot end this way. */
    suspend fun giveUp(context: Context) = mutate(context) {
        val focus = context.app.dao.activeFocus()?.current(context) ?: return@mutate
        if (focus.strict) return@mutate
        val partial = if (focus.phase == FocusPhase.FOCUS) focus.elapsedPhaseMillis() else 0L
        finish(context, focus.copy(focusedMillisBefore = focus.focusedMillisBefore + partial), completed = false)
    }

    private fun ActiveFocus.nextRound(now: Long) =
        copy(phase = FocusPhase.FOCUS, round = round + 1, phaseStartedAt = now, phaseEndsAt = now + focusMinutes * 60_000L, phaseElapsedMillis = 0, phaseAnchorElapsed = SystemClock.elapsedRealtime())

    private suspend fun finish(context: Context, focus: ActiveFocus, completed: Boolean) {
        val dao = context.app.dao
        val now = maxOf(System.currentTimeMillis(), focus.startedAt + focus.focusedMillisBefore)
        if (focus.focusedMillisBefore >= 60_000) {
            val id = dao.addSession(
                FocusSession(
                    startedAt = focus.startedAt,
                    endedAt = now,
                    focusedMillis = focus.focusedMillisBefore,
                    completed = completed,
                    tag = focus.tag,
                    goalMinutes = focus.goalMinutes,
                    questVersion = 1,
                    rewardDay = focus.rewardDay,
                    rewardStartHour = focus.rewardStartHour,
                ),
            )
            Celebrations.offer(id)
        }
        dao.clearActiveFocus()
        dev.agneswd.ronumi.game.applyStreakFreezes(dao)
        val held = dao.heldCount()
        announce(
            context,
            if (completed) context.getString(R.string.focus_completed_title) else context.getString(R.string.focus_ended_title),
            if (held > 0) context.resources.getQuantityString(R.plurals.focus_completed_inbox_body, held, formatDuration(context, focus.focusedMillisBefore), held)
            else context.getString(R.string.focus_completed_body, formatDuration(context, focus.focusedMillisBefore)),
        )
    }

    private suspend fun announce(context: Context, title: String, text: String) {
        if (!context.app.dao.currentSettings().notifyFocusEvents) return
        val notification = NotificationCompat.Builder(context, RonumiApp.CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(MainActivity.pendingFocus(context))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(EVENT_NOTIFICATION_ID, notification)
    }

    private const val EVENT_NOTIFICATION_ID = 2
}

/** Every fourth Pomodoro break uses the long-break setting, even when short breaks are disabled. */
internal fun ActiveFocus.breakAfterRoundMinutes(): Int = if (round % 4 == 0) longBreakMinutes else breakMinutes

/**
 * The session that just ended and still waits for its celebration screen.
 * It lives in memory: after a restart the user simply sees the home screen.
 */
object Celebrations {
    val pending = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    fun offer(sessionId: Long) {
        pending.value = sessionId
    }

    fun consume() {
        pending.value = null
    }
}
