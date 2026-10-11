package cn.coindecision.app.domain

import cn.coindecision.app.ui.CoinSound
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import java.io.File

class CoinSoundTest {
    @Test fun cuesAreShortQuietAndFadeOut() {
        val raw = File("src/main/res/raw").takeIf { it.exists() } ?: File("app/src/main/res/raw")
        for (name in listOf("coin_launch", "coin_landing")) {
            val samples = CoinSound.decode(File(raw, "$name.pcm").readBytes())
            assertTrue(samples.size in 3000..22050)
            assertTrue(samples.maxOf { abs(it.toInt()) } in 500..7000)
            assertEquals(0, samples.first().toInt())
            assertTrue(samples.takeLast(100).maxOf { abs(it.toInt()) } < 150)
        }
    }
    @Test fun pcmRequiresWholeLittleEndianSamples() {
        assertArrayEquals(shortArrayOf(1, -2), CoinSound.decode(byteArrayOf(1, 0, -2, -1)))
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1))) {
            try { CoinSound.decode(bytes); fail("Invalid PCM accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
