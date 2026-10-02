package dev.agneswd.stillpoint.ui.design

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dev.agneswd.stillpoint.R

/** Quiet library sounds. See docs/audio-sources.md for origins and processing. */
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
    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        if (pool != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
        get() = !::prefs.isInitialized || prefs.getBoolean(KEY, true)
        set(value) = prefs.edit().putBoolean(KEY, value).apply()

    fun play(sound: Sound) {
        if (!enabled) return
        val id = ids[sound] ?: return
        pool?.play(id, sound.volume, sound.volume, 1, 0, 1f)
    }
}
