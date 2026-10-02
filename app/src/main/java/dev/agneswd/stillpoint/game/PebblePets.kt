package dev.agneswd.stillpoint.game

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.room.withTransaction
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.currentSettings
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Persists taps outside the screen lifecycle. Room serializes increments from every Pebble instance. */
object PebblePets {
    private val restoreGeneration = AtomicLong()

    fun pet(context: Context): Job {
        val app = context.app
        val generation = restoreGeneration.get()
        return app.scope.launch {
            try {
                val unlocked = app.database.withTransaction {
                    if (restoreGeneration.get() != generation) return@withTransaction false
                    val before = app.dao.currentSettings().petTapCount
                    app.dao.recordPetTap()
                    before == PebbleStyles.SECRET_PET_TAPS - 1
                }
                if (unlocked) withContext(Dispatchers.Main) {
                    if (restoreGeneration.get() == generation) {
                        Toast.makeText(app, "Star guardian unlocked! Find it in Pebble's wardrobe.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.w("PebblePets", "Could not save Pebble tap", error)
            }
        }
    }

    /** Call inside the restore transaction, before replacing settings. Old queued taps are then discarded. */
    fun invalidatePendingTaps() {
        restoreGeneration.incrementAndGet()
    }
}
