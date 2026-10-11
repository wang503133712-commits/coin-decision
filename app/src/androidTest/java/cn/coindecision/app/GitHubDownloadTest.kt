package cn.coindecision.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.coindecision.app.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class GitHubDownloadTest {
    @Test fun downloadedFutureApkPassesRealAndroidPackageAndSignatureChecks() = runBlocking {
        assumeTrue("使用专用未来版 APK 夹具单独验证下载链路", InstrumentationRegistry.getArguments().getString("updatePhase") == "github")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(requireNotNull(context.externalCacheDir), "github-future-fixture.apk")
        assertTrue(fixture.exists())
        val bytes = fixture.readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val parts = BuildConfig.VERSION_NAME.split('.').map { it.toInt() }
        val futureName = "${parts[0]}.${parts[1]}.${parts[2] + 1}"
        val tag = "v$futureName"
        val mismatchTag = "v${parts[0]}.${parts[1]}.${parts[2] + 2}"
        val url = "https://github.com/test/coin/releases/download/$tag/coin-decision.apk"
        val metadata = """{"tag_name":"$tag","draft":false,"prerelease":false,"assets":[{"name":"coin-decision.apk","state":"uploaded","size":${bytes.size},"digest":"sha256:$hash","browser_download_url":"$url"}]}"""
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertNull(request.header("Authorization")); requests++
            val body = if (request.url.host == "api.github.com") metadata.toResponseBody("application/json".toMediaType())
                else bytes.toResponseBody("application/vnd.android.package-archive".toMediaType())
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("test fixture").body(body).build()
        }.build()
        val service = GitHubUpdates(client)
        val release = requireNotNull(service.latest("test/coin", BuildConfig.VERSION_NAME))
        var progress = 0
        val updater = ApkUpdater(context)
        val candidate = updater.download(release, service) { progress = it }
        assertEquals(100, progress); assertEquals(2, requests)
        assertEquals(BuildConfig.VERSION_CODE + 1L, candidate.versionCode)
        assertEquals(futureName, candidate.versionName)
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("github-update-") })
        assertTrue(candidate.file.exists())
        candidate.file.delete()
        try {
            updater.download(release.copy(tag = mismatchTag, url = url.replace(tag, mismatchTag)), service) { }
            fail("错误 Release 标签不能安装")
        } catch (e: IllegalStateException) { assertTrue(e.message.orEmpty().contains("标签不一致")) }
        assertFalse(candidate.file.exists())
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("github-update-") })
    }
    @Test fun realPublicReleaseMatchesInstalledFinalApk() = runBlocking {
        assumeTrue("发布后单独运行真实 GitHub 下载验证", InstrumentationRegistry.getArguments().getString("updatePhase") == "live")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = GitHubUpdates()
        val release = requireNotNull(service.latest(DEFAULT_GITHUB_REPOSITORY, "1.3.0"))
        assertEquals("v${BuildConfig.VERSION_NAME}", release.tag)
        val file = File(context.cacheDir, "live-release-test.apk")
        try {
            var progress = 0
            service.download(release, file) { progress = it }
            assertEquals(100, progress)
            fun hash(apk: File): String {
                val digest = MessageDigest.getInstance("SHA-256")
                apk.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) { val count = input.read(buffer); if (count == -1) break; digest.update(buffer, 0, count) }
                }
                return digest.digest().joinToString("") { "%02x".format(it) }
            }
            assertEquals(hash(File(context.applicationInfo.sourceDir)), hash(file))
        } finally { file.delete() }
    }
}
