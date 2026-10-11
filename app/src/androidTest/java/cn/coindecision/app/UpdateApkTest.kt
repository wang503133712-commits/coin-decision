package cn.coindecision.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.coindecision.app.data.ApkUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpdateApkTest {
    @Test fun acceptsRealSignedHigherVersionApk() = runBlocking {
        assumeTrue("此用例在安装旧版后以 updatePhase=apk 单独执行", InstrumentationRegistry.getArguments().getString("updatePhase") == "apk")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtureDir = if (InstrumentationRegistry.getArguments().getString("fixtureLocation") == "external")
            requireNotNull(context.externalCacheDir) else context.filesDir
        val fixture = File(fixtureDir, "update-fixture.apk")
        assumeTrue("单独执行更新包验证时需先复制当前新版的同签名 APK", fixture.exists())
        val candidate = ApkUpdater(context).inspect(Uri.fromFile(fixture))
        assertEquals(BuildConfig.VERSION_CODE.toLong(), candidate.versionCode)
        assertEquals(BuildConfig.VERSION_NAME, candidate.versionName)
        assertTrue(candidate.file.exists())
    }
}
