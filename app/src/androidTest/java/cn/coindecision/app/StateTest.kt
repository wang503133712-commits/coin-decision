package cn.coindecision.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.coindecision.app.ui.CoinViewModel
import cn.coindecision.app.ai.DecisionAiService
import cn.coindecision.app.ai.OfflineDecisionAiService
import androidx.lifecycle.ViewModelStore
import cn.coindecision.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StateTest {
    @Test fun aiExtractionRequiresConfirmationBeforeQuestionGeneration() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CoinApplication
        app.repository.clear()
        var generated = false
        val fake = object : DecisionAiService {
            override suspend fun extractOptions(matter: String): DecisionOptions {
                assertEquals("想聚餐又想在家休息", matter)
                return DecisionOptions("参加聚餐", "在家休息")
            }
            override suspend fun generate(request: DecisionRequest): AiResponse {
                generated = true
                return OfflineDecisionAiService().generate(request)
            }
        }
        val vm = withContext(Dispatchers.Main) { CoinViewModel(app, fake) }
        val store = ViewModelStore().apply { put("test", vm) }
        try {
            withTimeout(10000) { vm.ui.first { !it.loading } }
            withContext(Dispatchers.Main) { vm.edit("想聚餐又想在家休息", "", ""); vm.extractOptions() }
            val s = withTimeout(10000) { vm.ui.first { it.session.stage == Stage.CONFIRM && !it.extracting }.session }
            assertEquals("参加聚餐", s.optionA); assertEquals("在家休息", s.optionB)
            assertTrue(s.questions.isEmpty()); assertFalse(generated)
            withContext(Dispatchers.Main) { vm.editMatter(s.matter) }
            assertEquals(Stage.CONFIRM, vm.ui.value.session.stage)
            assertEquals(s.optionA, vm.ui.value.session.optionA)
            withContext(Dispatchers.Main) { vm.back(); vm.editMatter("新的复杂事项") }
            assertTrue(vm.ui.value.session.optionA.isBlank()); assertTrue(vm.ui.value.session.optionB.isBlank())
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }
    @Test fun cancelledAiExtractionCannotOverwriteNewMatterOrChoices() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CoinApplication
        app.repository.clear()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fake = object : DecisionAiService {
            override suspend fun extractOptions(matter: String): DecisionOptions {
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
                return DecisionOptions("参加聚餐", "在家休息")
            }
            override suspend fun generate(request: DecisionRequest) = OfflineDecisionAiService().generate(request)
        }
        val vm = withContext(Dispatchers.Main) { CoinViewModel(app, fake) }
        val store = ViewModelStore().apply { put("test", vm) }
        try {
            withTimeout(10000) { vm.ui.first { !it.loading } }
            withContext(Dispatchers.Main) { vm.edit("复杂问题", "", ""); vm.extractOptions() }
            withTimeout(10000) { entered.await() }
            withContext(Dispatchers.Main) { vm.edit("新问题", "喝茶", "喝咖啡") }
            release.complete(Unit); delay(200)
            assertEquals("新问题", vm.ui.value.session.matter)
            assertEquals("喝茶", vm.ui.value.session.optionA)
            assertEquals(Stage.EDIT, vm.ui.value.session.stage)
            assertFalse(vm.ui.value.extracting)
        } finally { release.complete(Unit); withContext(Dispatchers.Main) { store.clear() } }
    }
    @Test fun staleCancelledRequestCannotOverwriteEditedMatter() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CoinApplication
        app.repository.clear()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fake = object : DecisionAiService {
            override suspend fun generate(request: DecisionRequest): AiResponse {
                entered.complete(Unit)
                // 故意模拟不遵守取消的服务，旧响应仍不能覆盖新事项。
                withContext(NonCancellable) { release.await() }
                return OfflineDecisionAiService().generate(request)
            }
        }
        val vm = withContext(Dispatchers.Main) { CoinViewModel(app, fake) }
        val store = ViewModelStore().apply { put("test", vm) }
        try {
            withTimeout(10000) { vm.ui.first { !it.loading } }
            withContext(Dispatchers.Main) { vm.edit("原事项", "去聚餐", "不去聚餐"); vm.confirm(); vm.generate() }
            withTimeout(10000) { entered.await() }
            withContext(Dispatchers.Main) { vm.edit("新事项", "去散步", "不去散步") }
            release.complete(Unit)
            delay(200)
            assertEquals("新事项", vm.ui.value.session.matter)
            assertEquals(Stage.EDIT, vm.ui.value.session.stage)
            assertTrue(vm.ui.value.session.questions.isEmpty())
            withContext(Dispatchers.Main) { vm.newDecision() }
            withTimeout(10000) { while (app.repository.load().revision < vm.ui.value.session.revision) delay(50) }
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }
    @Test fun draftAnswersAndEditInvalidateQuestionGroup() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CoinApplication
        app.repository.clear()
        val vm = withContext(Dispatchers.Main) { CoinViewModel(app) }
        withTimeout(10000) { vm.ui.first { !it.loading } }
        withContext(Dispatchers.Main) {
            vm.edit("我要不要不去聚餐", "不去聚餐", "去聚餐")
            vm.confirm(); vm.generate(offline = true)
        }
        val questions = withTimeout(10000) { vm.ui.first { it.session.stage == Stage.ANSWER }.session.questions }
        assertTrue(questions.first().options.first().label.contains("A：不去聚餐"))
        withContext(Dispatchers.Main) { vm.answer(questions.first().options.first().id); vm.next(); vm.previous() }
        assertEquals(questions.first().options.first().id, vm.ui.value.session.answers.first().optionId)
        withContext(Dispatchers.Main) { vm.answer(questions.first().options.last().id); vm.next(); vm.previous() }
        assertEquals(questions.first().options.last().id, vm.ui.value.session.answers.first().optionId)
        withContext(Dispatchers.Main) { vm.edit("a".repeat(500), "在家休息", "出去走走") }
        assertEquals(500, vm.ui.value.session.matter.length)
        assertTrue(vm.ui.value.session.questions.isEmpty()); assertTrue(vm.ui.value.session.answers.isEmpty())
        assertNull(vm.ui.value.session.activeTossId)
        withContext(Dispatchers.Main) { vm.newDecision() }
        // 等候本测试最后一份草稿实际落盘，避免下个 UI 测试被旧写入覆盖。
        withTimeout(10000) { while (app.repository.load().revision < vm.ui.value.session.revision) delay(50) }
    }
}
