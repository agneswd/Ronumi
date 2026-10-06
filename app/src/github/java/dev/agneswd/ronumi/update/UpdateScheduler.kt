package dev.agneswd.ronumi.update

import android.app.NotificationManager
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.Context

object UpdateScheduler {
    /** Job id used by builds before 0.1.3. Startup cancels this id. */
    internal const val JOB = 64021
    private const val NOTIFICATION = 64022

    /** Cancels a daily check left by an older build. This release does not schedule one. */
    fun cancel(context: Context): Boolean = try {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return false
        scheduler.cancel(JOB)
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION)
        true
    } catch (_: RuntimeException) {
        false
    }
}

/** Receives a job that an older build already scheduled. It does not use the network. */
class UpdateJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean = false

    override fun onStopJob(params: JobParameters): Boolean = false
}
