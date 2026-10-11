package cn.coindecision.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.content.Context
import cn.coindecision.app.R
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 本地实录硬币 PCM；来源和 CC0 许可见 AUDIO_LICENSES.md。 */
object CoinSound {
    const val rate = 22050
    fun launch(context: Context) = read(context, R.raw.coin_launch)
    fun landing(context: Context) = read(context, R.raw.coin_landing)
    private fun read(context: Context, resource: Int) = context.resources.openRawResource(resource).use { decode(it.readBytes()) }
    fun decode(bytes: ByteArray): ShortArray {
        require(bytes.isNotEmpty() && bytes.size % 2 == 0) { "无效 PCM 音效" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(buffer.remaining()).also { buffer.get(it) }
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
