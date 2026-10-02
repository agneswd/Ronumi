package dev.agneswd.stillpoint.focus

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.agneswd.stillpoint.data.currentSettings
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.StillpointApp
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.ActiveFocus
import dev.agneswd.stillpoint.data.FocusPhase
import dev.agneswd.stillpoint.data.FocusSound
import dev.agneswd.stillpoint.data.TimerMode
import kotlinx.coroutines.awaitCancellation
import dev.agneswd.stillpoint.ui.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the focus timer alive with the screen off. It shows the countdown notification,
 * plays the focus noise, and moves to the next phase when the current one ends.
 * It stops when the [ActiveFocus] row goes away.
 */
class FocusService : LifecycleService() {
    private var started = false
    private val noise by lazy { NoisePlayer(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_GIVE_UP) lifecycleScope.launch { Focus.giveUp(this@FocusService) }
        if (!started) {
            started = true
            goForeground(placeholder())
            lifecycleScope.launch { run() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        noise.stop()
        super.onDestroy()
    }

    private suspend fun run() {
        Focus.checkpoint(this)
        app.dao.activeFocusFlow().collectLatest { focus ->
            if (focus == null) {
                noise.stop()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@collectLatest
            }
            // A changed Live Update setting applies at the next update, within 30 seconds.
            goForeground(notification(focus, live = app.dao.currentSettings().liveFocusTimer))
            val playing = focus.phase == FocusPhase.FOCUS && focus.running && focus.sound != FocusSound.OFF
            if (playing) noise.play(focus.sound) else noise.stop()
            // A paused session waits here until the row changes again.
            if (!focus.running) awaitCancellation()
            val left = focus.remainingMillis()
            delay(minOf(left, 30_000L))
            if (focus.remainingMillis() == 0L) Focus.advance(this) else Focus.checkpoint(this)
        }
    }

    private fun goForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun placeholder(): Notification = builder().setContentTitle("Focus").build()

    private fun notification(focus: ActiveFocus, live: Boolean): Notification {
        val title = when {
            !focus.running -> "Paused"
            focus.phase == FocusPhase.BREAK -> "Break"
            focus.rounds > 1 -> "Focus, round ${focus.round} of ${focus.rounds}"
            else -> "Focus"
        }
        val stopwatch = focus.timerMode == TimerMode.STOPWATCH
        val builder = builder()
            .setContentTitle(title)
            .setContentText(focus.tag.ifBlank { null })
            .setWhen(System.currentTimeMillis() + if (stopwatch) -focus.elapsedPhaseMillis() else focus.remainingMillis())
            .setShowWhen(focus.running)
            .setUsesChronometer(focus.running)
            .setChronometerCountDown(!stopwatch)
            // Android 16 shows a promoted ongoing notification as a chip with the timer in the status bar.
            .setRequestPromotedOngoing(live)
        if (!focus.strict) {
            val giveUp = PendingIntent.getService(this, 1, intent(this).setAction(ACTION_GIVE_UP), PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, "End session", giveUp)
        }
        return builder.build()
    }

    private fun builder() = NotificationCompat.Builder(this, StillpointApp.CHANNEL_FOCUS)
        .setSmallIcon(R.drawable.ic_stat)
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setContentIntent(MainActivity.pendingFocus(this))

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val ACTION_GIVE_UP = "give_up"

        fun intent(context: Context) = Intent(context, FocusService::class.java)

        fun canNotify(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
