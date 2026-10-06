package dev.agneswd.stillpoint.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import dev.agneswd.stillpoint.data.FocusSound
import java.io.File
import java.nio.ByteOrder

/** Plays bundled library recordings offline. One worker owns the track until its fade ends. */
class NoisePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val assets = appContext.assets
    private val pcmCache = HashMap<FocusSound, ShortArray>()
    @Volatile
    private var sound = FocusSound.OFF
    @Volatile
    private var requestedAt = 0L
    private var thread: Thread? = null

    @Synchronized
    fun play(next: FocusSound) {
        requestedAt = SystemClock.elapsedRealtime()
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
        val buffer = ShortArray(FocusLoopRenderer.SAMPLE_RATE / 50)
        val generator = FocusLoopRenderer(::loadPcm)
        var track: AudioTrack? = null
        var loggedFirstFrame = false
        var totalWritten = 0L
        var pendingAt = 0
        var held = false
        // One rendered buffer. A full track returns 0 in non-blocking mode so decode can continue.
        fun writeOne(playback: AudioTrack, blocking: Boolean): Boolean {
            if (!held) {
                if (generator.buffered() < buffer.size) return false
                generator.render(sound, buffer)
                pendingAt = 0
                held = true
            }
            val mode = if (blocking) AudioTrack.WRITE_BLOCKING else AudioTrack.WRITE_NON_BLOCKING
            val count = playback.write(buffer, pendingAt, buffer.size - pendingAt, mode)
            if (count == 0) {
                check(!blocking) { "AudioTrack write failed: 0" }
                return false
            }
            check(count > 0) { "AudioTrack write failed: $count" }
            pendingAt += count
            totalWritten += count
            if (!loggedFirstFrame) {
                loggedFirstFrame = true
                Log.i("NoisePlayer", "first frame ${SystemClock.elapsedRealtime() - requestedAt}ms")
            }
            if (pendingAt >= buffer.size) held = false
            return true
        }
        try {
            val playback = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(FocusLoopRenderer.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(buffer.size * 4, AudioTrack.getMinBufferSize(
                    FocusLoopRenderer.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
                .build().also { track = it }
            playback.play()
            val initial = synchronized(this) { sound }
            if (initial != FocusSound.OFF) {
                val cached = synchronized(pcmCache) { pcmCache[initial] }
                if (cached != null) {
                    generator.prepare(initial)
                    writeOne(playback, blocking = true)
                } else {
                    val decoded = decodeOgg(initial) { storage, from, valid ->
                        generator.follow(initial, storage, from, valid)
                        while (writeOne(playback, blocking = false)) Unit
                    }
                    val consumed = (generator.cursor() - decoded.playedFrom).coerceAtLeast(0)
                    generator.adoptLoop(decoded.pcm, if (decoded.pcm.isEmpty()) 0 else consumed % decoded.pcm.size)
                }
            }
            while (true) {
                writeOne(playback, blocking = true)
                if (synchronized(this) { sound == FocusSound.OFF && generator.isSilent }) {
                    // Let the queued fade play before releasing the track.
                    val deadline = System.nanoTime() + 500_000_000
                    while ((totalWritten - playback.playbackHeadPosition.toLong()) and 0xffff_ffffL != 0L &&
                        System.nanoTime() < deadline) Thread.sleep(5)
                    return
                }
            }
        } catch (error: Exception) {
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

    /** Decodes one bundled Ogg Vorbis loop into the PCM buffer the renderer already uses. */
    private fun loadPcm(sound: FocusSound): ShortArray {
        synchronized(pcmCache) { pcmCache[sound]?.let { return it } }
        val decoded = decodeOgg(sound)
        synchronized(pcmCache) {
            pcmCache[sound] = decoded.pcm
            return decoded.pcm
        }
    }

    /**
     * Decodes one loop. [onWindow] runs on this thread once a render buffer of real samples exists,
     * and again as more samples arrive, so playback can start before the rest of the file is done.
     * The window's [from] index skips the encoder delay. Playback writes without blocking this
     * decoder. The deadline only stops a stuck codec, and a short result keeps the samples decoded.
     */
    private fun decodeOgg(
        sound: FocusSound,
        onWindow: ((storage: ShortArray, from: Int, valid: Int) -> Unit)? = null,
    ): DecodedPcm {
        val name = sound.name.lowercase(java.util.Locale.ROOT)
        val expected = if (sound == FocusSound.PINK) PINK_SAMPLES else LOOP_SAMPLES
        val file = File.createTempFile("focus-$name-", ".ogg", appContext.cacheDir)
        try {
            assets.open("focus/$name.ogg").use { input -> file.outputStream().use { input.copyTo(it) } }
            val extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)
            val format = extractor.getTrackFormat(0)
            extractor.selectTrack(0)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Focus audio has no mime type")
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                var delay = if (format.containsKey(MediaFormat.KEY_ENCODER_DELAY)) format.getInteger(MediaFormat.KEY_ENCODER_DELAY) else 0
                var padding = if (format.containsKey(MediaFormat.KEY_ENCODER_PADDING)) format.getInteger(MediaFormat.KEY_ENCODER_PADDING) else 0
                val storage = ShortArray(expected + delay + padding + 65_536)
                var total = 0
                var noted = delay
                var windowSent = false
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                // A full rain loop decodes in about 2 s here. Playback has already started.
                val deadline = SystemClock.elapsedRealtime() + 15_000
                while (!outputDone && SystemClock.elapsedRealtime() < deadline && total < storage.size) {
                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val input = codec.getInputBuffer(inIndex) ?: error("Missing decoder input")
                            val count = extractor.readSampleData(input, 0)
                            if (count < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, count, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val outIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFormat = codec.outputFormat
                            val sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            check(sampleRate == FocusLoopRenderer.SAMPLE_RATE) { "Focus audio sample rate is $sampleRate" }
                            if (outFormat.containsKey(MediaFormat.KEY_ENCODER_DELAY)) {
                                delay = outFormat.getInteger(MediaFormat.KEY_ENCODER_DELAY)
                            }
                            if (outFormat.containsKey(MediaFormat.KEY_ENCODER_PADDING)) {
                                padding = outFormat.getInteger(MediaFormat.KEY_ENCODER_PADDING)
                            }
                            if (!windowSent) noted = delay
                        }
                        else -> if (outIndex >= 0) {
                            val output = codec.getOutputBuffer(outIndex)
                            if (output != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                output.position(info.offset)
                                output.limit(info.offset + info.size)
                                val shorts = output.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                val room = storage.size - total
                                val count = minOf(shorts.remaining(), room)
                                shorts.get(storage, total, count)
                                total += count
                                val stride = FocusLoopRenderer.SAMPLE_RATE / 50
                                if (onWindow != null && total - noted >= stride) {
                                    onWindow(storage, delay.coerceAtMost(total), total)
                                    noted = total
                                    windowSent = true
                                }
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                }
                var from = delay.coerceAtMost(total)
                var until = (total - padding).coerceAtLeast(from)
                if (until - from > expected) from = until - expected
                val pcm = storage.copyOfRange(from, until)
                Log.i(
                    "NoisePlayer",
                    "decoded $name samples=${pcm.size} expected=$expected raw=$total delay=$delay padding=$padding done=$outputDone",
                )
                check(pcm.isNotEmpty()) { "Empty focus audio asset" }
                synchronized(pcmCache) { pcmCache[sound] = pcm }
                return DecodedPcm(pcm, from)
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
                runCatching { extractor.release() }
            }
        } finally {
            file.delete()
        }
    }

    private class DecodedPcm(val pcm: ShortArray, val playedFrom: Int)

    private companion object {
        const val LOOP_SAMPLES = 970_200
        const val PINK_SAMPLES = 573_300
    }
}

/** Buffer-independent loop playback. Loading happens on the audio worker, after the old sound fades. */
internal class FocusLoopRenderer(private val load: (FocusSound) -> ShortArray) {
    private var current = FocusSound.OFF
    private var gain = 0f
    private var samples = shortArrayOf()
    private var position = 0
    private var limit = 0
    private var looping = true

    val isSilent: Boolean get() = current == FocusSound.OFF && gain == 0f

    fun cursor(): Int = position

    /** Samples still waiting in the current prefix. A finished loop always has a buffer. */
    fun buffered(): Int = if (looping) Int.MAX_VALUE else (limit - position).coerceAtLeast(0)

    /** Loads a cached sound. The first play uses [follow] so it does not wait for the whole file. */
    fun prepare(requested: FocusSound) {
        if (requested == FocusSound.OFF) return
        current = requested
        samples = load(requested)
        position = 0
        limit = samples.size
        gain = 0f
        looping = true
    }

    /** Point at the in-progress decode buffer. [from] is the first real sample. */
    fun follow(requested: FocusSound, pcm: ShortArray, from: Int, valid: Int) {
        if (current != requested || samples !== pcm) {
            current = requested
            samples = pcm
            position = from.coerceAtMost(valid)
            gain = 0f
            looping = false
        }
        limit = valid
    }

    /** Switch to the trimmed loop without replaying the samples already written. */
    fun adoptLoop(pcm: ShortArray, at: Int) {
        samples = pcm
        limit = pcm.size
        position = if (pcm.isEmpty()) 0 else at.coerceIn(0, pcm.lastIndex)
        looping = true
    }

    fun render(requested: FocusSound, output: ShortArray) {
        for (i in output.indices) {
            if (current != requested && gain == 0f) {
                current = requested
                samples = if (current == FocusSound.OFF) shortArrayOf() else load(current).also {
                    require(it.isNotEmpty()) { "Empty focus audio asset" }
                }
                position = 0
                limit = samples.size
                looping = true
            }
            val span = if (looping) samples.size else limit
            // Apply the old gain first so every start and sound change includes a zero sample.
            output[i] = if (current == FocusSound.OFF || span <= 0 || position >= span) 0 else (samples[position] * gain).toInt().toShort()
            if (span > 0 && position < span) {
                position += 1
                if (looping && position >= span) position = 0
            }
            gain = if (current == requested && current != FocusSound.OFF) {
                minOf(1f, gain + 1f / (SAMPLE_RATE * 0.25f))
            } else maxOf(0f, gain - 1f / (SAMPLE_RATE * 0.18f))
        }
    }

    companion object {
        const val SAMPLE_RATE = 22_050
    }
}
