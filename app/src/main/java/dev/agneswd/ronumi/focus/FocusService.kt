package dev.agneswd.ronumi.focus

import dev.agneswd.ronumi.ui.displayName
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.agneswd.ronumi.data.currentSettings
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.RonumiApp
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.ActiveFocus
import dev.agneswd.ronumi.data.FocusPhase
import dev.agneswd.ronumi.data.FocusSound
import dev.agneswd.ronumi.data.TimerMode
import kotlinx.coroutines.awaitCancellation
import dev.agneswd.ronumi.ui.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the focus timer alive with the screen off. It shows the countdown notification,
 * plays the focus noise, and moves to the next phase when the current one ends.
 * It stops when the [ActiveFocus] row goes away.
 */
class FocusService : LifecycleService() {
    // Text in the app language. See RonumiApp.getResources.
    override fun getResources(): android.content.res.Resources = applicationContext.resources

    private var started = false
    private val noise by lazy { NoisePlayer(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_GIVE_UP) lifecycleScope.launch { Focus.giveUp(this@FocusService) }
        if (intent?.action == ACTION_FINISH) lifecycleScope.launch { Focus.stopStopwatch(this@FocusService) }
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

    private fun placeholder(): Notification = builder().setContentTitle(getString(R.string.focus_notification_title)).build()

    private fun notification(focus: ActiveFocus, live: Boolean): Notification {
        val title = when {
            !focus.running -> getString(R.string.focus_notification_paused)
            focus.phase == FocusPhase.BREAK -> getString(R.string.focus_notification_break)
            focus.rounds > 1 -> getString(R.string.focus_notification_round, focus.round, focus.rounds)
            else -> getString(R.string.focus_notification_title)
        }
        val stopwatch = focus.timerMode == TimerMode.STOPWATCH
        val builder = builder()
            .setContentTitle(title)
            .setContentText(focus.tag.ifBlank { null }?.displayName(this))
            .setWhen(System.currentTimeMillis() + if (stopwatch) -focus.elapsedPhaseMillis() else focus.remainingMillis())
            .setShowWhen(focus.running)
            .setUsesChronometer(focus.running)
            .setChronometerCountDown(!stopwatch)
            // Android 16 shows a promoted ongoing notification as a chip with the timer in the status bar.
            .setRequestPromotedOngoing(live)
        // A stopwatch has no early end. Finishing it counts as a completed session, as on the focus screen.
        if (stopwatch) {
            builder.addAction(0, getString(R.string.focus_notification_finish), PendingIntent.getService(this, 2, intent(this).setAction(ACTION_FINISH), PendingIntent.FLAG_IMMUTABLE))
        } else if (!focus.strict) {
            val giveUp = PendingIntent.getService(this, 1, intent(this).setAction(ACTION_GIVE_UP), PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, getString(R.string.focus_notification_end), giveUp)
        }
        return builder.build()
    }

    private fun builder() = NotificationCompat.Builder(this, RonumiApp.CHANNEL_FOCUS)
        .setSmallIcon(R.drawable.ic_stat)
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setContentIntent(MainActivity.pendingFocus(this))

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val ACTION_GIVE_UP = "give_up"
        private const val ACTION_FINISH = "finish"

        fun intent(context: Context) = Intent(context, FocusService::class.java)

        fun canNotify(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
