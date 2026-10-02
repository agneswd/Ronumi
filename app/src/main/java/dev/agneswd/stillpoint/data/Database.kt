package dev.agneswd.stillpoint.data

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Dao
interface StillpointDao {
    @Query("SELECT * FROM Settings WHERE id = 0")
    fun settingsFlow(): Flow<Settings?>

    @Query("SELECT * FROM Settings WHERE id = 0")
    suspend fun settingsOrNull(): Settings?

    @Upsert
    suspend fun saveSettings(settings: Settings)

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

    /** Replaces every user-made row with the content of a backup. */
    @Transaction
    suspend fun replaceAll(backup: Backup) {
        clearLimits()
        clearSchedules()
        clearSites()
        clearSessions()
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

private val settingsLock = Mutex()

/** Read, change and save the settings row. The lock keeps quick taps from losing changes. */
suspend fun StillpointDao.updateSettings(change: (Settings) -> Settings) = settingsLock.withLock {
    saveSettings(change(currentSettings()))
}

class Converters {
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
    ],
    version = 2,
)
@TypeConverters(Converters::class)
abstract class StillpointDatabase : RoomDatabase() {
    abstract fun dao(): StillpointDao

    companion object {
        fun open(context: Context): StillpointDatabase =
            Room.databaseBuilder(context, StillpointDatabase::class.java, "stillpoint.db")
                // Nothing is released yet, so a schema change may start from an empty database.
                // Add real migrations before the first public release.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
