package cn.coindecision.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

private const val MAX_APK_BYTES = 150L * 1024 * 1024

internal fun trustedGithubTransport(url: HttpUrl): Boolean = url.isHttps && url.port == 443 &&
    url.username.isEmpty() && url.password.isEmpty() && (url.host in setOf("api.github.com", "github.com") ||
    url.host.endsWith(".githubusercontent.com"))

fun githubRepository(value: String): String {
    val text = value.trim().trimEnd('/')
    val path = if (text.startsWith("https://")) {
        val url = text.toHttpUrlOrNull() ?: error("请输入 GitHub 仓库地址")
        require(url.host == "github.com" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
            url.query == null && url.fragment == null) { "请使用公开 GitHub 仓库地址" }
        url.encodedPath.removePrefix("/")
    } else text
    val normalized = path.removeSuffix(".git")
    require(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}").matches(normalized) &&
        normalized.substringAfter('/') !in listOf(".", "..")) { "格式应为 用户名/仓库名，或 https://github.com/用户名/仓库名" }
    return normalized
}

private fun versionParts(value: String): List<Int> {
    require(Regex("v?\\d{1,9}\\.\\d{1,9}\\.\\d{1,9}").matches(value)) { "正式发布标签应为 v主版号.次版号.修订号，例如 v1.3.1" }
    return value.removePrefix("v").split('.').map { it.toInt() }
}

fun newerRelease(remote: String, current: String): Boolean {
    val a = versionParts(remote); val b = versionParts(current)
    return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
}

@Serializable private data class ReleaseAsset(
    val name: String, val size: Long, val state: String,
    @SerialName("browser_download_url") val url: String, val digest: String? = null
)
@Serializable private data class ReleaseResponse(
    @SerialName("tag_name") val tag: String, val draft: Boolean, val prerelease: Boolean,
    val body: String? = null, val assets: List<ReleaseAsset>
)
data class GitHubRelease(val repository: String, val tag: String, val notes: String,
    val assetName: String, val url: String, val size: Long, val sha256: String?)

class GitHubUpdates(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES)
    .retryOnConnectionFailure(false).followSslRedirects(false)
    .addNetworkInterceptor { chain ->
        val url = chain.request().url
        require(trustedGithubTransport(url)) { "更新下载跳转到了不受支持的地址" }
        chain.proceed(chain.request())
    }.build()) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(repository: String, currentVersion: String): GitHubRelease? = withContext(Dispatchers.IO) {
        val repo = githubRepository(repository)
        val request = Request.Builder().url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "CoinDecision-Android").build()
        client.newCall(request).execute().use { response ->
            check(response.code != 404) { "找不到公开仓库的正式 Release，请先发布签名 APK" }
            check(response.code != 403 && response.code != 429) { "GitHub 请求受限，请稍后再试" }
            check(response.isSuccessful) { "检查更新失败（HTTP ${response.code}）" }
            val source = response.body?.source() ?: error("GitHub 返回了空响应")
            require(!source.request(1024 * 1024 + 1L)) { "更新信息过大" }
            val bytes = source.readByteArray()
            coroutineContext.ensureActive()
            val release = json.decodeFromString<ReleaseResponse>(bytes.toString(Charsets.UTF_8))
            require(!release.draft && !release.prerelease) { "只接受正式发布版本" }
            if (!newerRelease(release.tag, currentVersion)) return@withContext null
            val apks = release.assets.filter { it.state == "uploaded" && it.name.endsWith(".apk", true) }
            val asset = apks.singleOrNull { it.name == "coin-decision.apk" } ?: apks.singleOrNull()
                ?: error("Release 请上传一个签名 APK；多个 APK 时请把通用包命名为 coin-decision.apk")
            val result = GitHubRelease(repo, release.tag, release.body.orEmpty().take(2000), asset.name,
                asset.url, asset.size, asset.digest)
            validateAsset(result)
            result
        }
    }

    private fun validateAsset(release: GitHubRelease) {
        val url = release.url.toHttpUrlOrNull() ?: error("无效的 APK 下载地址")
        val segments = url.pathSegments
        require(url.isHttps && url.host == "github.com" && url.port == 443 && url.username.isEmpty() &&
            url.password.isEmpty() && url.query == null && url.fragment == null && segments.size == 6 &&
            "${segments[0]}/${segments[1]}".equals(githubRepository(release.repository), true) &&
            segments[2] == "releases" && segments[3] == "download" && segments[4] == release.tag &&
            segments[5] == release.assetName) { "APK 下载地址与该仓库的 Release 不匹配" }
        require(release.size in 1..MAX_APK_BYTES) { "更新包大小无效，上限 150 MB" }
        require(release.sha256 == null || Regex("sha256:[a-fA-F0-9]{64}").matches(release.sha256)) { "更新包校验值无效" }
    }

    suspend fun download(release: GitHubRelease, file: File, progress: suspend (Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        validateAsset(release)
        try {
            val request = Request.Builder().url(release.url).header("User-Agent", "CoinDecision-Android").build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "下载失败（HTTP ${response.code}）" }
                val body = response.body ?: error("下载内容为空")
                require(body.contentLength() == -1L || body.contentLength() == release.size) { "更新包大小与发布信息不一致" }
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L; var lastPercent = -1
                body.byteStream().use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        total += count
                        require(total <= release.size && total <= MAX_APK_BYTES) { "下载内容超出声明大小" }
                        digest.update(buffer, 0, count); output.write(buffer, 0, count)
                        val percent = (total * 100 / release.size).toInt()
                        if (percent != lastPercent) { progress(percent); lastPercent = percent }
                    }
                } }
                require(total == release.size) { "更新包下载不完整，请重试" }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                require(release.sha256 == null || release.sha256.substringAfter(':').equals(hash, true)) { "更新包 SHA-256 不匹配，请重试" }
            }
        } catch (e: Exception) { file.delete(); throw e }
    }
}
