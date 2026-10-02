package dev.agneswd.stillpoint.focus

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dev.agneswd.stillpoint.data.FocusSound
import kotlin.random.Random

/**
 * Makes white, pink or brown noise on the device. No audio files, no network.
 * One background thread writes samples while a sound plays.
 */
class NoisePlayer {
    @Volatile
    private var sound = FocusSound.OFF
    private var thread: Thread? = null

    fun play(next: FocusSound) {
        sound = next
        if (thread?.isAlive == true) return
        thread = Thread(::loop, "focus-noise").apply { start() }
    }

    fun stop() {
        sound = FocusSound.OFF
        thread = null
    }

    private fun loop() {
        val buffer = ShortArray(SAMPLE_RATE / 10)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(buffer.size * 2 * 2)
            .build()
        track.play()
        val random = Random.Default
        // Pink noise filter state (Paul Kellet's economy filter) and brown noise state.
        var b0 = 0f; var b1 = 0f; var b2 = 0f
        var brown = 0f
        var gain = 0f
        while (sound != FocusSound.OFF) {
            val current = sound
            for (i in buffer.indices) {
                val white = random.nextFloat() * 2f - 1f
                val sample = when (current) {
                    FocusSound.WHITE -> white * 0.25f
                    FocusSound.PINK -> {
                        b0 = 0.99765f * b0 + white * 0.0990460f
                        b1 = 0.96300f * b1 + white * 0.2965164f
                        b2 = 0.57000f * b2 + white * 1.0526913f
                        (b0 + b1 + b2 + white * 0.1848f) * 0.08f
                    }
                    FocusSound.BROWN -> {
                        brown = (brown + white * 0.02f) / 1.02f
                        brown * 2.5f
                    }
                    FocusSound.OFF -> 0f
                }
                // Fade in over about two seconds so the sound does not start with a jump.
                gain = minOf(1f, gain + 1f / (SAMPLE_RATE * 2))
                buffer[i] = (sample.coerceIn(-1f, 1f) * gain * Short.MAX_VALUE).toInt().toShort()
            }
            track.write(buffer, 0, buffer.size)
        }
        track.stop()
        track.release()
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
    }
}
