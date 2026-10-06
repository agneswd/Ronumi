package dev.agneswd.stillpoint.ui.design

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dev.agneswd.stillpoint.R

/** Short phrases on recorded instruments. See docs/audio-sources.md for origins and processing. */
enum class Sound(val res: Int, val volume: Float = 1f) {
    TAP(R.raw.sfx_tap),
    TOGGLE_ON(R.raw.sfx_toggle_on),
    TOGGLE_OFF(R.raw.sfx_toggle_off),
    SELECT(R.raw.sfx_select),
    START(R.raw.sfx_start),
    COMPLETE(R.raw.sfx_complete),
    LEVEL_UP(R.raw.sfx_level_up),
    STREAK(R.raw.sfx_streak),
    QUEST(R.raw.sfx_quest),
    GIVE_UP(R.raw.sfx_give_up),
    BLOCK(R.raw.sfx_block),
    WELCOME(R.raw.sfx_welcome),
    QUESTION(R.raw.sfx_question),
    SLIDE(R.raw.sfx_slide),
    NOTIFICATION(R.raw.sfx_notification),
}

/**
 * Plays sound effects through the media volume, like games do.
 * Call [init] once at app start. The user can turn sounds off in Settings.
 */
object Sfx {
    private const val PREFS = "ui"
    private const val KEY = "soundEffects"

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<Sound, Int>()
    private val prefsLock = Any()
    private var prefs: android.content.SharedPreferences? = null
    private var pendingEnabled: Boolean? = null

    fun init(context: Context) {
        if (pool != null) return
        val appContext = context.applicationContext
        // The preference file is read off the main thread. Until it loads, sound stays on.
        Thread({
            val loaded = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val pending = synchronized(prefsLock) {
                prefs = loaded
                pendingEnabled.also { pendingEnabled = null }
            }
            if (pending != null) loaded.edit().putBoolean(KEY, pending).apply()
        }, "sfx-prefs").start()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build().also { p ->
            // Loading is asynchronous. A sound that is not ready yet stays silent once.
            Sound.entries.forEach { ids[it] = p.load(context, it.res, 1) }
        }
    }

    var enabled: Boolean
        get() = synchronized(prefsLock) { pendingEnabled ?: prefs?.getBoolean(KEY, true) ?: true }
        set(value) {
            val current = synchronized(prefsLock) {
                val loaded = prefs
                if (loaded == null) {
                    pendingEnabled = value
                    null
                } else loaded
            }
            current?.edit()?.putBoolean(KEY, value)?.apply()
        }

    fun play(sound: Sound) {
        if (!enabled) return
        val id = ids[sound] ?: return
        pool?.play(id, sound.volume, sound.volume, 1, 0, 1f)
    }
}
