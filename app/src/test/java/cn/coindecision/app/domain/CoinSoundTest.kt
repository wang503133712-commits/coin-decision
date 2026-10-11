package cn.coindecision.app.domain

import cn.coindecision.app.ui.CoinSound
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CoinSoundTest {
    @Test fun cuesAreShortQuietAndFadeOut() {
        for (samples in listOf(CoinSound.launch(), CoinSound.landing())) {
            assertTrue(samples.size in 3000..6000)
            assertTrue(samples.maxOf { abs(it.toInt()) } in 500..5000)
            assertEquals(0, samples.first().toInt())
            assertTrue(samples.takeLast(100).maxOf { abs(it.toInt()) } < 150)
        }
    }
}
