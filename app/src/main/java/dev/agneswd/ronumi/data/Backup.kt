package dev.agneswd.ronumi.data

import dev.agneswd.ronumi.R
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.guard.hostOf
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
suspend fun exportBackup(context: Context, dao: RonumiDao, target: Uri, password: CharArray) = withContext(Dispatchers.IO) {
    val backup = context.app.database.withTransaction {
        Backup(settings = dao.currentSettings(), limits = dao.allLimits(), schedules = dao.allSchedules(),
            sites = dao.allSites(), sessions = dao.allSessions(), usageDays = dao.allUsageDays())
    }
    val plaintext = json.encodeToString(backup).encodeToByteArray()
    val encrypted = try { BackupCrypto.encrypt(plaintext, password) } finally { plaintext.fill(0) }
    context.contentResolver.openOutputStream(target, "wt").use { out ->
        requireNotNull(out) { context.app.getString(R.string.backup_error_write) }.write(encrypted)
    }
}

/** Validates the entire file before replacing any stored data. */
suspend fun importBackup(context: Context, dao: RonumiDao, source: Uri, password: CharArray) = withContext(Dispatchers.IO) {
    val bytes = context.contentResolver.openInputStream(source).use { input ->
        requireNotNull(input) { context.app.getString(R.string.backup_error_read) }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(out.size() + count <= BackupCrypto.MAX_ENCRYPTED_BYTES) { context.app.getString(R.string.backup_error_size) }
            out.write(buffer, 0, count)
        }
        out.toByteArray()
    }
    val plaintext = BackupCrypto.decrypt(bytes, password)
    restorePlainDocument(context, dao, plaintext)
}

/**
 * Restores one plaintext backup document. [bytes] is wiped here.
 * This path does not decrypt a password envelope.
 */
suspend fun restorePlainDocument(context: Context, dao: RonumiDao, bytes: ByteArray) = withContext(Dispatchers.IO) {
    val backup = try {
        json.decodeFromString<Backup>(bytes.decodeToString(throwOnInvalidSequence = true))
    } finally { bytes.fill(0) }
    restoreBackup(context, dao, backup)
}

/**
 * Validates [backup] before the transaction. A failed check does not change stored data.
 * Consent versions live in their own preferences. Restore does not write them.
 */
internal suspend fun restoreBackup(context: Context, dao: RonumiDao, backup: Backup) {
    val ready = backup.validated()
    context.app.database.withTransaction {
        require(dao.activeFocus() == null) { context.app.getString(R.string.backup_error_focus_running) }
        val current = dao.currentSettings()
        require(!current.protection || !dev.agneswd.ronumi.guard.Rules(schedules = dao.allSchedules()).locked(java.time.LocalDateTime.now())) {
            context.app.getString(R.string.backup_error_schedule_running)
        }
        dev.agneswd.ronumi.game.RonumiPets.invalidatePendingTaps()
        dao.replaceAll(ready.copy(settings = ready.settings.copy(id = 0, pauseBlocksUntil = 0)))
    }
    context.getSharedPreferences("delivery", Context.MODE_PRIVATE).edit().clear().apply()
    context.getSharedPreferences("plans", Context.MODE_PRIVATE).edit().clear().apply()
    dev.agneswd.ronumi.schedule.Plans.refresh(context)
    dev.agneswd.ronumi.widget.Widgets.refresh(context)
}

/** Validates data inside the authenticated envelope, including older record schemas. */
fun Backup.validated(): Backup {
    requireBackup(version in 1..2) { R.string.backup_error_version }
    requireBackup(sessions.size <= 100_000 && usageDays.size <= 50_000 && limits.size <= 5_000 &&
        schedules.size <= 5_000 && sites.size <= 5_000) { R.string.backup_error_record_count }
    val s = settings
    requireBackup(s.focusGoalMinutes in 5..1440 && s.focusMinutes in 1..240 && s.breakMinutes in 0..60 &&
        s.longBreakMinutes in 0..120 && s.focusRounds in 1..12 && s.goalDays in 1..127 &&
        s.emergencyPassesPerDay in 0..10 && s.streakFreezes in 0..2 && s.freezeWeeksRewarded >= 0) { R.string.backup_error_settings }
    requireBackup(s.themeMode in setOf("SYSTEM", "LIGHT", "DARK")) { R.string.backup_error_theme }
    requireBackup(s.clockFormat in setOf("SYSTEM", "H12", "H24")) { R.string.backup_error_clock }
    requireBackup(s.petTapCount in 0..1000) { R.string.backup_error_pet_count }
    requireBackup(s.pebbleItems.size <= 8 && s.pebbleItems.all { it.matches(Regex("[a-z][a-z0-9_]{0,63}")) }) { R.string.backup_error_items }
    requireBackup(s.notificationDeliveryTimes.all { it.toIntOrNull() in 0..1439 }) { R.string.backup_error_delivery_time }
    requireBackup(s.freezeRewardedThrough.isEmpty() || runCatching { LocalDate.parse(s.freezeRewardedThrough) }.isSuccess) { R.string.backup_error_freeze_reward }
    requireBackup(s.frozenDays.all { runCatching { LocalDate.parse(it) }.isSuccess }) { R.string.backup_error_streak_date }
    requireBackup(limits.map { it.packageName }.distinct().size == limits.size && limits.all {
        it.packageName.isNotBlank() && it.minutesPerDay in 1..1440 && it.reminderMinutes in 0..120
    }) { R.string.backup_error_limits }
    requireBackup(schedules.map { it.id }.distinct().size == schedules.size && schedules.all {
        it.id > 0 && it.name.isNotBlank() && it.startMinute in 0..1439 && it.endMinute in 0..1439 &&
            it.days in 1..127 && it.focusMinutes in 1..240 && it.icon in SCHEDULE_ICONS
    }) { R.string.backup_error_schedules }
    requireBackup(sites.map { it.domain }.distinct().size == sites.size && sites.all { hostOf(it.domain) == it.domain }) { R.string.backup_error_sites }
    // Twelve four-hour Pomodoro rounds are the longest producible focus record.
    requireBackup(sessions.map { it.id }.distinct().size == sessions.size && sessions.all {
        it.id > 0 && it.startedAt > 0 && it.endedAt >= it.startedAt && it.focusedMillis in 0..172_800_000L &&
            it.focusedMillis <= it.endedAt - it.startedAt && it.goalMinutes in 5..1440 && it.questVersion in 0..1 &&
            (it.rewardDay.isEmpty() || runCatching { LocalDate.parse(it.rewardDay) }.isSuccess) && it.rewardStartHour in -1..23
    }) { R.string.backup_error_focus_history }
    // A civil day can exceed 24 hours after a clock or time-zone change.
    requireBackup(usageDays.map { it.day }.distinct().size == usageDays.size && usageDays.all {
        runCatching { LocalDate.parse(it.day) }.isSuccess && it.unlocks >= 0 && it.heldCount >= 0 &&
            it.perApp.all { (pkg, value) -> pkg.isNotBlank() && value in 0..172_800_000L } &&
            it.limitMinutes.all { (pkg, value) -> pkg.isNotBlank() && value in 1..1440 }
    }) { R.string.backup_error_usage_history }
    // Old files have no setup state or goal snapshot.
    return if (version == 1) copy(settings = s.copy(onboarded = true), sessions = sessions.map { it.copy(goalMinutes = s.focusGoalMinutes) }) else this
}
