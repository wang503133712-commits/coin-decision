package cn.coindecision.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** 原创短音色，只用本地 PCM；媒体音量与静音由系统控制。 */
object CoinSound {
    const val rate = 22050
    fun launch(): ShortArray = ShortArray((rate * .17).toInt()) { i ->
        val t = i.toDouble() / rate
        val fade = sin(PI * t / .17).coerceAtLeast(0.0)
        val envelope = fade * fade
        val value = sin(2 * PI * (450 * t + 1800 * t * t)) * .045 + sin(2 * PI * 1900 * t) * .018
        (value * envelope * Short.MAX_VALUE).toInt().toShort()
    }
    fun landing(): ShortArray = ShortArray((rate * .22).toInt()) { i ->
        val t = i.toDouble() / rate
        val attack = (t / .003).coerceAtMost(1.0)
        val value = (sin(2 * PI * 2100 * t) * .07 + sin(2 * PI * 3250 * t) * .025) * exp(-t * 25) +
            sin(2 * PI * 210 * t) * .035 * exp(-t * 50)
        (value * attack * Short.MAX_VALUE).toInt().toShort()
    }
}
class CoinAudio {
    private var track: AudioTrack? = null
    fun play(samples: ShortArray) {
        release()
        try {
            val player = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(CoinSound.rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
            track = player
            check(player.write(samples, 0, samples.size) == samples.size)
            check(player.state == AudioTrack.STATE_INITIALIZED)
            player.play()
        } catch (_: IllegalArgumentException) { release() }
        catch (_: IllegalStateException) { release() }
    }
    fun release() { track?.release(); track = null }
}
