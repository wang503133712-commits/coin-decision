package cn.coindecision.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class GitHubUpdatesTest {
    private lateinit var server: MockWebServer
    private lateinit var service: GitHubUpdates
    private lateinit var file: File
    private val bytes = "test-only-apk-bytes".toByteArray()
    private val hash get() = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val assetUrl = "https://github.com/test/coin/releases/download/v1.3.1/coin-decision.apk"
    private fun asset(url: String = assetUrl, size: Long = bytes.size.toLong(), digest: String = "sha256:$hash") =
        """{"name":"coin-decision.apk","size":$size,"state":"uploaded","browser_download_url":"$url","digest":"$digest"}"""
    private fun release(tag: String = "v1.3.1", assets: String = asset(), prerelease: Boolean = false) =
        """{"tag_name":"$tag","draft":false,"prerelease":$prerelease,"body":"更新说明","assets":[$assets],"extra":"ignored"}"""
    private fun candidate(digest: String? = "sha256:$hash") = GitHubRelease("test/coin", "v1.3.1", "", "coin-decision.apk", assetUrl, bytes.size.toLong(), digest)

    @Before fun setup() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .followRedirects(false).retryOnConnectionFailure(false)
            .readTimeout(2, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                chain.proceed(request.newBuilder().url(server.url(request.url.encodedPath)).build())
            }.build()
        service = GitHubUpdates(client)
        file = File.createTempFile("coin-update-test", ".apk")
    }
    @After fun teardown() { server.shutdown(); file.delete() }

    @Test fun repositoryAndVersionValidation() {
        assertEquals("test/coin", githubRepository(" https://github.com/test/coin.git/ "))
        assertEquals("test/coin", githubRepository("test/coin"))
        for (value in listOf("http://github.com/test/coin", "https://github.com.evil.test/test/coin", "https://user:password@github.com/test/coin",
            "https://github.com/test/coin?token=secret", "https://github.com/test/coin/releases", "test/..", "test/.git", "test/coin/extra")) {
            assertThrows(IllegalArgumentException::class.java) { githubRepository(value) }
        }
        assertTrue(newerRelease("v1.10.0", "1.9.9"))
        assertFalse(newerRelease("v1.3.0", "1.3.0"))
        assertFalse(newerRelease("v1.2.9", "1.3.0"))
        assertThrows(IllegalArgumentException::class.java) { newerRelease("v1.3.1-beta", "1.3.0") }
    }
    @Test fun redirectsAllowGithubCdnAndRejectOtherHostsOrHttp() {
        for (url in listOf("https://github.com/", "https://api.github.com/", "https://release-assets.githubusercontent.com/file?token=cdn-only"))
            assertTrue(trustedGithubTransport(url.toHttpUrl()))
        for (url in listOf("http://github.com/", "https://github.com.evil.test/", "https://evilgithubusercontent.com/", "https://user:secret@github.com/", "https://github.com:444/"))
            assertFalse(trustedGithubTransport(url.toHttpUrl()))
    }
    @Test fun latestUsesPublicApiWithoutAnyKey() = runBlocking {
        server.enqueue(MockResponse().setBody(release()))
        val found = service.latest("https://github.com/test/coin", "1.3.0")!!
        assertEquals("v1.3.1", found.tag); assertEquals("更新说明", found.notes)
        val request = server.takeRequest()
        assertEquals("/repos/test/coin/releases/latest", request.path)
        assertNull(request.getHeader("Authorization"))
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertEquals("", request.body.readUtf8())
    }
    @Test fun oldOrEqualReleaseDoesNotOfferDownload() = runBlocking {
        for (tag in listOf("v1.2.2", "v1.3.0")) {
            server.enqueue(MockResponse().setBody(release(tag = tag, assets = "")))
            assertNull(service.latest("test/coin", "1.3.0"))
        }
    }
    @Test fun unpublishedMalformedAndAmbiguousReleasesFail() = runBlocking {
        val invalid = listOf("{}", "not-json", release(prerelease = true), release(assets = ""),
            release(assets = asset().replace("coin-decision.apk", "one.apk") + "," + asset().replace("coin-decision.apk", "two.apk")),
            release(assets = asset(size = 151L * 1024 * 1024)), release(assets = asset(digest = "sha256:broken")),
            release(assets = asset(url = assetUrl.replace("test/coin", "other/coin"))),
            release(assets = asset(url = assetUrl.replace("https://", "http://"))))
        for (body in invalid) {
            server.enqueue(MockResponse().setBody(body))
            try { service.latest("test/coin", "1.3.0"); fail("Accepted invalid release") } catch (_: IllegalArgumentException) { }
            catch (_: IllegalStateException) { }
        }
    }
    @Test fun networkErrorsAndOversizedMetadataAreBounded() = runBlocking {
        for (code in listOf(404, 403, 429, 500, 302)) {
            server.enqueue(MockResponse().setResponseCode(code).setHeader("Location", "http://unsafe.test"))
            try { service.latest("test/coin", "1.3.0"); fail() } catch (_: IllegalStateException) { }
        }
        server.enqueue(MockResponse().setBody(" ".repeat(1024 * 1024 + 1)))
        try { service.latest("test/coin", "1.3.0"); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(6, server.requestCount)
    }
    @Test fun downloadVerifiesDigestSizeAndReportsProgress() = runBlocking {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
        val progress = mutableListOf<Int>()
        service.download(candidate(), file) { progress.add(it) }
        assertArrayEquals(bytes, file.readBytes()); assertEquals(100, progress.last())
        assertNull(server.takeRequest().getHeader("Authorization"))
    }
    @Test fun truncatedOversizedAndCorruptDownloadsAreRemoved() = runBlocking {
        for (body in listOf(bytes.copyOf(bytes.size - 1), bytes + byteArrayOf(0), ByteArray(bytes.size))) {
            server.enqueue(MockResponse().setChunkedBody(okio.Buffer().write(body), 4))
            try { service.download(candidate(), file); fail() } catch (_: IllegalArgumentException) { }
            assertFalse(file.exists())
        }
    }
    @Test fun failedAndCancelledDownloadsAreRemoved() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        try { service.download(candidate(), file); fail() } catch (_: IllegalStateException) { }
        assertFalse(file.exists())
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
        try { service.download(candidate(), file) { throw CancellationException("test-only-cancel") }; fail() } catch (_: CancellationException) { }
        assertFalse(file.exists())
    }
    @Test fun digestMayBeAbsentButArchiveSignatureMustStillBeCheckedByCaller() = runBlocking {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
        service.download(candidate(null), file)
        assertArrayEquals(bytes, file.readBytes())
    }
}
