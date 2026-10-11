package cn.coindecision.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import cn.coindecision.app.domain.UpdatePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class UpdateCandidate(val file: File, val versionName: String, val versionCode: Long)
class ApkUpdater(private val context: Context) {
    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val certificates = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return certificates.orEmpty().map { cert ->
            MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
    @Suppress("DEPRECATION")
    private fun code(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION")
    suspend fun inspect(uri: Uri): UpdateCandidate = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val file = File(dir, "candidate.apk")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        total += n
                        require(total <= 150L * 1024 * 1024) { "更新包过大，上限 150 MB" }
                        output.write(buffer, 0, n)
                    }
                }
            } ?: error("无法读取更新包")
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val current = context.packageManager.getPackageInfo(context.packageName, flags)
            val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("不是有效的 APK 更新包")
            UpdatePolicy.validate(current.packageName, code(current), signers(current), candidate.packageName, code(candidate), signers(candidate))
            UpdateCandidate(file, candidate.versionName ?: code(candidate).toString(), code(candidate))
        } catch (e: Exception) { file.delete(); throw e }
    }
    suspend fun download(release: GitHubRelease, service: GitHubUpdates, progress: suspend (Int) -> Unit): UpdateCandidate {
        val file = withContext(Dispatchers.IO) { File.createTempFile("github-update-", ".apk", context.cacheDir) }
        try {
            service.download(release, file, progress)
            val candidate = inspect(Uri.fromFile(file))
            return withContext(Dispatchers.IO) {
                if (candidate.versionName != release.tag.removePrefix("v")) {
                    candidate.file.delete()
                    error("APK 版本与 Release 标签不一致，请检查发布附件")
                }
                candidate
            }
        } finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { file.delete() } }
    }
    fun install(candidate: UpdateCandidate): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", candidate.file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        return true
    }
}
