package com.isladinamica.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Sonido de la isla al llegar un mensaje.
 * Por defecto es un tintineo de tres notas generado por la propia app (sonido original).
 * Si la persona eligió un archivo de audio, se usa ese.
 */
object NotifSound {

    private const val SAMPLE_RATE = 44100

    private val chime: ShortArray by lazy { buildChime() }

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Respeta el modo silencio, el modo vibrar y "No molestar". */
    fun onMessage(ctx: Context, doNotDisturb: Boolean) {
        if (!Prefs.soundOn(ctx) || doNotDisturb) return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> play(ctx)
            AudioManager.RINGER_MODE_VIBRATE -> vibrate(ctx, 180L)
            else -> Unit
        }
    }

    fun play(ctx: Context) {
        val custom = Prefs.customSound(ctx)
        if (custom != null) {
            try {
                playFile(ctx, Uri.parse(custom))
                return
            } catch (_: Exception) {
            }
        }
        try {
            playChime()
        } catch (_: Exception) {
        }
    }

    private fun playFile(ctx: Context, uri: Uri) {
        val mp = MediaPlayer()
        mp.setAudioAttributes(attrs)
        mp.setDataSource(ctx, uri)
        mp.setOnPreparedListener { it.start() }
        mp.setOnCompletionListener { it.release() }
        mp.setOnErrorListener { m, _, _ ->
            m.release()
            true
        }
        mp.prepareAsync()
    }

    private fun playChime() {
        val pcm = chime
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        track.setNotificationMarkerPosition(pcm.size - 1)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) {
                t.release()
            }

            override fun onPeriodicNotification(t: AudioTrack) {}
        })
        track.play()
    }

    private fun vibrate(ctx: Context, ms: Long) {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Tres notas suaves (mi, sol, si) con ataque rápido y caída natural, como una campanita. */
    private fun buildChime(): ShortArray {
        val total = (SAMPLE_RATE * 1.0).toInt()
        val data = DoubleArray(total)

        fun note(freq: Double, start: Double, dur: Double, amp: Double) {
            val s0 = (start * SAMPLE_RATE).toInt()
            val n = (dur * SAMPLE_RATE).toInt()
            for (i in 0 until n) {
                val idx = s0 + i
                if (idx >= total) break
                val t = i.toDouble() / SAMPLE_RATE
                val attack = min(1.0, i / (0.004 * SAMPLE_RATE))
                val env = exp(-t * 6.5) * attack
                val tone = sin(2.0 * PI * freq * t) + 0.3 * sin(2.0 * PI * freq * 2.0 * t) * exp(-t * 9.0)
                data[idx] += amp * env * tone
            }
        }

        note(1318.51, 0.00, 0.70, 0.40)
        note(1567.98, 0.13, 0.65, 0.40)
        note(1975.53, 0.27, 0.70, 0.36)

        return ShortArray(total) { i ->
            (data[i].coerceIn(-1.0, 1.0) * 32767.0 * 0.8).toInt().toShort()
        }
    }
}
