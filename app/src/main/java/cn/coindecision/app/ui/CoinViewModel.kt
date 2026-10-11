package cn.coindecision.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.coindecision.app.CoinApplication
import cn.coindecision.app.ai.*
import cn.coindecision.app.data.AppSettings
import cn.coindecision.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

enum class Page { MAIN, HISTORY, DETAIL, SETTINGS }
data class UiState(
    val loading: Boolean = true, val session: Session = Session(), val page: Page = Page.MAIN,
    val settings: AppSettings = AppSettings(), val hasKey: Boolean = false,
    val result: TossSnapshot? = null, val detail: TossSnapshot? = null,
    val history: List<TossSnapshot> = emptyList(), val notice: String = "", val tossing: Boolean = false,
    val testing: Boolean = false
)
class CoinViewModel(private val app: CoinApplication, private val testAiOverride: DecisionAiService? = null) : ViewModel() {
    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()
    private val repo = app.repository
    private var generation: Job? = null
    private var testJob: Job? = null
    private var epoch = 0L
    init {
        viewModelScope.launch {
            try {
                var session = repo.load()
                if (session.stage in listOf(Stage.GENERATING, Stage.ANIMATING)) {
                    session = session.copy(stage = if (session.activeTossId == null) Stage.CANCELLED else Stage.RESULT,
                        message = if (session.activeTossId == null) "上次出题已中断，可重新开始。" else "", revision = session.revision + 1)
                    repo.save(session)
                }
                val result = session.activeTossId?.let { repo.find(it) }
                _ui.update { it.copy(session = session, result = result, loading = false) }
            } catch (_: Exception) { _ui.update { it.copy(loading = false, notice = "读取本地数据失败，请检查设备存储。") } }
        }
        viewModelScope.launch {
            app.settings.flow.catch { _ui.update { it.copy(notice = "设置读取失败") } }.collect { settings ->
                val hasKey = withContext(Dispatchers.IO) { runCatching { app.vault.hasKey(settings.config.baseUrl) }.getOrDefault(false) }
                _ui.update { it.copy(settings = settings, hasKey = hasKey) }
            }
        }
        viewModelScope.launch {
            repo.history.catch { _ui.update { it.copy(notice = "历史读取失败") } }.collect { rows ->
                val records = rows.mapNotNull { runCatching { codec.decodeFromString<TossSnapshot>(it.payload) }.getOrNull() }
                _ui.update { it.copy(history = records) }
            }
        }
    }
    private fun change(transform: (Session) -> Session) {
        val next = transform(_ui.value.session).copy(revision = _ui.value.session.revision + 1)
        _ui.update { it.copy(session = next, notice = "") }
        viewModelScope.launch { try { repo.save(next) } catch (_: Exception) { _ui.update { it.copy(notice = "草稿保存失败，请检查存储空间") } } }
    }
    fun edit(matter: String, a: String, b: String) {
        if (_ui.value.tossing) return
        generation?.cancel(); epoch++
        change { old -> Session(matter = matter.take(500), optionA = a.take(120), optionB = b.take(120), revision = old.revision) }
        _ui.update { it.copy(result = null) }
    }
    fun confirm() {
        try {
            val s = _ui.value.session
            Contract.request(s.request())
            change { it.copy(stage = if (SafetyGate.blocked(s.request())) Stage.BLOCKED else Stage.CONFIRM,
                message = if (SafetyGate.blocked(s.request())) SafetyGate.message else "") }
        } catch (e: IllegalArgumentException) { notice(e.message ?: "请填写事项和两面含义") }
    }
    fun generate(offline: Boolean = false) {
        if (_ui.value.loading || _ui.value.session.stage == Stage.BLOCKED || _ui.value.tossing) return
        val original = _ui.value.session
        try { Contract.request(original.request()) } catch (e: IllegalArgumentException) { notice(e.message ?: "输入无效"); return }
        if (SafetyGate.blocked(original.request())) { change { it.copy(stage = Stage.BLOCKED, message = SafetyGate.message) }; return }
        if (original.questions.isNotEmpty() && original.stage !in listOf(Stage.ERROR, Stage.CANCELLED)) {
            change { it.copy(stage = Stage.ANSWER) }; return
        }
        generation?.cancel()
        val token = ++epoch
        val settings = _ui.value.settings
        val online = settings.online && !offline
        change { it.copy(stage = Stage.GENERATING, message = "", questions = emptyList(), answers = emptyList(),
            source = if (online) Source.DEEPSEEK else Source.OFFLINE, config = if (online) settings.config else null) }
        generation = viewModelScope.launch {
            try {
                val service = testAiOverride ?: if (online) deepSeek(settings.config) else OfflineDecisionAiService()
                val response = service.generate(original.request())
                ensureActive()
                if (token != epoch) return@launch
                change { it.copy(stage = when (response.status) { AiStatus.ok -> Stage.ANSWER; AiStatus.blocked -> Stage.BLOCKED; AiStatus.needs_clarification -> Stage.CLARIFY },
                    questions = response.questions, answers = response.questions.map { Answer() }, index = 0, message = response.message) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { if (token == epoch) change { it.copy(stage = Stage.ERROR, message = (e as? AiFailure)?.userMessage ?: "出题失败，请检查设置或重新输入") } }
        }
    }
    private suspend fun deepSeek(config: AiConfig): DeepSeekDecisionAiService {
        val key = withContext(Dispatchers.IO) { app.vault.read(config.baseUrl) }
        return DeepSeekDecisionAiService(config, key, app.systemPrompt)
    }
    fun cancelGeneration() {
        generation?.cancel(); epoch++
        change { it.copy(stage = Stage.CANCELLED, message = "已取消，没有生成或投币") }
    }
    fun answer(optionId: String?) {
        val s = _ui.value.session
        if (s.stage != Stage.ANSWER) return
        val q = s.questions.getOrNull(s.index) ?: return
        if (optionId != null && q.options.none { it.id == optionId }) { notice("未知选项"); return }
        change { it.copy(answers = it.answers.toMutableList().apply { set(it.index, if (optionId == null) Answer(AnswerKind.SKIPPED) else Answer(AnswerKind.SELECTED, optionId)) }) }
    }
    fun next() {
        val s = _ui.value.session
        if (s.answers.getOrNull(s.index)?.kind == AnswerKind.UNANSWERED) { notice("请选择一个选项，或主动跳过"); return }
        change { it.copy(index = minOf(it.index + 1, 4), stage = if (it.index == 4) Stage.READY else Stage.ANSWER) }
    }
    fun previous() { change { it.copy(index = maxOf(0, it.index - 1), stage = Stage.ANSWER) } }
    fun toss() {
        val state = _ui.value
        if (state.loading || state.tossing || state.session.stage != Stage.READY) return
        _ui.update { it.copy(tossing = true, notice = "") }
        viewModelScope.launch {
            try {
                val result = repo.toss(state.session)
                val saved = repo.load()
                _ui.update { it.copy(result = result, session = saved.copy(stage = Stage.ANIMATING), tossing = false) }
            } catch (_: Exception) { _ui.update { it.copy(tossing = false, notice = "结果保存失败或状态无效，尚未完成投币。可以重试。") } }
        }
    }
    fun finishAnimation() { if (_ui.value.session.stage == Stage.ANIMATING) _ui.update { it.copy(session = it.session.copy(stage = Stage.RESULT)) } }
    fun again() {
        if (_ui.value.session.stage != Stage.RESULT) return
        _ui.update { it.copy(result = null) }
        change { it.copy(stage = Stage.READY, activeTossId = null) }
    }
    fun newDecision() {
        if (_ui.value.tossing) return
        generation?.cancel(); epoch++
        change { Session(revision = it.revision) }
        _ui.update { it.copy(page = Page.MAIN, result = null, detail = null) }
    }
    fun page(page: Page) { if (!_ui.value.tossing) _ui.update { it.copy(page = page, notice = "") } }
    fun detail(record: TossSnapshot) { _ui.update { it.copy(page = Page.DETAIL, detail = record, notice = "") } }
    fun back() {
        val state = _ui.value
        if (state.tossing || state.session.stage == Stage.ANIMATING) return
        if (state.page == Page.DETAIL) { page(Page.HISTORY); return }
        if (state.page != Page.MAIN) { page(Page.MAIN); return }
        when (state.session.stage) {
            Stage.GENERATING -> cancelGeneration()
            Stage.ANSWER -> if (state.session.index > 0) previous() else change { it.copy(stage = Stage.CONFIRM) }
            Stage.READY -> change { it.copy(stage = Stage.ANSWER, index = 4) }
            Stage.RESULT -> newDecision()
            Stage.CONFIRM, Stage.ERROR, Stage.CANCELLED, Stage.BLOCKED, Stage.CLARIFY -> change { it.copy(stage = Stage.EDIT) }
            else -> Unit
        }
    }
    fun delete(id: String) { viewModelScope.launch {
        try {
            val s = repo.delete(id)
            _ui.update { it.copy(session = s, result = if (it.result?.tossId == id) null else it.result, detail = null, page = Page.HISTORY) }
        } catch (_: Exception) { notice("删除失败，请重试") }
    } }
    fun clearHistory() { viewModelScope.launch { try { val s = repo.clear(); _ui.update { it.copy(session = s, result = null, detail = null) } } catch (_: Exception) { notice("清空失败") } } }
    fun feedback(text: String) { val id = _ui.value.result?.tossId ?: return; viewModelScope.launch {
        try { repo.feedback(id, text); _ui.update { it.copy(result = it.result?.copy(feedback = text)) } } catch (_: Exception) { notice("反馈保存失败") }
    } }
    fun saveSettings(value: AppSettings, key: String) { viewModelScope.launch {
        try {
            val base = validatedBaseUrl(value.config.baseUrl).toString().trimEnd('/')
            require(value.config.model.isNotBlank() && value.config.model.length <= 100) { "请输入有效模型 ID" }
            val normalized = value.copy(config = value.config.copy(baseUrl = base, model = value.config.model.trim()))
            withContext(Dispatchers.IO) {
                if (validatedBaseUrl(_ui.value.settings.config.baseUrl) != validatedBaseUrl(base)) app.vault.delete()
                if (key.isNotBlank()) app.vault.save(base, key)
            }
            app.settings.save(normalized)
            val hasKey = withContext(Dispatchers.IO) { app.vault.hasKey(base) }
            _ui.update { it.copy(hasKey = hasKey, notice = "设置已保存" + if (normalized.online && !hasKey) "，个人 Key 尚未配置" else "") }
        } catch (e: Exception) { notice(e.message?.take(160) ?: "设置保存失败") }
    } }
    fun deleteKey() { viewModelScope.launch { try { withContext(Dispatchers.IO) { app.vault.delete() }; _ui.update { it.copy(hasKey = false, notice = "个人 Key 已删除") } } catch (_: Exception) { notice("删除 Key 失败") } } }
    fun testConnection() {
        if (_ui.value.testing) return
        _ui.update { it.copy(testing = true, notice = "正在测试已保存的配置…") }
        val config = _ui.value.settings.config
        testJob = viewModelScope.launch {
            try { deepSeek(config).testConnection(); notice("连接测试成功（不表示题目质量已验证）") }
            catch (cancel: CancellationException) { notice("连接测试已取消"); throw cancel }
            catch (e: Exception) { notice((e as? AiFailure)?.userMessage ?: "连接测试失败或 Key 无法读取") }
            finally { _ui.update { it.copy(testing = false) } }
        }
    }
    fun cancelTest() { testJob?.cancel() }
    fun notice(value: String) { _ui.update { it.copy(notice = value) } }
}
