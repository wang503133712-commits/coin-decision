package cn.coindecision.app

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.coindecision.app.ai.OfflineDecisionAiService
import cn.coindecision.app.data.*
import cn.coindecision.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun failedTransactionRetriesTheSameSample() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, CoinDatabase::class.java).build()
        try {
            val samples = AtomicInteger()
            val repo = CoinRepository(db, CoinTossEngine { samples.incrementAndGet(); .2 })
            val s = ready(); repo.save(s)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_result BEFORE INSERT ON draft WHEN (SELECT COUNT(*) FROM history)>0 BEGIN SELECT RAISE(ABORT, 'test-save-failure'); END")
            try { repo.toss(s); fail("事务应该失败") } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(1, samples.get())
            assertTrue(repo.history.first().isEmpty())
            assertEquals(s, repo.load())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_result")
            val result = repo.toss(s)
            assertEquals(1, samples.get())
            assertEquals(Face.A, result.face)
            assertEquals(result, repo.find(result.tossId))
        } finally { db.close() }
    }
    @Test fun soundPreferencePersistsWithoutChangingOtherSettings() = runBlocking {
        val store = SettingsStore(context)
        val original = store.flow.first()
        try {
            val changed = original.copy(sound = !original.sound)
            store.save(changed)
            assertEquals(changed, SettingsStore(context).flow.first())
        } finally { store.save(original) }
    }
    @Test fun updateRepositoryPersistsIndependentlyOfApiSettings() = runBlocking {
        val store = SettingsStore(context)
        val original = store.flow.first()
        try {
            store.saveGithubRepository("https://github.com/test/coin")
            assertEquals(original.copy(githubRepository = "test/coin"), store.flow.first())
            store.save(original.copy(sound = !original.sound, githubRepository = ""))
            assertEquals("test/coin", SettingsStore(context).flow.first().githubRepository)
            try { store.saveGithubRepository("https://example.com/test/coin"); fail() } catch (_: IllegalArgumentException) { }
            assertEquals("test/coin", store.flow.first().githubRepository)
        } finally { store.save(original); store.saveGithubRepository(original.githubRepository) }
    }
    private suspend fun ready(): Session {
        val request = DecisionRequest(matter = "测试周末", optionA = "去露营", optionB = "不去露营")
        val questions = OfflineDecisionAiService().generate(request).questions
        return Session(matter = request.matter, optionA = request.optionA, optionB = request.optionB,
            questions = questions, answers = questions.map { Answer(AnswerKind.SELECTED, it.options.first().id) }, stage = Stage.READY, revision = 1)
    }
    @Test fun concurrentClicksRestoreAndExplicitRetoss() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, CoinDatabase::class.java).build()
        try {
            val samples = AtomicInteger()
            val repo = CoinRepository(db, CoinTossEngine { samples.incrementAndGet(); .2 })
            val s = ready(); repo.save(s)
            val results = coroutineScope { (1..12).map { async(Dispatchers.Default) { repo.toss(s) } }.awaitAll() }
            assertEquals(1, samples.get()); assertEquals(1, results.map { it.tossId }.toSet().size)
            assertEquals(1, repo.history.first().size)
            val restored = CoinRepository(db, CoinTossEngine { fail("Restore sampled again"); .9 })
            val persisted = restored.load()
            assertEquals(Stage.RESULT, persisted.stage)
            assertEquals(results.first(), restored.find(persisted.activeTossId!!))
            assertEquals(results.first(), restored.toss(persisted))
            val again = persisted.copy(stage = Stage.READY, activeTossId = null, revision = persisted.revision + 1)
            // 在草稿写入前明确再投也不能误返回第一枚结果。
            val second = repo.toss(again)
            assertEquals(2, samples.get()); assertEquals(2, second.number); assertNotEquals(results.first().tossId, second.tossId)
            assertEquals(2, repo.history.first().size)
            repo.delete(second.tossId)
            assertEquals(1, repo.history.first().size)
            assertNull(repo.load().activeTossId)
            try { repo.toss(s); fail("旧事项不能在删除后重新投币") } catch (_: IllegalArgumentException) { }
            repo.clear(); assertTrue(repo.history.first().isEmpty())
        } finally { db.close() }
    }
    @Test fun fileDatabaseSurvivesReopeningWithoutResampling() = runBlocking {
        val name = "coin-persistence-instrumentation.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, CoinDatabase::class.java, name).build()
        val snapshot = CoinRepository(db, CoinTossEngine { .8 }).let { repo -> val s = ready(); repo.save(s); repo.toss(s) }
        db.close()
        db = Room.databaseBuilder(context, CoinDatabase::class.java, name).build()
        try {
            val repo = CoinRepository(db, CoinTossEngine { fail("Reopen must not sample"); .2 })
            assertEquals(snapshot, repo.find(repo.load().activeTossId!!))
            assertEquals(snapshot, repo.toss(repo.load()))
            assertEquals(snapshot.probability, repo.find(snapshot.tossId)!!.probability)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun keyIsEncryptedBoundToTargetAndCanBeDeleted() {
        val vault = KeyVault(context)
        val fake = "instrumentation-fake-not-a-real-key"
        vault.delete()
        try {
            vault.save("https://api.deepseek.com", fake)
            assertEquals(fake, vault.read("https://api.deepseek.com"))
            assertFalse(vault.hasKey("https://different.example"))
            assertEquals("", vault.read("https://different.example"))
            val prefs = context.getSharedPreferences("private_key_ciphertext", Context.MODE_PRIVATE)
            assertFalse(prefs.all.values.any { it.toString().contains(fake) })
            prefs.edit().putString("ciphertext", "corrupt").commit()
            assertThrows(IllegalStateException::class.java) { vault.read("https://api.deepseek.com") }
            assertFalse(vault.hasKey("https://api.deepseek.com"))
        } finally { vault.delete() }
    }
}
