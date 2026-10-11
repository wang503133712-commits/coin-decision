package cn.coindecision.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.coindecision.app.ai.OfflineDecisionAiService
import cn.coindecision.app.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpdateDataTest {
    @Test fun seedUpdateSnapshot() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("updatePhase") == "seed")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CoinApplication
        val prior = app.repository.load()
        val r = DecisionRequest(matter = "更新保留测试：周末露营", optionA = "去露营", optionB = "不去露营")
        val q = OfflineDecisionAiService().generate(r).questions
        val session = Session(matter = r.matter, optionA = r.optionA, optionB = r.optionB, stage = Stage.READY,
            questions = q, answers = List(5) { Answer(AnswerKind.SKIPPED) }, revision = prior.revision + 1)
        app.repository.save(session)
        val snapshot = app.repository.toss(session)
        app.vault.save("https://api.deepseek.com", "upgrade-fake-not-a-real-key")
        app.settings.save(app.settings.flow.first().copy(config = AiConfig(model = "upgrade-custom-model")))
        File(app.filesDir, "update-expected.json").writeText(codec.encodeToString(snapshot))
        assertEquals(Stage.RESULT, app.repository.load().stage)
    }
    @Test fun assertUpdateSnapshotPreserved() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("updatePhase") == "assert")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CoinApplication
        val expected = codec.decodeFromString<TossSnapshot>(File(app.filesDir, "update-expected.json").readText())
        assertEquals(expected, app.repository.find(expected.tossId))
        assertEquals(expected.tossId, app.repository.load().activeTossId)
        assertEquals(Stage.RESULT, app.repository.load().stage)
        assertEquals("upgrade-fake-not-a-real-key", app.vault.read("https://api.deepseek.com"))
        assertEquals(AiConfig(model = "upgrade-custom-model"), app.settings.flow.first().config)
        app.vault.delete()
    }
}
