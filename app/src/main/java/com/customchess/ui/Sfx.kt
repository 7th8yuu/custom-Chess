package com.customchess.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

enum class Snd { MOVE, CAPTURE, CHECK, CASTLE, PROMOTE, WIN, LOSE, DRAW, OK, BAD, TAP }

/**
 * Âm thanh tổng hợp bằng code (không cần file âm thanh): tiếng quân chạm bàn, ăn quân, chiếu, thắng, thua...
 * Mỗi âm là một đoạn PCM ngắn nạp sẵn vào AudioTrack tĩnh.
 */
object Sfx {
    private const val RATE = 22050
    @Volatile var enabled = true
    @Volatile var volume = 0.7f
    private val tracks = HashMap<Snd, AudioTrack?>()

    fun play(s: Snd) {
        if (!enabled || volume <= 0f) return
        try {
            val t = tracks.getOrPut(s) { build(s) } ?: return
            t.setVolume(volume)
            if (t.playState != AudioTrack.PLAYSTATE_STOPPED) t.stop()
            t.reloadStaticData()
            t.play()
        } catch (_: Throwable) {
            tracks[s] = null
        }
    }

    private fun build(s: Snd): AudioTrack? {
        val pcm = synth(s)
        val bytes = pcm.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    // --------------------------------------------------------------------------------- tổng hợp

    private fun note(buf: FloatArray, startMs: Int, ms: Int, hz: Double, amp: Float, decay: Double, square: Boolean = false) {
        val start = startMs * RATE / 1000
        val n = ms * RATE / 1000
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= buf.size) break
            val t = i.toDouble() / RATE
            var v = sin(2.0 * PI * hz * t)
            if (square) v = sign(v) * 0.5
            val env = exp(-t * decay) * minOf(1.0, i / 60.0) // vào nhanh, tắt dần
            buf[idx] += (v * env * amp).toFloat()
        }
    }

    private fun thud(buf: FloatArray, startMs: Int, ms: Int, hz: Double, noiseAmp: Float, amp: Float, decay: Double) {
        val rnd = Random(startMs * 31 + ms)
        val start = startMs * RATE / 1000
        val n = ms * RATE / 1000
        var lp = 0f
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= buf.size) break
            val t = i.toDouble() / RATE
            val noise = rnd.nextFloat() * 2f - 1f
            lp += (noise - lp) * 0.35f // lọc thấp cho tiếng "cộp" trầm
            val env = exp(-t * decay)
            buf[idx] += (lp * noiseAmp * env + sin(2.0 * PI * hz * t) * amp * env).toFloat()
        }
    }

    private fun synth(s: Snd): ShortArray {
        val total = when (s) {
            Snd.MOVE -> 90; Snd.CAPTURE -> 130; Snd.CHECK -> 220; Snd.CASTLE -> 200; Snd.PROMOTE -> 330
            Snd.WIN -> 620; Snd.LOSE -> 620; Snd.DRAW -> 420; Snd.OK -> 260; Snd.BAD -> 300; Snd.TAP -> 30
        }
        val buf = FloatArray(total * RATE / 1000)
        when (s) {
            Snd.MOVE -> thud(buf, 0, 80, 190.0, 0.9f, 0.5f, 55.0)
            Snd.CAPTURE -> { thud(buf, 0, 120, 140.0, 1.2f, 0.6f, 38.0); thud(buf, 0, 40, 420.0, 0.5f, 0.0f, 90.0) }
            Snd.CHECK -> { thud(buf, 0, 60, 190.0, 0.8f, 0.4f, 55.0); note(buf, 40, 90, 880.0, 0.35f, 22.0); note(buf, 120, 100, 1175.0, 0.35f, 20.0) }
            Snd.CASTLE -> { thud(buf, 0, 70, 200.0, 0.9f, 0.5f, 55.0); thud(buf, 100, 80, 170.0, 0.9f, 0.5f, 55.0) }
            Snd.PROMOTE -> { note(buf, 0, 90, 660.0, 0.35f, 16.0); note(buf, 80, 90, 880.0, 0.35f, 16.0); note(buf, 160, 160, 1320.0, 0.35f, 12.0) }
            Snd.WIN -> listOf(523.25, 659.25, 783.99, 1046.5).forEachIndexed { i, hz -> note(buf, i * 120, 260, hz, 0.32f, 8.0) }
            Snd.LOSE -> listOf(392.0, 329.63, 261.63).forEachIndexed { i, hz -> note(buf, i * 170, 300, hz, 0.34f, 7.0) }
            Snd.DRAW -> { note(buf, 0, 200, 440.0, 0.3f, 9.0); note(buf, 190, 220, 440.0, 0.3f, 9.0) }
            Snd.OK -> { note(buf, 0, 110, 660.0, 0.34f, 14.0); note(buf, 100, 150, 990.0, 0.34f, 12.0) }
            Snd.BAD -> { note(buf, 0, 140, 170.0, 0.3f, 12.0, square = true); note(buf, 130, 170, 130.0, 0.3f, 10.0, square = true) }
            Snd.TAP -> thud(buf, 0, 25, 300.0, 0.5f, 0.2f, 140.0)
        }
        return ShortArray(buf.size) { (buf[it].coerceIn(-1f, 1f) * 30000f).toInt().toShort() }
    }
}
