package dev.agneswd.stillpoint.focus

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.StillpointApp
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.ActiveFocus
import dev.agneswd.stillpoint.data.FocusPhase
import dev.agneswd.stillpoint.data.FocusSession
import dev.agneswd.stillpoint.data.TimerMode
import dev.agneswd.stillpoint.data.currentSettings
import dev.agneswd.stillpoint.guard.formatDuration
import dev.agneswd.stillpoint.ui.MainActivity
import dev.agneswd.stillpoint.widget.Widgets

/**
 * Starts, moves and ends focus sessions. The [ActiveFocus] row is the only state.
 * The service, the screens and the guard all read that row.
 */
object Focus {
    /** A stopwatch runs at most this long, so a forgotten session ends by itself. */
    private const val STOPWATCH_LIMIT_MILLIS = 12 * 60 * 60_000L

    /**
     * Starts a session with the saved focus settings. It does nothing if a session runs.
     * [minutes] overrides the focus length, for example for the 2 minute first session.
     */
    suspend fun start(context: Context, tag: String, minutes: Int? = null) {
        val dao = context.app.dao
        if (dao.activeFocus() != null) return
        val s = dao.currentSettings()
        val now = System.currentTimeMillis()
        val mode = if (minutes != null) TimerMode.TIMER else s.timerMode
        val focusMinutes = minutes ?: s.focusMinutes
        dao.saveActiveFocus(
            ActiveFocus(
                startedAt = now,
                phase = FocusPhase.FOCUS,
                phaseStartedAt = now,
                phaseEndsAt = if (mode == TimerMode.STOPWATCH) now + STOPWATCH_LIMIT_MILLIS else now + focusMinutes * 60_000L,
                round = 1,
                rounds = if (mode == TimerMode.POMODORO) s.focusRounds.coerceAtLeast(1) else 1,
                focusMinutes = focusMinutes,
                breakMinutes = s.breakMinutes,
                packages = s.focusPackages,
                mode = s.focusMode,
                strict = s.focusStrict,
                lockHome = s.focusLockHome,
                sound = s.focusSound,
                tag = tag.trim(),
                timerMode = mode,
                longBreakMinutes = s.longBreakMinutes,
                theme = s.focusTheme,
            ),
        )
        ContextCompat.startForegroundService(context, FocusService.intent(context))
        Widgets.refresh(context)
    }

    /** Moves to the next phase. The last focus round ends the session. */
    suspend fun advance(context: Context) {
        val dao = context.app.dao
        val focus = dao.activeFocus() ?: return
        if (!focus.running) return
        val now = System.currentTimeMillis()
        when (focus.phase) {
            FocusPhase.FOCUS -> {
                val done = focus.copy(focusedMillisBefore = focus.focusedMillisBefore + (focus.phaseEndsAt - focus.phaseStartedAt))
                when {
                    focus.round >= focus.rounds -> finish(context, done, completed = true)
                    focus.breakMinutes == 0 -> {
                        dao.saveActiveFocus(done.nextRound(now))
                        announce(context, "Round ${focus.round + 1} of ${focus.rounds}", "Keep going.")
                    }
                    else -> {
                        // Every fourth break is a long one.
                        val minutes = if (focus.round % 4 == 0) focus.longBreakMinutes else focus.breakMinutes
                        dao.saveActiveFocus(done.copy(phase = FocusPhase.BREAK, phaseStartedAt = now, phaseEndsAt = now + minutes * 60_000L))
                        announce(context, "Break for $minutes minutes", "Stand up and look at something far away.")
                    }
                }
            }

            FocusPhase.BREAK -> {
                dao.saveActiveFocus(focus.nextRound(now))
                announce(context, "Round ${focus.round + 1} of ${focus.rounds}", "Back to focus.")
            }
        }
        Widgets.refresh(context)
    }

    /** Pauses a session that is not strict. Blocks stop until it resumes. */
    suspend fun pause(context: Context) {
        val dao = context.app.dao
        val focus = dao.activeFocus() ?: return
        if (focus.strict || !focus.running) return
        dao.saveActiveFocus(focus.copy(pausedAt = System.currentTimeMillis()))
        Widgets.refresh(context)
    }

    suspend fun resume(context: Context) {
        val dao = context.app.dao
        val focus = dao.activeFocus() ?: return
        if (focus.running) return
        val gap = System.currentTimeMillis() - focus.pausedAt
        dao.saveActiveFocus(
            focus.copy(pausedAt = 0, phaseStartedAt = focus.phaseStartedAt + gap, phaseEndsAt = focus.phaseEndsAt + gap),
        )
        Widgets.refresh(context)
    }

    /** Ends a stopwatch session as a finished session. */
    suspend fun stopStopwatch(context: Context) {
        val focus = context.app.dao.activeFocus() ?: return
        if (focus.timerMode != TimerMode.STOPWATCH) return
        val end = if (focus.running) System.currentTimeMillis() else focus.pausedAt
        finish(context, focus.copy(focusedMillisBefore = focus.focusedMillisBefore + (end - focus.phaseStartedAt)), completed = true)
    }

    /** Ends the session before the last round. Strict sessions cannot end this way. */
    suspend fun giveUp(context: Context) {
        val focus = context.app.dao.activeFocus() ?: return
        if (focus.strict) return
        val end = if (focus.running) System.currentTimeMillis() else focus.pausedAt
        val partial = if (focus.phase == FocusPhase.FOCUS) minOf(end, focus.phaseEndsAt) - focus.phaseStartedAt else 0L
        finish(context, focus.copy(focusedMillisBefore = focus.focusedMillisBefore + partial), completed = false)
    }

    private fun ActiveFocus.nextRound(now: Long) =
        copy(phase = FocusPhase.FOCUS, round = round + 1, phaseStartedAt = now, phaseEndsAt = now + focusMinutes * 60_000L)

    private suspend fun finish(context: Context, focus: ActiveFocus, completed: Boolean) {
        val dao = context.app.dao
        val now = System.currentTimeMillis()
        if (focus.focusedMillisBefore >= 60_000) {
            val id = dao.addSession(
                FocusSession(
                    startedAt = focus.startedAt,
                    endedAt = now,
                    focusedMillis = focus.focusedMillisBefore,
                    completed = completed,
                    tag = focus.tag,
                ),
            )
            Celebrations.offer(id)
        }
        dao.clearActiveFocus()
        val held = dao.heldCount()
        val heldText = if (held > 0) " $held notifications are waiting." else ""
        announce(
            context,
            if (completed) "Session done" else "Session ended",
            "You focused for ${formatDuration(focus.focusedMillisBefore)}.$heldText",
        )
        Widgets.refresh(context)
    }

    private fun announce(context: Context, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, StillpointApp.CHANNEL_EVENTS)
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
