package cn.coindecision.app

import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.coindecision.app.ui.CoinAudio
import cn.coindecision.app.ui.CoinSound
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoinAudioTest {
    @Test fun coinEffectsUseMediaVolumeAndActuallyPlay() {
        val audio = CoinAudio()
        try {
            for (samples in listOf(CoinSound.launch(), CoinSound.landing())) {
                audio.play(samples)
                val field = CoinAudio::class.java.getDeclaredField("track").apply { isAccessible = true }
                val track = field.get(audio) as? AudioTrack
                assertNotNull("投币必须创建可播放的音轨", track)
                assertEquals("投币音效应使用媒体音量，而不是系统提示音", AudioManager.STREAM_MUSIC, track!!.audioAttributes.volumeControlStream)
                assertEquals(AudioTrack.PLAYSTATE_PLAYING, track.playState)
                val deadline = SystemClock.uptimeMillis() + 1000
                while (track.playbackHeadPosition == 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(10)
                assertTrue("实际播放位置必须前进", track.playbackHeadPosition > 0)
            }
        } finally { audio.release() }
    }
}
