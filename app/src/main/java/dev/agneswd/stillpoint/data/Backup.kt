package dev.agneswd.stillpoint.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.guard.hostOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/** A versioned local backup. Active sessions and temporary passes do not move between phones. */
@Serializable
data class Backup(
    val version: Int = 2,
    val settings: Settings,
    val limits: List<AppLimit>,
    val schedules: List<Schedule>,
    val sites: List<BlockedSite>,
    val sessions: List<FocusSession>,
    val usageDays: List<UsageDay> = emptyList(),
)

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

/** Reads a consistent snapshot, even if a session ends during export. */
suspend fun exportBackup(context: Context, dao: StillpointDao, target: Uri, password: CharArray) = withContext(Dispatchers.IO) {
    val backup = context.app.database.withTransaction {
        Backup(settings = dao.currentSettings(), limits = dao.allLimits(), schedules = dao.allSchedules(),
            sites = dao.allSites(), sessions = dao.allSessions(), usageDays = dao.allUsageDays())
    }
    val plaintext = json.encodeToString(backup).encodeToByteArray()
    val encrypted = try { BackupCrypto.encrypt(plaintext, password) } finally { plaintext.fill(0) }
    context.contentResolver.openOutputStream(target, "wt").use { out ->
        requireNotNull(out) { "Cannot write the backup file" }.write(encrypted)
    }
}

/** Validates the entire file before replacing any stored data. */
suspend fun importBackup(context: Context, dao: StillpointDao, source: Uri, password: CharArray) = withContext(Dispatchers.IO) {
    val bytes = context.contentResolver.openInputStream(source).use { input ->
        requireNotNull(input) { "Cannot read the backup file" }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(out.size() + count <= BackupCrypto.MAX_ENCRYPTED_BYTES) { "Backup exceeds 16 MB" }
            out.write(buffer, 0, count)
        }
        out.toByteArray()
    }
    val plaintext = BackupCrypto.decrypt(bytes, password)
    val backup = try {
        json.decodeFromString<Backup>(plaintext.decodeToString(throwOnInvalidSequence = true)).validated()
    } finally { plaintext.fill(0) }
    context.app.database.withTransaction {
        require(dao.activeFocus() == null) { "End the focus session before restoring a backup" }
        val current = dao.currentSettings()
        require(!current.protection || !dev.agneswd.stillpoint.guard.Rules(schedules = dao.allSchedules()).locked(java.time.LocalDateTime.now())) {
            "Restore is locked while a protected schedule runs"
        }
        dev.agneswd.stillpoint.game.PebblePets.invalidatePendingTaps()
        dao.replaceAll(backup.copy(settings = backup.settings.copy(id = 0, pauseBlocksUntil = 0)))
    }
    context.getSharedPreferences("delivery", Context.MODE_PRIVATE).edit().clear().apply()
    context.getSharedPreferences("plans", Context.MODE_PRIVATE).edit().clear().apply()
    dev.agneswd.stillpoint.schedule.Plans.refresh(context)
    dev.agneswd.stillpoint.widget.Widgets.refresh(context)
}

/** Validates data inside the authenticated envelope, including older record schemas. */
fun Backup.validated(): Backup {
    require(version in 1..2) { "Unsupported backup version" }
    require(sessions.size <= 100_000 && usageDays.size <= 50_000 && limits.size <= 5_000 &&
        schedules.size <= 5_000 && sites.size <= 5_000) { "Backup contains too many records" }
    val s = settings
    require(s.focusGoalMinutes in 5..1440 && s.focusMinutes in 1..240 && s.breakMinutes in 0..60 &&
        s.longBreakMinutes in 0..120 && s.focusRounds in 1..12 && s.goalDays in 1..127 &&
        s.emergencyPassesPerDay in 0..10 && s.streakFreezes in 0..2 && s.freezeWeeksRewarded >= 0) { "Invalid focus or game settings" }
    require(s.themeMode in setOf("SYSTEM", "LIGHT", "DARK")) { "Invalid theme mode" }
    require(s.clockFormat in setOf("SYSTEM", "H12", "H24")) { "Invalid clock format" }
    require(s.petTapCount in 0..1000) { "Invalid Pebble tap count" }
    require(s.pebbleItems.size <= 8 && s.pebbleItems.all { it.matches(Regex("[a-z][a-z0-9_]{0,63}")) }) { "Invalid Pebble items" }
    require(s.notificationDeliveryTimes.all { it.toIntOrNull() in 0..1439 }) { "Invalid notification delivery time" }
    require(s.freezeRewardedThrough.isEmpty() || runCatching { LocalDate.parse(s.freezeRewardedThrough) }.isSuccess) { "Invalid freeze reward date" }
    require(s.frozenDays.all { runCatching { LocalDate.parse(it) }.isSuccess }) { "Invalid streak date" }
    require(limits.map { it.packageName }.distinct().size == limits.size && limits.all {
        it.packageName.isNotBlank() && it.minutesPerDay in 1..1440 && it.reminderMinutes in 0..120
    }) { "Invalid app limits" }
    require(schedules.map { it.id }.distinct().size == schedules.size && schedules.all {
        it.id > 0 && it.name.isNotBlank() && it.startMinute in 0..1439 && it.endMinute in 0..1439 &&
            it.days in 1..127 && it.focusMinutes in 1..240 && it.icon in SCHEDULE_ICONS
    }) { "Invalid schedules" }
    require(sites.map { it.domain }.distinct().size == sites.size && sites.all { hostOf(it.domain) == it.domain }) { "Invalid site list" }
    // Twelve four-hour Pomodoro rounds are the longest producible focus record.
    require(sessions.map { it.id }.distinct().size == sessions.size && sessions.all {
        it.id > 0 && it.startedAt > 0 && it.endedAt >= it.startedAt && it.focusedMillis in 0..172_800_000L &&
            it.focusedMillis <= it.endedAt - it.startedAt && it.goalMinutes in 5..1440 && it.questVersion in 0..1 &&
            (it.rewardDay.isEmpty() || runCatching { LocalDate.parse(it.rewardDay) }.isSuccess) && it.rewardStartHour in -1..23
    }) { "Invalid focus history" }
    // A civil day can exceed 24 hours after a clock or time-zone change.
    require(usageDays.map { it.day }.distinct().size == usageDays.size && usageDays.all {
        runCatching { LocalDate.parse(it.day) }.isSuccess && it.unlocks >= 0 && it.heldCount >= 0 &&
            it.perApp.all { (pkg, value) -> pkg.isNotBlank() && value in 0..172_800_000L } &&
            it.limitMinutes.all { (pkg, value) -> pkg.isNotBlank() && value in 1..1440 }
    }) { "Invalid usage history" }
    // Old files have no setup state or goal snapshot.
    return if (version == 1) copy(settings = s.copy(onboarded = true), sessions = sessions.map { it.copy(goalMinutes = s.focusGoalMinutes) }) else this
}
