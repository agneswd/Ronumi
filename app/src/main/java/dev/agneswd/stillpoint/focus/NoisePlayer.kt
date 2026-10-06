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
        try {
            val initial = synchronized(this) { sound }
            if (initial != FocusSound.OFF) generator.prepare(initial)
            val playback = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(FocusLoopRenderer.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(buffer.size * 4, AudioTrack.getMinBufferSize(
                    FocusLoopRenderer.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
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
                    if (!loggedFirstFrame) {
                        loggedFirstFrame = true
                        Log.i("NoisePlayer", "first frame ${SystemClock.elapsedRealtime() - requestedAt}ms")
                    }
                }
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
            pcmCache[sound] = decoded
            return decoded
        }
    }

    private fun decodeOgg(sound: FocusSound): ShortArray {
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
                val chunks = ArrayList<ShortArray>()
                var total = 0
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                val deadline = SystemClock.elapsedRealtime() + 2_000
                while (!outputDone && SystemClock.elapsedRealtime() < deadline) {
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
                            val sampleRate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            check(sampleRate == FocusLoopRenderer.SAMPLE_RATE) { "Focus audio sample rate is $sampleRate" }
                        }
                        else -> if (outIndex >= 0) {
                            val output = codec.getOutputBuffer(outIndex)
                            if (output != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                output.position(info.offset)
                                output.limit(info.offset + info.size)
                                val pcm = ShortArray(info.size / 2)
                                output.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
                                chunks.add(pcm)
                                total += pcm.size
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                }
                check(outputDone) { "Focus audio decode timed out" }
                val raw = ShortArray(total)
                var at = 0
                for (chunk in chunks) {
                    chunk.copyInto(raw, at)
                    at += chunk.size
                }
                val delay = if (format.containsKey(MediaFormat.KEY_ENCODER_DELAY)) format.getInteger(MediaFormat.KEY_ENCODER_DELAY) else 0
                val padding = if (format.containsKey(MediaFormat.KEY_ENCODER_PADDING)) format.getInteger(MediaFormat.KEY_ENCODER_PADDING) else 0
                var pcm = raw
                if (delay > 0 && pcm.size > delay) pcm = pcm.copyOfRange(delay, pcm.size)
                if (padding > 0 && pcm.size > padding) pcm = pcm.copyOfRange(0, pcm.size - padding)
                if (pcm.size > expected) pcm = pcm.copyOfRange(pcm.size - expected, pcm.size)
                Log.i("NoisePlayer", "decoded $name samples=${pcm.size} expected=$expected raw=${raw.size} delay=$delay padding=$padding")
                check(pcm.isNotEmpty()) { "Empty focus audio asset" }
                return pcm
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
                runCatching { extractor.release() }
            }
        } finally {
            file.delete()
        }
    }

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

    val isSilent: Boolean get() = current == FocusSound.OFF && gain == 0f

    /** Loads the first sound before [AudioTrack.play], so playback starts on decoded PCM. */
    fun prepare(requested: FocusSound) {
        if (requested == FocusSound.OFF) return
        current = requested
        samples = load(requested)
        position = 0
        gain = 0f
    }

    fun render(requested: FocusSound, output: ShortArray) {
        for (i in output.indices) {
            if (current != requested && gain == 0f) {
                current = requested
                samples = if (current == FocusSound.OFF) shortArrayOf() else load(current).also {
                    require(it.isNotEmpty()) { "Empty focus audio asset" }
                }
                position = 0
            }
            // Apply the old gain first so every start and sound change includes a zero sample.
            output[i] = if (current == FocusSound.OFF) 0 else (samples[position] * gain).toInt().toShort()
            if (samples.isNotEmpty()) position = (position + 1) % samples.size
            gain = if (current == requested && current != FocusSound.OFF) {
                minOf(1f, gain + 1f / (SAMPLE_RATE * 0.25f))
            } else maxOf(0f, gain - 1f / (SAMPLE_RATE * 0.18f))
        }
    }

    companion object {
        const val SAMPLE_RATE = 22_050
    }
}
