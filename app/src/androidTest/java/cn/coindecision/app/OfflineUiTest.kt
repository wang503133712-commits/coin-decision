package cn.coindecision.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineUiTest {
    val rule = createAndroidComposeRule<MainActivity>()
    @get:Rule val isolatedFixture: TestRule = RuleChain.outerRule(object : ExternalResource() {
        override fun before() { runBlocking {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CoinApplication
            app.repository.clear()
            app.settings.save(app.settings.flow.first().copy(online = false, config = cn.coindecision.app.domain.AiConfig()))
        } }
    }).around(rule)
    @Test fun updateSettingsRejectInvalidRepositoryAndKeepLocalEntry() {
        rule.waitUntil(10000) { rule.onAllNodesWithText("试试示例：周末露营").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("设置").performClick()
        val repo = rule.onNode(hasSetTextAction() and hasText("GitHub 更新仓库"))
        repo.performScrollTo().performTextReplacement("https://example.com/test/coin")
        rule.onNodeWithText("保存并检查更新").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("请使用公开 GitHub 仓库地址").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("选择本地 APK 更新包").performScrollTo().assertIsEnabled()
    }
    @Test fun longNegatedInputKeepsConfirmedDirections() {
        rule.waitUntil(10000) { rule.onAllNodesWithText("你在犹豫什么？").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("手动编辑两种选择").performScrollTo().performClick()
        val fields = rule.onAllNodes(hasSetTextAction())
        val matter = "我要不要不去聚餐？" + "长文本说明。".repeat(80)
        fields[0].performTextReplacement(matter.take(500))
        fields[1].performScrollTo().performTextReplacement("不去聚餐")
        fields[2].performScrollTo().performTextReplacement("去聚餐")
        rule.onNodeWithText("确认两面含义").performScrollTo().performClick()
        rule.onNodeWithText("不去聚餐").assertExists()
        rule.onNodeWithText("我确认 A/B 是不同、互斥的安排，文字准确表达我的意思。").performScrollTo().performClick()
        rule.onNodeWithText("开始了解自己").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("1 / 5").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("明显更倾向 A：不去聚餐").assertExists()
        rule.onNodeWithText("明显更倾向 B：去聚餐").assertExists()
        rule.onNodeWithText("返回").performClick()
        rule.onNodeWithText("返回编辑").performScrollTo().performClick()
        fields[0].performScrollTo().performTextReplacement("")
        fields[1].performScrollTo().performTextReplacement("")
        fields[2].performScrollTo().performTextReplacement("")
    }
    @Test fun questionAloneExtractsOptionsAndEditsDoNotKeepStaleOptions() {
        rule.waitUntil(10000) { rule.onAllNodesWithText("你在犹豫什么？").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        rule.onNode(hasSetTextAction()).performTextReplacement("这周末要不要去露营？")
        rule.waitUntil(5000) { rule.onAllNodes(hasSetTextAction() and hasText("这周末要不要去露营？")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("确认两面含义").performScrollTo().performClick()
        rule.onNodeWithText("这周末去露营").assertExists()
        rule.onNodeWithText("这周末不去露营").assertExists()
        rule.onNodeWithText("返回编辑").performScrollTo().performClick()
        rule.onNode(hasSetTextAction() and hasText("你在犹豫什么？")).performScrollTo().performTextReplacement("买房还是投资？")
        rule.waitUntil(5000) { rule.onAllNodes(hasSetTextAction() and hasText("买房还是投资？")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("确认两面含义").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("先确认，你的两种选择").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("买房").assertExists()
        rule.onNodeWithText("投资").assertExists()
        rule.onNodeWithText("这周末去露营").assertDoesNotExist()
    }
    @Test fun aiWithoutKeyOpensSettingsInsteadOfUsingFixedQuestions() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CoinApplication
        app.vault.delete()
        app.settings.save(app.settings.flow.first().copy(online = true))
        rule.waitUntil(10000) { rule.onAllNodesWithText("试试示例：周末露营").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("试试示例：周末露营").performScrollTo().performClick()
        rule.onNodeWithText("确认两面含义").performScrollTo().performClick()
        rule.onNodeWithText("我确认 A/B 是不同、互斥的安排，文字准确表达我的意思。").performScrollTo().performClick()
        rule.onNodeWithText("配置 AI 接口，生成相关问题").performScrollTo().performClick()
        rule.onNodeWithText("当前模型：deepseek-flash（默认，无需填写）").performScrollTo().assertExists()
        rule.onNodeWithText("1 / 5").assertDoesNotExist()
    }
    @Test fun offlineEndToEndAndHistoryDeletion() {
        rule.waitUntil(10000) { rule.onAllNodesWithText("试试示例：周末露营").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("试试示例：周末露营").performScrollTo().performClick()
        rule.onNodeWithText("确认两面含义").performScrollTo().performClick()
        rule.onNodeWithText("我确认 A/B 是不同、互斥的安排，文字准确表达我的意思。").performClick()
        rule.onNodeWithText("开始了解自己").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("1 / 5").fetchSemanticsNodes().isNotEmpty() }
        // 回答、返回、主动跳过，最终全部完成。
        rule.onNodeWithText("明显更倾向 A：这周末去露营").performScrollTo().performClick()
        rule.onNodeWithText("下一题").performScrollTo().performClick()
        rule.onNodeWithText("上一题").performScrollTo().performClick()
        rule.onNodeWithText("下一题").performScrollTo().performClick()
        repeat(4) { i ->
            if (i == 2) rule.onNodeWithText("考虑两种安排的不确定性，你觉得哪种更为稳妥？").assertExists()
            rule.onNodeWithText("跳过这题（按中立）").performScrollTo().performClick()
        }
        rule.onNodeWithContentDescription("待投掷硬币，尚未产生结果").assertExists()
        rule.onNodeWithText("修改回答").performScrollTo().assertHasClickAction()
        rule.onNodeWithText("查看权重计算公式").performScrollTo().performClick()
        rule.onNodeWithText("五题等权，每题占本次答案倾向的 20%。").assertExists()
        rule.onNodeWithText("收起权重计算公式").performScrollTo().performClick()
        rule.onNodeWithText("投出这枚硬币").performScrollTo().performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithText("这一次，硬币落在").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("展开概率与回答说明").performScrollTo().performClick()
        rule.onNodeWithText("s = 0.60 · 正面 53% · 反面 47%").assertExists()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10000) { rule.onAllNodesWithText("这一次，硬币落在").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("返回").performClick()
        rule.onNodeWithText("试试示例：周末露营").assertExists()
        rule.onNodeWithText("历史").performClick()
        rule.onNodeWithText("1 枚硬币 · 查看记录不会重新投币").assertExists()
        rule.onNodeWithText("这周末要不要去露营？").performScrollTo().performClick()
        rule.onNodeWithText("删除此记录").performScrollTo().performClick()
        rule.onNodeWithText("确认").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("0 枚硬币 · 查看记录不会重新投币").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun defaultModelIsPresetAndManualIdIsOptional() {
        rule.waitUntil(10000) { rule.onAllNodesWithText("试试示例：周末露营").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("设置").performClick()
        rule.onNodeWithText("当前模型：deepseek-flash（默认，无需填写）").performScrollTo().assertExists()
        rule.onNodeWithText("模型 ID（接口调用名称）").assertDoesNotExist()
        rule.onNodeWithText("自动读取可用模型").assertExists()
        rule.onNodeWithText("高级：手动填写模型 ID").performScrollTo().performClick()
        rule.onNodeWithText("模型 ID（接口调用名称）").performScrollTo().assertExists()
        rule.onNodeWithText("收起高级设置").performScrollTo().performClick()
        rule.onNodeWithText("模型 ID（接口调用名称）").assertDoesNotExist()
    }
    @Test fun updatedReadyScreenHasNeutralCoinAndCompactEditButton() {
        rule.waitUntil(10000) { rule.onAllNodesWithText("试试示例：周末露营").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("试试示例：周末露营").performScrollTo().performClick()
        rule.onNodeWithText("确认两面含义").performScrollTo().performClick()
        rule.onNodeWithText("我确认 A/B 是不同、互斥的安排，文字准确表达我的意思。").performScrollTo().performClick()
        rule.onNodeWithText("开始了解自己").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("1 / 5").fetchSemanticsNodes().isNotEmpty() }
        repeat(5) { i ->
            if (i == 3) {
                rule.onNodeWithText("考虑两种安排的不确定性，你觉得哪种更为稳妥？").assertExists()
                saveScreenshot("question-four-1.2.png")
            }
            rule.onNodeWithText("跳过这题（按中立）").performScrollTo().performClick()
        }
        rule.onNodeWithContentDescription("待投掷硬币，尚未产生结果").assertExists()
        rule.onNodeWithText("投出这枚硬币").performScrollTo()
        val primary = rule.onNodeWithText("投出这枚硬币").fetchSemanticsNode().boundsInRoot
        val edit = rule.onNodeWithText("修改回答").performScrollTo().fetchSemanticsNode().boundsInRoot
        assertTrue("修改按钮应显著小于投币按钮", edit.width < primary.width * .4f)
        assertTrue("修改按钮应居中", kotlin.math.abs(edit.center.x - primary.center.x) < 2f)
        saveScreenshot("ready-screen-1.2.png")
        rule.onNodeWithText("修改回答").performClick()
        rule.onNodeWithText("5 / 5").assertExists()
        rule.onNodeWithText("准备投币").performScrollTo().performClick()
        rule.onNodeWithContentDescription("待投掷硬币，尚未产生结果").assertExists()
    }
    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, name).outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun blockedFlowNeverShowsToss() {
        rule.waitUntil(10000) { rule.onAllNodesWithText("你在犹豫什么？").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("手动编辑两种选择").performScrollTo().performClick()
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("要不要停药")
        fields[1].performTextReplacement("停药")
        fields[2].performTextReplacement("继续原方案")
        rule.onNodeWithText("确认两面含义").performScrollTo().performClick()
        rule.onNodeWithText("这件事，不交给硬币").assertExists()
        rule.onNodeWithText("投出这枚硬币").assertDoesNotExist()
        rule.onNodeWithText("明确切换：用离线通用题目").assertDoesNotExist()
        rule.onNodeWithText("返回修改事项和两面含义").performScrollTo().performClick()
        fields[0].performTextReplacement("")
        fields[1].performTextReplacement("")
        fields[2].performTextReplacement("")
    }
}
