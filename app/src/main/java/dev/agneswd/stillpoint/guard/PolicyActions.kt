package dev.agneswd.stillpoint.guard

import android.content.Context
import androidx.room.withTransaction
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.LimitMode
import dev.agneswd.stillpoint.data.LimitPass
import dev.agneswd.stillpoint.data.Schedule
import dev.agneswd.stillpoint.data.currentSettings
import dev.agneswd.stillpoint.data.updateSettings
import java.time.LocalDate
import java.time.LocalDateTime

/** Rechecks current rules before spending a pass. Old block screens cannot bypass a new strict block. */
object PolicyActions {
    /** Every schedule editor uses this check, including an editor opened before a block starts. */
    suspend fun saveSchedule(context: Context, schedule: Schedule, delete: Boolean = false): Boolean =
        unlessLocked(context, "Schedules are locked until the current block ends.") {
            if (delete) context.app.dao.deleteSchedule(schedule) else context.app.dao.saveSchedule(schedule)
        }

    /**
     * Saves a change to blocks, limits or sites unless protection locks them.
     * A page opened before a block starts stays open, so the lock is checked again here.
     */
    suspend fun changeBlocks(context: Context, change: suspend () -> Unit): Boolean =
        unlessLocked(context, "Blocks are locked until the current block ends.", change)

    private suspend fun unlessLocked(context: Context, message: String, change: suspend () -> Unit): Boolean {
        val changed = context.app.database.withTransaction {
            val dao = context.app.dao
            if (dao.currentSettings().protection && Rules(schedules = dao.allSchedules(), focus = dao.activeFocus()).locked(LocalDateTime.now())) {
                return@withTransaction false
            }
            change()
            true
        }
        if (!changed) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
        return changed
    }

    suspend fun grantExtra(context: Context, pkg: String): Boolean = context.app.database.withTransaction {
        val dao = context.app.dao
        val limit = dao.allLimits().firstOrNull { it.packageName == pkg && it.enabled } ?: return@withTransaction false
        if (limit.mode != LimitMode.GENTLE) return@withTransaction false
        val s = dao.currentSettings()
        val rules = Rules(settings = s, schedules = dao.allSchedules(), focus = dao.activeFocus())
        if (rules.focusing && rules.focus!!.mode.blocks(pkg, rules.focus.packages) ||
            rules.activeSchedules(LocalDateTime.now()).any { it.mode.blocks(pkg, it.packages) }) return@withTransaction false
        val today = LocalDate.now().toString()
        if (dao.passesUsed(today) >= s.emergencyPassesPerDay) return@withTransaction false
        val previous = dao.pass(today, pkg)
        dao.savePass(LimitPass(today, pkg, System.currentTimeMillis() + 5 * 60_000, (previous?.uses ?: 0) + 1))
        true
    }

    suspend fun pauseBlocks(context: Context, minutes: Int): Boolean {
        val dao = context.app.dao
        return context.app.database.withTransaction {
            val s = dao.currentSettings()
            val rules = Rules(settings = s, schedules = dao.allSchedules(), focus = dao.activeFocus())
            if (rules.focus?.strict == true || s.protection && rules.locked(LocalDateTime.now())) return@withTransaction false
            dao.updateSettings { it.copy(pauseBlocksUntil = if (minutes == 0) 0 else System.currentTimeMillis() + minutes.coerceIn(1, 60) * 60_000) }
            true
        }
    }
}
