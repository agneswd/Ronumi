package dev.agneswd.stillpoint.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object UpdateScheduler {
    private const val JOB = 64021
    internal const val NOTIFICATION = 64022
    const val SHOW_UPDATES = "show_updates"

    /** Android chooses the exact time. Persisted jobs resume after a reboot. */
    fun schedule(context: Context, enabled: Boolean): Boolean = try {
        configure(context, enabled)
    } catch (_: RuntimeException) {
        false
    }

    private fun configure(context: Context, enabled: Boolean): Boolean {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return false
        if (!enabled) {
            scheduler.cancel(JOB)
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION)
            return true
        }
        if (scheduler.getPendingJob(JOB) != null) return true
        val job = JobInfo.Builder(JOB, ComponentName(context, UpdateJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(TimeUnit.DAYS.toMillis(1), TimeUnit.HOURS.toMillis(2))
            .build()
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
    }
}

/** Checks metadata only. A download and an install each require a separate user action. */
class UpdateJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work = scope.launch {
            try {
                if (!app.dao.settings().first().autoUpdateChecks) return@launch
                val result = UpdateClient.check(this@UpdateJob)
                if (result is UpdateCheck.Available && app.dao.settings().first().autoUpdateChecks) {
                    notifyUpdate(result.release)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("UpdateJob", "The daily update check could not finish", error)
            } finally {
                // Android owns retries. A failed check waits for the next daily job.
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { jobFinished(params, false) }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notifyUpdate(release: UpdateRelease) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel("app_updates", "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("updates", Context.MODE_PRIVATE)
        if (prefs.getString("notifiedTag", null) == release.tag) return
        val launch = Intent(this, MainActivity::class.java)
            .putExtra(UpdateScheduler.SHOW_UPDATES, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(this, UpdateScheduler.NOTIFICATION, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "app_updates")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Stillpoint ${release.tag} is available")
            .setContentText("Tap to review the update. Nothing downloads until you choose.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(UpdateScheduler.NOTIFICATION, notification)
        prefs.edit().putString("notifiedTag", release.tag).apply()
    }
}
