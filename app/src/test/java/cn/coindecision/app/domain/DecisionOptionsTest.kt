package cn.coindecision.app.domain

import org.junit.Assert.*
import org.junit.Test

class DecisionOptionsTest {
    @Test fun extractsCommonBinaryQuestionsWithoutNetwork() {
        val cases = listOf(
            Triple("这周末要不要去露营？", "这周末去露营", "这周末不去露营"),
            Triple("我该不该买新手机", "我买新手机", "我不买新手机"),
            Triple("明天是否参加聚餐？", "明天参加聚餐", "明天不参加聚餐"),
            Triple("需不需要换工作", "换工作", "不换工作"),
            Triple("我能不能接受邀请", "我接受邀请", "我不接受邀请"),
            Triple("去不去露营", "去露营", "不去露营"),
            Triple("今晚吃不吃火锅？", "今晚吃火锅", "今晚不吃火锅"),
            Triple("明天去露营吗？", "明天去露营", "明天不去露营"),
            Triple("我应该去露营吗", "我去露营", "我不去露营"),
            Triple("请问要不要去散步？", "去散步", "不去散步")
        )
        cases.forEach { (matter, a, b) -> assertEquals(matter, DecisionOptions(a, b), DecisionOptions.infer(matter)) }
    }
    @Test fun retainsNegatedActionDirection() {
        assertEquals(DecisionOptions("我不去聚餐", "我去聚餐"), DecisionOptions.infer("我要不要不去聚餐？"))
        assertEquals(DecisionOptions("不参加", "参加"), DecisionOptions.infer("是否不参加"))
    }
    @Test fun extractsExplicitAlternativesWithoutInventingActions() {
        val cases = listOf(
            Triple("喝茶还是咖啡？", "喝茶", "咖啡"),
            Triple("去北京还是上海", "去北京", "上海"),
            Triple("买房还是投资", "买房", "投资"),
            Triple("买车还是租车？", "买车", "租车"),
            Triple("去露营还是在家休息", "去露营", "在家休息"),
            Triple("继续工作还是辞职", "继续工作", "辞职"),
            Triple("是红色还是蓝色？", "红色", "蓝色")
        )
        cases.forEach { (matter, a, b) -> assertEquals(matter, DecisionOptions(a, b), DecisionOptions.infer(matter)) }
    }
    @Test fun leavesUnclearOrOversizedQuestionsForCorrection() {
        listOf("", "吃什么？", "这件事怎么办", "要不要", "还是咖啡", "茶还是", "茶还是茶", "茶还是咖啡还是水",
            "要不要喝茶还是咖啡", "要不要去露营？要不要买装备？", "要不要去露营，顺便买装备", "是否没有安排",
            "要不要" + "去".repeat(121)).forEach { assertNull(it, DecisionOptions.infer(it)) }
    }
}
