package dev.agneswd.stillpoint.focus

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import dev.agneswd.stillpoint.data.FocusSound
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Generates focus sounds locally. One worker owns the track until its fade ends. */
class NoisePlayer {
    @Volatile
    private var sound = FocusSound.OFF
    private var thread: Thread? = null

    @Synchronized
    fun play(next: FocusSound) {
        sound = next
        if (next != FocusSound.OFF && thread == null) startWorker()
    }

    @Synchronized
    fun stop() {
        sound = FocusSound.OFF
    }

    private fun startWorker() {
        thread = Thread(::loop, "focus-noise").apply { start() }
    }

    private fun loop() {
        val buffer = ShortArray(NoiseGenerator.SAMPLE_RATE / 50)
        val generator = NoiseGenerator()
        var track: AudioTrack? = null
        try {
            val playback = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(NoiseGenerator.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(buffer.size * 4, AudioTrack.getMinBufferSize(
                    NoiseGenerator.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
                .build().also { track = it }
            playback.play()
            var totalWritten = 0L
            while (true) {
                generator.render(sound, buffer)
                var written = 0
                while (written < buffer.size) {
                    val count = playback.write(buffer, written, buffer.size - written)
                    check(count > 0) { "AudioTrack write failed: $count" }
                    written += count
                    totalWritten += count
                }
                if (synchronized(this) { sound == FocusSound.OFF && generator.isSilent }) {
                    // Let the queued fade play before releasing the track.
                    val deadline = System.nanoTime() + 500_000_000
                    while ((totalWritten - playback.playbackHeadPosition.toLong()) and 0xffff_ffffL != 0L &&
                        System.nanoTime() < deadline) Thread.sleep(5)
                    return
                }
            }
        } catch (error: RuntimeException) {
            synchronized(this) { sound = FocusSound.OFF }
            Log.w("NoisePlayer", "Focus audio stopped", error)
        } finally {
            track?.let {
                runCatching { it.stop() }
                runCatching { it.release() }
            }
            synchronized(this) {
                thread = null
                // A play request can arrive while the old track drains and releases.
                if (sound != FocusSound.OFF) startWorker()
            }
        }
    }
}

/** Stateful PCM generation. Filters and envelopes continue across output buffers. */
internal class NoiseGenerator(private val random: Random = Random.Default) {
    private var current = FocusSound.OFF
    private var gain = 0f
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var brown = 0f
    private var rainLow = 0f
    private var rainSlow = 0f
    private var dropIn = 0
    private var nextDrop = 0
    private val dropFirst = FloatArray(4)
    private val dropSecond = FloatArray(4)
    private val dropCoefficient = FloatArray(4)
    private val dropDecay = FloatArray(4)
    private val dropRemaining = IntArray(4)
    private var waveSample = 0
    private var waveLength = SAMPLE_RATE * 8

    val isSilent: Boolean get() = current == FocusSound.OFF && gain == 0f

    fun render(requested: FocusSound, output: ShortArray) {
        for (i in output.indices) {
            if (current != requested && gain == 0f) {
                current = requested
                resetFilters()
            }
            gain = if (current == requested && current != FocusSound.OFF) {
                minOf(1f, gain + 1f / (SAMPLE_RATE * 0.12f))
            } else maxOf(0f, gain - 1f / (SAMPLE_RATE * 0.08f))
            val sample = if (current == FocusSound.OFF) 0f else sample()
            output[i] = (sample.coerceIn(-1f, 1f) * gain * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun resetFilters() {
        b0 = 0f
        b1 = 0f
        b2 = 0f
        brown = 0f
        rainLow = 0f
        rainSlow = 0f
        dropIn = 0
        dropRemaining.fill(0)
        waveSample = 0
        waveLength = random.nextInt(SAMPLE_RATE * 6, SAMPLE_RATE * 10 + 1)
    }

    private fun sample(): Float {
        val white = random.nextFloat() * 2f - 1f
        return when (current) {
            FocusSound.WHITE -> white * 0.25f
            FocusSound.PINK -> {
                // Paul Kellet's economy filter.
                b0 = 0.99765f * b0 + white * 0.0990460f
                b1 = 0.96300f * b1 + white * 0.2965164f
                b2 = 0.57000f * b2 + white * 1.0526913f
                (b0 + b1 + b2 + white * 0.1848f) * 0.08f
            }
            FocusSound.BROWN -> brown(white) * 2.5f
            FocusSound.RAIN -> rain(white)
            FocusSound.WAVES -> {
                val triangle = 1f - abs(2f * waveSample / waveLength - 1f)
                val swell = triangle * triangle * (3f - 2f * triangle)
                if (++waveSample >= waveLength) {
                    waveSample = 0
                    waveLength = random.nextInt(SAMPLE_RATE * 6, SAMPLE_RATE * 10 + 1)
                }
                brown(white) * 2.5f * (0.45f + 0.9f * swell)
            }
            FocusSound.OFF -> 0f
        }
    }

    private fun brown(white: Float): Float {
        brown = (brown + white * 0.02f) / 1.02f
        return brown
    }

    private fun rain(white: Float): Float {
        rainLow += 0.12f * (white - rainLow)
        rainSlow += 0.005f * (white - rainSlow)
        var sample = (rainLow - rainSlow) * 0.95f
        if (dropIn-- <= 0) {
            val voice = nextDrop
            nextDrop = (nextDrop + 1) % dropFirst.size
            val angle = 2.0 * Math.PI * random.nextInt(900, 2800) / SAMPLE_RATE
            val decay = 0.990f + random.nextFloat() * 0.005f
            dropFirst[voice] = sin(angle).toFloat() * (0.04f + random.nextFloat() * 0.08f)
            dropSecond[voice] = 0f
            dropCoefficient[voice] = 2f * decay * cos(angle).toFloat()
            dropDecay[voice] = decay * decay
            dropRemaining[voice] = SAMPLE_RATE / 12
            dropIn = random.nextInt(SAMPLE_RATE / 70, SAMPLE_RATE / 20)
        }
        for (voice in dropFirst.indices) {
            val remaining = dropRemaining[voice]
            if (remaining == 0) continue
            val next = dropCoefficient[voice] * dropFirst[voice] - dropDecay[voice] * dropSecond[voice]
            val attack = minOf(1f, (SAMPLE_RATE / 12 - remaining) / (SAMPLE_RATE * 0.002f))
            sample += next * attack
            dropSecond[voice] = dropFirst[voice]
            dropFirst[voice] = next
            dropRemaining[voice]--
        }
        return sample
    }

    companion object {
        const val SAMPLE_RATE = 22_050
    }
}
