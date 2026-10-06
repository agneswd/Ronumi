package dev.agneswd.stillpoint

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import dev.agneswd.stillpoint.update.UpdateJob
import dev.agneswd.stillpoint.update.UpdateScheduler
import java.io.File

/**
 * Schedules the retired update job before [android.app.Application.onCreate].
 * The github build cancels that job at startup. Only the bridge device check includes this provider.
 */
class UpdateJobPlant : ContentProvider() {
    override fun onCreate(): Boolean {
        val context = context ?: return true
        val text = runCatching {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: error("JobScheduler is unavailable")
            val info = JobInfo.Builder(UpdateScheduler.JOB, ComponentName(context, UpdateJob::class.java))
                .setMinimumLatency(3_600_000L)
                .build()
            val result = scheduler.schedule(info)
            "scheduled=$result id=${UpdateScheduler.JOB}"
        }.fold({ it }, { "FAIL ${it::class.simpleName}: ${it.message}" })
        File(context.filesDir, "update-job-plant.txt").writeText(text + "\n")
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        throw UnsupportedOperationException()

    override fun getType(uri: Uri): String = throw UnsupportedOperationException()

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
}
