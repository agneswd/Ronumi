package dev.agneswd.stillpoint.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A local backup file. It replaces the cloud backup of the original app. */
@Serializable
data class Backup(
    val version: Int = 1,
    val settings: Settings,
    val limits: List<AppLimit>,
    val schedules: List<Schedule>,
    val sites: List<BlockedSite>,
    val sessions: List<FocusSession>,
)

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

suspend fun exportBackup(context: Context, dao: StillpointDao, target: Uri) {
    val backup = Backup(
        settings = dao.currentSettings(),
        limits = dao.allLimits(),
        schedules = dao.allSchedules(),
        sites = dao.allSites(),
        sessions = dao.allSessions(),
    )
    context.contentResolver.openOutputStream(target, "wt").use { out ->
        requireNotNull(out) { "Cannot write $target" }.write(json.encodeToString(backup).toByteArray())
    }
}

suspend fun importBackup(context: Context, dao: StillpointDao, source: Uri) {
    val text = context.contentResolver.openInputStream(source).use { input ->
        requireNotNull(input) { "Cannot read $source" }.readBytes().decodeToString()
    }
    dao.replaceAll(json.decodeFromString<Backup>(text))
}
