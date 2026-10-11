package cn.coindecision.app.ai

import cn.coindecision.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface DecisionAiService { suspend fun generate(request: DecisionRequest): AiResponse }
class OfflineDecisionAiService : DecisionAiService {
    override suspend fun generate(request: DecisionRequest): AiResponse {
        Contract.request(request)
        if (SafetyGate.blocked(request)) return AiResponse(1, AiStatus.blocked, SafetyGate.message, emptyList())
        val prompts = listOf(
            "暂时放下别人的期待，你更愿意选择哪一种安排？",
            "比较两种安排可能带来的体验，你更期待哪一种？",
            "考虑两种安排各自的时间、精力和其他成本，你更能接受哪一种？",
            "考虑两种安排的不确定性，你觉得哪种更为稳妥？",
            "回头看这次选择，哪一种安排更可能让你少一些遗憾？"
        )
        val labels = listOf("明显更倾向 A：${request.optionA}", "稍微更倾向 A：${request.optionA}",
            "两种安排差不多", "稍微更倾向 B：${request.optionB}", "明显更倾向 B：${request.optionB}")
        return Contract.response(AiResponse(1, AiStatus.ok, "", Dimension.entries.mapIndexed { i, dim ->
            val optionLabels = if (dim == Dimension.uncertainty_acceptance)
                listOf("明显觉得 A 更稳妥：${request.optionA}", "稍微觉得 A 更稳妥：${request.optionA}", "两种安排差不多",
                    "稍微觉得 B 更稳妥：${request.optionB}", "明显觉得 B 更稳妥：${request.optionB}") else labels
            Question("q${i+1}", dim, prompts[i], Lean.entries.mapIndexed { j, lean -> Option("q${i+1}_${j+1}", optionLabels[j], lean) })
        }))
    }
}

class AiFailure(val userMessage: String, val retryable: Boolean = false) : IOException(userMessage)
@Serializable data class ChatMessage(val role: String, val content: String)
@Serializable data class ChatRequest(
    val model: String, val messages: List<ChatMessage>, val stream: Boolean = false,
    val response_format: Map<String, String> = mapOf("type" to "json_object"),
    val thinking: Map<String, String> = mapOf("type" to "disabled"), val max_tokens: Int = 4096
)
@Serializable private data class Envelope(val choices: List<Choice> = emptyList())
@Serializable private data class Choice(val message: Content, val finish_reason: String? = null)
@Serializable private data class Content(val content: String? = null)

fun validatedBaseUrl(value: String): HttpUrl {
    val url = value.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("请输入有效 HTTPS 地址")
    require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
        "接口必须是无账号、无查询参数的 HTTPS 地址"
    }
    return url.newBuilder().encodedPath(url.encodedPath.trimEnd('/') + "/").build()
}

class DeepSeekDecisionAiService(
    private val config: AiConfig, private val key: String, private val systemPrompt: String,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build(),
    private val backoff: suspend () -> Unit = { delay(750) }
) : DecisionAiService {
    private val envelopeCodec = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    override suspend fun generate(request: DecisionRequest): AiResponse {
        Contract.request(request)
        if (SafetyGate.blocked(request)) return AiResponse(1, AiStatus.blocked, SafetyGate.message, emptyList())
        val content = send(listOf(ChatMessage("system", systemPrompt), ChatMessage("user", codec.encodeToString(request))))
        return try { Contract.response(codec.decodeFromString<AiResponse>(content)) }
        catch (_: IllegalArgumentException) { throw AiFailure("题目格式或内容契约校验失败，请重试或明确选择离线通用题目") }
    }
    suspend fun testConnection() {
        val content = send(listOf(ChatMessage("system", "Return only json: {\"ok\":true}"), ChatMessage("user", "Connection test. Return json.")))
        val element = try { envelopeCodec.parseToJsonElement(content) } catch (_: Exception) { throw AiFailure("连接返回了非法 JSON") }
        if (element.toString() != "{\"ok\":true}") throw AiFailure("已收到响应，但连接测试内容不符合约定")
    }
    private suspend fun send(messages: List<ChatMessage>): String {
        if (key.isBlank()) throw AiFailure("NotConfigured：请先保存个人 API Key")
        val base = try { validatedBaseUrl(config.baseUrl) } catch (_: IllegalArgumentException) { throw AiFailure("接口地址不合法，只接受 HTTPS") }
        if (config.model.isBlank() || config.model.length > 100) throw AiFailure("模型 ID 为空或过长")
        val request = Request.Builder().url(base.resolve("chat/completions")!!)
            .header("Authorization", "Bearer ${key.trim()}")
            .post(codec.encodeToString(ChatRequest(config.model, messages)).toRequestBody("application/json".toMediaType())).build()
        repeat(2) { attempt ->
            try {
                client.newCall(request).await().use { response ->
                    if (!response.isSuccessful) {
                        val message = when (response.code) {
                            401, 403 -> "鉴权失败，请检查个人 Key 和权限"
                            429 -> "服务商限流，请稍后重试"
                            in 500..599 -> "服务商暂时不可用"
                            else -> "接口请求失败（HTTP ${response.code}）"
                        }
                        throw AiFailure(message, response.code == 429 || response.code in 500..599)
                    }
                    val body = response.body ?: throw AiFailure("接口响应为空")
                    if (body.contentLength() > 262144) throw AiFailure("接口响应过大")
                    val buffer = okio.Buffer()
                    val source = body.source()
                    while (buffer.size <= 262144) {
                        if (source.read(buffer, minOf(8192L, 262145L - buffer.size)) == -1L) break
                    }
                    if (buffer.size > 262144) throw AiFailure("接口响应过大")
                    val envelope = try { envelopeCodec.decodeFromString<Envelope>(buffer.readUtf8()) }
                    catch (_: IllegalArgumentException) { throw AiFailure("接口响应不是有效 JSON") }
                    val choice = envelope.choices.firstOrNull() ?: throw AiFailure("接口没有返回 choices")
                    if (choice.finish_reason != "stop") throw AiFailure("接口输出截断或未正常结束")
                    return choice.message.content?.takeIf { it.isNotBlank() } ?: throw AiFailure("接口返回了空 content")
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: IOException) {
                val failure = when (e) {
                    is AiFailure -> e
                    is javax.net.ssl.SSLException, is javax.net.ssl.SSLPeerUnverifiedException -> AiFailure("HTTPS 证书验证失败，请检查接口地址", false).apply { initCause(e) }
                    else -> AiFailure("网络中断或超时，请检查网络", true).apply { initCause(e) }
                }
                if (attempt == 1 || !failure.retryable) throw failure
                backoff()
            }
        }
        throw AiFailure("请求失败")
    }
}
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}
