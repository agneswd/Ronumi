package dev.agneswd.ronumi.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface StillpointDao {
    @Query("SELECT * FROM Settings WHERE id = 0")
    fun settingsFlow(): Flow<Settings?>

    @Query("SELECT * FROM Settings WHERE id = 0")
    suspend fun settingsOrNull(): Settings?

    @Upsert
    suspend fun saveSettings(settings: Settings)

    @Transaction
    suspend fun changeSettings(change: (Settings) -> Settings) {
        saveSettings(change(settingsOrNull() ?: Settings()))
    }

    @Query("UPDATE Settings SET petTapCount = petTapCount + 1 WHERE id = 0 AND petTapCount < 1000")
    suspend fun incrementPetTapCount()

    @Transaction
    suspend fun recordPetTap() {
        if (settingsOrNull() == null) saveSettings(Settings())
        incrementPetTapCount()
    }

    @Query("SELECT * FROM AppLimit ORDER BY packageName")
    fun limits(): Flow<List<AppLimit>>

    @Upsert
    suspend fun saveLimit(limit: AppLimit)

    @Delete
    suspend fun deleteLimit(limit: AppLimit)

    @Query("SELECT * FROM Schedule ORDER BY startMinute")
    fun schedules(): Flow<List<Schedule>>

    @Upsert
    suspend fun saveSchedule(schedule: Schedule)

    @Delete
    suspend fun deleteSchedule(schedule: Schedule)

    @Query("SELECT * FROM BlockedSite ORDER BY domain")
    fun sites(): Flow<List<BlockedSite>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSite(site: BlockedSite)

    @Delete
    suspend fun deleteSite(site: BlockedSite)

    @Query("SELECT * FROM FocusSession ORDER BY startedAt DESC")
    fun sessions(): Flow<List<FocusSession>>

    @Insert
    suspend fun addSession(session: FocusSession): Long

    @Upsert
    suspend fun saveSession(session: FocusSession)

    @Delete
    suspend fun deleteSession(session: FocusSession)

    @Query("SELECT * FROM ActiveFocus WHERE id = 0")
    fun activeFocusFlow(): Flow<ActiveFocus?>

    @Query("SELECT * FROM ActiveFocus WHERE id = 0")
    suspend fun activeFocus(): ActiveFocus?

    @Upsert
    suspend fun saveActiveFocus(focus: ActiveFocus)

    @Query("DELETE FROM ActiveFocus")
    suspend fun clearActiveFocus()

    @Query("SELECT * FROM HeldNotification ORDER BY postedAt DESC")
    fun held(): Flow<List<HeldNotification>>

    @Insert
    suspend fun hold(notification: HeldNotification)

    @Query("DELETE FROM HeldNotification")
    suspend fun clearHeld()

    @Query("SELECT COUNT(*) FROM HeldNotification")
    suspend fun heldCount(): Int

    @Query("SELECT * FROM AppLimit")
    suspend fun allLimits(): List<AppLimit>

    @Query("SELECT * FROM Schedule")
    suspend fun allSchedules(): List<Schedule>

    @Query("SELECT * FROM BlockedSite")
    suspend fun allSites(): List<BlockedSite>

    @Query("SELECT * FROM FocusSession")
    suspend fun allSessions(): List<FocusSession>

    @Query("DELETE FROM AppLimit")
    suspend fun clearLimits()

    @Query("DELETE FROM Schedule")
    suspend fun clearSchedules()

    @Query("DELETE FROM BlockedSite")
    suspend fun clearSites()

    @Query("DELETE FROM FocusSession")
    suspend fun clearSessions()

    @Query("SELECT * FROM LimitPass WHERE day = :day AND packageName = :packageName")
    suspend fun pass(day: String, packageName: String): LimitPass?

    @Query("SELECT COALESCE(SUM(uses), 0) FROM LimitPass WHERE day = :day")
    suspend fun passesUsed(day: String): Int

    @Upsert
    suspend fun savePass(pass: LimitPass)

    @Query("DELETE FROM LimitPass")
    suspend fun clearPasses()

    @Query("SELECT * FROM UsageDay ORDER BY day")
    fun usageDays(): Flow<List<UsageDay>>

    @Query("SELECT * FROM UsageDay ORDER BY day")
    suspend fun allUsageDays(): List<UsageDay>

    @Upsert
    suspend fun saveUsageDay(day: UsageDay)

    @Query("DELETE FROM UsageDay")
    suspend fun clearUsageDays()

    @Query("SELECT * FROM HeldNotification WHERE notificationKey = :key LIMIT 1")
    suspend fun heldByKey(key: String): HeldNotification?

    @Upsert
    suspend fun saveHeld(notification: HeldNotification)

    @Query("SELECT * FROM HeldNotification ORDER BY postedAt DESC")
    suspend fun allHeld(): List<HeldNotification>

    @Query("DELETE FROM HeldNotification WHERE id IN (:ids)")
    suspend fun deleteHeld(ids: List<Long>)

    @Query("SELECT * FROM UsageDay WHERE day = :day")
    suspend fun usageDay(day: String): UsageDay?

    @Transaction
    suspend fun recordHeld(notification: HeldNotification, day: String) {
        val previous = heldByKey(notification.notificationKey)
        saveHeld(notification.copy(id = previous?.id ?: 0))
        if (previous == null) {
            val usage = usageDay(day) ?: UsageDay(day, emptyMap(), 0)
            saveUsageDay(usage.copy(heldCount = usage.heldCount + 1))
        }
    }

    @Transaction
    suspend fun recordUsage(day: UsageDay) {
        val previous = usageDay(day.day)
        val keepPast = day.perApp.isEmpty() && day.day < java.time.LocalDate.now().toString() && previous != null
        val perApp = if (keepPast) previous!!.perApp else day.perApp
        val budgets = if (day.day == java.time.LocalDate.now().toString()) {
            allLimits().filter { it.enabled }.associate { it.packageName to it.minutesPerDay.toLong() }
        } else emptyMap()
        saveUsageDay(day.copy(perApp = perApp, unlocks = if (keepPast) previous!!.unlocks else day.unlocks, heldCount = previous?.heldCount ?: 0,
            limitMinutes = budgets + previous?.limitMinutes.orEmpty()))
    }

    @Query("UPDATE LimitPass SET expiresAt = 0")
    suspend fun expirePasses()

    /** Replaces every user-made row with the content of a backup. */
    @Transaction
    suspend fun replaceAll(backup: Backup) {
        clearLimits()
        clearSchedules()
        clearSites()
        clearSessions()
        clearUsageDays()
        clearHeld()
        expirePasses()
        backup.usageDays.forEach { saveUsageDay(it) }
        backup.limits.forEach { saveLimit(it) }
        backup.schedules.forEach { saveSchedule(it) }
        backup.sites.forEach { addSite(it) }
        backup.sessions.forEach { saveSession(it) }
        saveSettings(backup.settings.copy(id = 0))
    }
}

/** Settings with defaults when the row does not exist yet. */
fun StillpointDao.settings(): Flow<Settings> = settingsFlow().map { it ?: Settings() }

suspend fun StillpointDao.currentSettings(): Settings = settingsOrNull() ?: Settings()

/** Room serializes read-modify-write changes with other database transactions. */
suspend fun StillpointDao.updateSettings(change: (Settings) -> Settings) = changeSettings(change)

class Converters {
    @TypeConverter
    fun fromUsage(value: Map<String, Long>): String = Json.encodeToString(value)

    @TypeConverter
    fun toUsage(value: String): Map<String, Long> = Json.decodeFromString(value)

    @TypeConverter
    fun fromSet(value: Set<String>): String = value.joinToString("\n")

    @TypeConverter
    fun toSet(value: String): Set<String> = value.split("\n").filter { it.isNotBlank() }.toSet()
}

@Database(
    entities = [
        AppLimit::class,
        Schedule::class,
        BlockedSite::class,
        FocusSession::class,
        HeldNotification::class,
        Settings::class,
        ActiveFocus::class,
        LimitPass::class,
        UsageDay::class,
    ],
    version = 8,
)
@TypeConverters(Converters::class)
abstract class StillpointDatabase : RoomDatabase() {
    abstract fun dao(): StillpointDao

    companion object {
        fun open(context: Context): StillpointDatabase =
            Room.databaseBuilder(context, StillpointDatabase::class.java, "stillpoint.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()
    }
}

/** Preserves the first version's data when the playful interface adds timer and game settings. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val settings = mapOf(
            "timerMode" to "TEXT NOT NULL DEFAULT 'TIMER'",
            "longBreakMinutes" to "INTEGER NOT NULL DEFAULT 15",
            "focusTheme" to "TEXT NOT NULL DEFAULT 'LAKE'",
            "onboarded" to "INTEGER NOT NULL DEFAULT 0",
            "purpose" to "TEXT NOT NULL DEFAULT ''",
            "distractions" to "TEXT NOT NULL DEFAULT ''",
            "streakFreezes" to "INTEGER NOT NULL DEFAULT 1",
            "frozenDays" to "TEXT NOT NULL DEFAULT ''",
            "freezeWeeksRewarded" to "INTEGER NOT NULL DEFAULT 0",
        )
        settings.forEach { (column, definition) -> db.execSQL("ALTER TABLE Settings ADD COLUMN $column $definition") }
        val active = mapOf(
            "timerMode" to "TEXT NOT NULL DEFAULT 'POMODORO'",
            "pausedAt" to "INTEGER NOT NULL DEFAULT 0",
            "longBreakMinutes" to "INTEGER NOT NULL DEFAULT 15",
            "theme" to "TEXT NOT NULL DEFAULT 'LAKE'",
        )
        active.forEach { (column, definition) -> db.execSQL("ALTER TABLE ActiveFocus ADD COLUMN $column $definition") }
        // Existing users keep their plan and do not repeat the first-launch questions.
        db.execSQL("UPDATE Settings SET onboarded = 1, timerMode = 'POMODORO'")
    }
}

/** Adds local policy settings and usage records without replacing existing tables. */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val columns = mapOf(
            "AppLimit" to mapOf("reminderMinutes" to "INTEGER NOT NULL DEFAULT 0"),
            "Schedule" to mapOf("startFocus" to "INTEGER NOT NULL DEFAULT 0", "focusMinutes" to "INTEGER NOT NULL DEFAULT 25"),
            "FocusSession" to mapOf("goalMinutes" to "INTEGER NOT NULL DEFAULT 120"),
            "ActiveFocus" to mapOf("goalMinutes" to "INTEGER NOT NULL DEFAULT 120"),
            "HeldNotification" to mapOf("notificationKey" to "TEXT NOT NULL DEFAULT ''"),
            "Settings" to mapOf(
                "goalDays" to "INTEGER NOT NULL DEFAULT 127",
                "allowFirstShort" to "INTEGER NOT NULL DEFAULT 0",
                "contentOnlyDuringFocus" to "INTEGER NOT NULL DEFAULT 0",
                "siteAllowList" to "INTEGER NOT NULL DEFAULT 0",
                "youtubeStudyMode" to "INTEGER NOT NULL DEFAULT 0",
                "allowedYoutubeChannels" to "TEXT NOT NULL DEFAULT ''",
                "blockYoutubeHome" to "INTEGER NOT NULL DEFAULT 0",
                "blockMultiWindow" to "INTEGER NOT NULL DEFAULT 0",
                "pauseBlocksUntil" to "INTEGER NOT NULL DEFAULT 0",
                "emergencyPassesPerDay" to "INTEGER NOT NULL DEFAULT 3",
                "notificationDeliveryTimes" to "TEXT NOT NULL DEFAULT ''",
                "productivePackages" to "TEXT NOT NULL DEFAULT ''",
            ),
        )
        columns.forEach { (table, fields) ->
            fields.forEach { (column, definition) -> db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition") }
        }
        db.execSQL("UPDATE FocusSession SET goalMinutes = COALESCE((SELECT focusGoalMinutes FROM Settings WHERE id = 0), 120)")
        db.execSQL("UPDATE ActiveFocus SET goalMinutes = COALESCE((SELECT focusGoalMinutes FROM Settings WHERE id = 0), 120)")
        db.execSQL("UPDATE HeldNotification SET notificationKey = 'legacy-' || id")
        db.execSQL("CREATE TABLE IF NOT EXISTS LimitPass (day TEXT NOT NULL, packageName TEXT NOT NULL, expiresAt INTEGER NOT NULL, uses INTEGER NOT NULL, PRIMARY KEY(day, packageName))")
        db.execSQL("CREATE TABLE IF NOT EXISTS UsageDay (day TEXT NOT NULL PRIMARY KEY, perApp TEXT NOT NULL, unlocks INTEGER NOT NULL, heldCount INTEGER NOT NULL, limitMinutes TEXT NOT NULL)")
    }
}

/** Keeps old quest rewards and adds the equipped wardrobe without removing history. */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE FocusSession ADD COLUMN questVersion INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Settings ADD COLUMN pebbleItems TEXT NOT NULL DEFAULT ''")
    }
}

/** Keeps existing notification behavior and selects schedule icons automatically after upgrade. */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Settings ADD COLUMN notifyFocusEvents INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE Settings ADD COLUMN notifyPlanReminders INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE Settings ADD COLUMN notifyInboxSummaries INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE Schedule ADD COLUMN icon TEXT NOT NULL DEFAULT 'auto'")
        db.execSQL("ALTER TABLE Settings ADD COLUMN petTapCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Settings ADD COLUMN themeMode TEXT NOT NULL DEFAULT 'SYSTEM'")
        db.execSQL("ALTER TABLE Settings ADD COLUMN autoUpdateChecks INTEGER NOT NULL DEFAULT 1")
    }
}

/** Adds monotonic focus checkpoints and stable calendar reward metadata. */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Settings ADD COLUMN freezeRewardedThrough TEXT NOT NULL DEFAULT ''")
        for (table in listOf("FocusSession", "ActiveFocus")) {
            db.execSQL("ALTER TABLE $table ADD COLUMN rewardDay TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $table ADD COLUMN rewardStartHour INTEGER NOT NULL DEFAULT -1")
        }
        db.execSQL("ALTER TABLE ActiveFocus ADD COLUMN phaseElapsedMillis INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ActiveFocus ADD COLUMN phaseAnchorElapsed INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE ActiveFocus ADD COLUMN bootCount INTEGER NOT NULL DEFAULT -1")
        // The old schema has only wall timestamps. Preserve its progress once during this upgrade.
        db.execSQL("UPDATE ActiveFocus SET phaseElapsedMillis = MAX(0, MIN(phaseEndsAt - phaseStartedAt, (CASE WHEN pausedAt > 0 THEN pausedAt ELSE ? END) - phaseStartedAt))", arrayOf(System.currentTimeMillis()))
    }
}

/** Adds the Live Update switch for the focus timer. It starts on, like the other focus notifications. */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Settings ADD COLUMN liveFocusTimer INTEGER NOT NULL DEFAULT 1")
    }
}

/** Clock text follows the phone until the user picks 12-hour or 24-hour. */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Settings ADD COLUMN clockFormat TEXT NOT NULL DEFAULT 'SYSTEM'")
    }
}
