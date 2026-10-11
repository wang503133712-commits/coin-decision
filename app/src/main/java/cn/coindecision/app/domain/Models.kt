package cn.coindecision.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.UUID

val codec = Json { ignoreUnknownKeys = false; encodeDefaults = true }
const val ALGORITHM_VERSION = 1
const val QUESTION_VERSION = 3

@Serializable enum class Lean(val score: Double) {
    STRONG_A(1.0), LEAN_A(0.75), NEUTRAL(0.5), LEAN_B(0.25), STRONG_B(0.0)
}
@Serializable enum class Dimension { desire, expectation, cost_acceptance, uncertainty_acceptance, regret }
@Serializable enum class AiStatus { ok, needs_clarification, blocked }
@Serializable data class Option(val id: String, val label: String, val lean: Lean)
@Serializable data class Question(val id: String, val dimension: Dimension, val text: String, val options: List<Option>)
@Serializable data class AiResponse(val schemaVersion: Int, val status: AiStatus, val message: String, val questions: List<Question>)
@Serializable data class DecisionRequest(val schemaVersion: Int = 1, val matter: String, val optionA: String, val optionB: String)
@Serializable data class AiConfig(val baseUrl: String = "https://api.deepseek.com", val model: String = "deepseek-flash")
@Serializable enum class Source { OFFLINE, DEEPSEEK }
@Serializable enum class Stage { EDIT, CONFIRM, GENERATING, ANSWER, READY, ANIMATING, RESULT, ERROR, CANCELLED, BLOCKED, CLARIFY }
@Serializable enum class AnswerKind { UNANSWERED, SELECTED, SKIPPED }
@Serializable data class Answer(val kind: AnswerKind = AnswerKind.UNANSWERED, val optionId: String? = null)
@Serializable data class Session(
    val decisionId: String = UUID.randomUUID().toString(),
    val questionVersion: Int = QUESTION_VERSION,
    val algorithmVersion: Int = ALGORITHM_VERSION,
    val matter: String = "", val optionA: String = "", val optionB: String = "",
    val stage: Stage = Stage.EDIT, val questions: List<Question> = emptyList(),
    val answers: List<Answer> = emptyList(), val index: Int = 0,
    val source: Source = Source.OFFLINE, val config: AiConfig? = null,
    val activeTossId: String? = null, val message: String = "", val tossCount: Int = 0,
    val revision: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun request() = DecisionRequest(matter = matter, optionA = optionA, optionB = optionB)
}
@Serializable enum class Face { A, B }
@Serializable data class Probability(val score: Double, val pA: Double, val pB: Double)
@Serializable data class TossSnapshot(
    val tossId: String, val session: Session, val probability: Probability,
    val face: Face, val createdAt: Long, val number: Int, val feedback: String? = null
)

object Contract {
    private fun text(value: String, max: Int) = require(value.isNotBlank() && value.length <= max) { "文本为空或过长" }
    fun request(r: DecisionRequest) {
        require(r.schemaVersion == 1) { "不支持的请求版本" }
        text(r.matter, 500); text(r.optionA, 120); text(r.optionB, 120)
        require(r.optionA.trim() != r.optionB.trim()) { "两面含义不能相同" }
    }
    fun response(r: AiResponse): AiResponse {
        require(r.schemaVersion == 1) { "不支持的题目版本" }
        require(r.message.length <= 500) { "提示过长" }
        if (r.status != AiStatus.ok) {
            text(r.message, 500); require(r.questions.isEmpty()) { "非正常状态不能包含题目" }; return r
        }
        require(r.questions.size == 5) { "需要完整的五道题" }
        require(r.questions.map { it.dimension }.toSet() == Dimension.entries.toSet()) { "题目维度不完整" }
        val ids = mutableSetOf<String>()
        r.questions.forEach { q ->
            text(q.id, 64); require(ids.add(q.id)) { "题目 ID 重复" }; text(q.text, 240)
            require(q.options.size == 5 && q.options.map { it.lean }.toSet() == Lean.entries.toSet()) { "选项立场不完整" }
            q.options.forEach { o -> text(o.id, 64); require(ids.add(o.id)) { "选项 ID 重复" }; text(o.label, 200) }
        }
        return r
    }
    fun questions(q: List<Question>) = response(AiResponse(1, AiStatus.ok, "", q))
}

class PreferenceScorer {
    fun score(questions: List<Question>, answers: List<Answer>): Double {
        Contract.questions(questions)
        require(answers.size == 5) { "答案不完整" }
        return questions.zip(answers).sumOf { (q, a) ->
            when (a.kind) {
                AnswerKind.UNANSWERED -> error("还有未回答的题目")
                AnswerKind.SKIPPED -> { require(a.optionId == null); 0.5 }
                AnswerKind.SELECTED -> q.options.singleOrNull { it.id == a.optionId }?.lean?.score
                    ?: error("未知选项")
            }
        } / 5.0
    }
}
class CoinProbabilityCalculator {
    fun calculate(score: Double): Probability {
        require(score.isFinite() && score in 0.0..1.0) { "偏好估计不合法" }
        val pA = 0.35 + 0.30 * score
        return Probability(score, pA, 1.0 - pA)
    }
}
fun interface RandomSource { fun nextDouble(): Double }
class SecureRandomSource : RandomSource {
    private val random = SecureRandom()
    override fun nextDouble() = random.nextDouble()
}
class CoinTossEngine(private val random: RandomSource = SecureRandomSource()) {
    fun toss(pA: Double): Face {
        require(pA.isFinite() && pA in 0.35..0.65) { "概率不合法" }
        val u = random.nextDouble()
        require(u.isFinite() && u >= 0.0 && u < 1.0) { "随机源不合法" }
        return if (u < pA) Face.A else Face.B
    }
}

object SafetyGate {
    // shortcut: 保守关键词规则不能识别所有语义，扩展公开服务时引入专门风险审核。
    private val risks = listOf(
        Regex("自杀|自残|自伤|伤害自己|结束生命|跳楼(?!机)|跳桥|割腕|服毒|吞药|杀人|伤人|砍人|捅人|殴打|毒死|炸弹|纵火|酒驾|诈骗|贩毒|抢劫"),
        Regex("停药|断药|换药|加药|药量|药物剂量|手术|化疗|是否就医|要不要就医|停止治疗"),
        Regex("大额.{0,8}(借|贷|投资)|投资.{0,10}\\d+\\s*(万|亿)|借贷|贷款|杠杆|抵押|全部积蓄|梭哈|重仓|卖房.{0,6}投资"),
        Regex("起诉|撤诉|认罪|签.{0,6}(法律|担保|离婚)|放弃.{0,6}(诉讼|法律|上诉)")
    )
    fun blocked(r: DecisionRequest): Boolean = risks.any { it.containsMatchIn("${r.matter}\n${r.optionA}\n${r.optionB}") }
    const val message = "这件事涉及安全或重大医疗、财务、法律后果，不适合交给硬币。请查阅可靠信息或寻求专业帮助；有紧迫人身危险时联系当地急救或可信任的人。基础检测不能覆盖所有风险。"
}

object UpdatePolicy {
    fun validate(currentPackage: String, currentCode: Long, currentSigners: Set<String>,
                 newPackage: String, newCode: Long, newSigners: Set<String>) {
        require(newPackage == currentPackage) { "这不是本应用的更新包" }
        require(newCode > currentCode) { "更新版号必须高于当前版本，不能降级或重复安装" }
        require(currentSigners.isNotEmpty() && currentSigners == newSigners) { "更新包签名不匹配" }
    }
}
