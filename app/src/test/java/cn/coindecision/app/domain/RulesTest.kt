package cn.coindecision.app.domain

import cn.coindecision.app.ai.OfflineDecisionAiService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RulesTest {
    private val request = DecisionRequest(matter = "周末露营", optionA = "去露营", optionB = "不去露营")
    @Test fun uncertaintyQuestionUsesSubjectiveStabilityWithoutChangingScores() = runTest {
        val q = OfflineDecisionAiService().generate(request).questions[3]
        assertEquals("考虑两种安排的不确定性，你觉得哪种更为稳妥？", q.text)
        assertEquals(Dimension.uncertainty_acceptance, q.dimension)
        assertEquals("明显觉得 A 更稳妥：去露营", q.options.first().label)
        assertEquals(Lean.STRONG_A, q.options.first().lean)
        assertEquals("明显觉得 B 更稳妥：不去露营", q.options.last().label)
        assertEquals(Lean.STRONG_B, q.options.last().lean)
        assertEquals(3, QUESTION_VERSION)
    }
    @Test fun scoresAndSkippedAreDeterministic() = runTest {
        val q = OfflineDecisionAiService().generate(request).questions
        val scorer = PreferenceScorer()
        for (lean in Lean.entries) {
            val answers = q.map { Answer(AnswerKind.SELECTED, it.options.single { o -> o.lean == lean }.id) }
            assertEquals(lean.score, scorer.score(q, answers), 1e-12)
        }
        assertEquals(.5, scorer.score(q, List(5) { Answer(AnswerKind.SKIPPED) }), 1e-12)
        val mixed = q.mapIndexed { i, question -> Answer(AnswerKind.SELECTED, question.options[i].id) }
        assertEquals(.5, scorer.score(q, mixed), 1e-12)
        assertThrows(IllegalStateException::class.java) { scorer.score(q, List(5) { Answer() }) }
        assertThrows(IllegalStateException::class.java) { scorer.score(q, List(5) { Answer(AnswerKind.SELECTED, "unknown") }) }
        assertThrows(IllegalArgumentException::class.java) { scorer.score(q, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { scorer.score(q, List(5) { Answer(AnswerKind.SKIPPED, "hidden") }) }
    }
    @Test fun probabilityAndStrictRandomBoundary() {
        val calc = CoinProbabilityCalculator()
        assertEquals(.35, calc.calculate(0.0).pA, 1e-12)
        assertEquals(.5, calc.calculate(.5).pA, 1e-12)
        assertEquals(.65, calc.calculate(1.0).pA, 1e-12)
        (0..1000).forEach { i -> val p = calc.calculate(i / 1000.0); assertTrue(p.pA in .35.. .65); assertEquals(1.0, p.pA + p.pB, 1e-12) }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -.01, 1.01).forEach { s -> assertThrows(IllegalArgumentException::class.java) { calc.calculate(s) } }
        assertEquals(Face.A, CoinTossEngine { .499999 }.toss(.5))
        assertEquals(Face.B, CoinTossEngine { .5 }.toss(.5))
        assertEquals(Face.B, CoinTossEngine { .500001 }.toss(.5))
        assertThrows(IllegalArgumentException::class.java) { CoinTossEngine { 1.0 }.toss(.5) }
        assertThrows(IllegalArgumentException::class.java) { CoinTossEngine { Double.NaN }.toss(.5) }
    }
    @Test fun seededSamplingIsASupplementaryCheck() {
        val rng = Random(314159)
        val engine = CoinTossEngine { rng.nextDouble() }
        for (p in listOf(.35, .5, .65)) {
            val observed = (1..100000).count { engine.toss(p) == Face.A } / 100000.0
            assertEquals(p, observed, .01)
        }
    }
    @Test fun contractRejectsMalformedQuestions() = runTest {
        val good = OfflineDecisionAiService().generate(request)
        assertEquals(good, Contract.response(good))
        assertThrows(IllegalArgumentException::class.java) { codec.decodeFromString<AiResponse>(codec.encodeToString(good).replace("STRONG_A", "UNKNOWN")) }
        assertThrows(IllegalArgumentException::class.java) { Contract.response(good.copy(questions = listOf(good.questions.first().copy(text = "")) + good.questions.drop(1))) }
        assertThrows(IllegalArgumentException::class.java) { Contract.response(good.copy(questions = listOf(good.questions.first().copy(text = "x".repeat(241))) + good.questions.drop(1))) }
        assertThrows(IllegalArgumentException::class.java) { Contract.response(good.copy(schemaVersion = 2)) }
        assertThrows(IllegalArgumentException::class.java) { Contract.response(good.copy(questions = good.questions.take(4))) }
        assertThrows(IllegalArgumentException::class.java) { Contract.response(good.copy(questions = good.questions.toMutableList().apply { set(1, first()) })) }
        val missingLean = good.questions.first().let { it.copy(options = List(5) { _ -> it.options.first() }) }
        assertThrows(IllegalArgumentException::class.java) { Contract.response(good.copy(questions = listOf(missingLean) + good.questions.drop(1))) }
        assertThrows(IllegalArgumentException::class.java) { Contract.response(AiResponse(1, AiStatus.blocked, "", emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { codec.decodeFromString<AiResponse>("""{"schemaVersion":1,"status":"ok","message":"","questions":[],"probability":1}""") }
        assertThrows(IllegalArgumentException::class.java) { Contract.request(request.copy(optionB = request.optionA)) }
    }
    @Test fun safetyDoesNotBlockEveryMentionOfMoney() = runTest {
        assertFalse(SafetyGate.blocked(request.copy(matter = "花点零花钱买冰淇淋")))
        assertFalse(SafetyGate.blocked(request.copy(matter = "要不要玩跳楼机")))
        for (matter in listOf("要不要自杀", "要不要跳楼", "我要不要停药", "把全部积蓄拿去投资", "投资50万元", "要不要起诉")) {
            val r = request.copy(matter = matter)
            assertTrue(SafetyGate.blocked(r))
            assertEquals(AiStatus.blocked, OfflineDecisionAiService().generate(r).status)
        }
    }
    @Test fun updatePolicyRequiresSameIdentityAndHigherVersion() {
        UpdatePolicy.validate("coin", 1, setOf("cert"), "coin", 2, setOf("cert"))
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.validate("coin", 1, setOf("cert"), "other", 2, setOf("cert")) }
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.validate("coin", 1, setOf("cert"), "coin", 1, setOf("cert")) }
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.validate("coin", 1, setOf("cert"), "coin", 0, setOf("cert")) }
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.validate("coin", 1, setOf("cert"), "coin", 2, setOf("attacker")) }
    }
}
