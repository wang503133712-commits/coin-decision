package cn.coindecision.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import cn.coindecision.app.ai.validatedBaseUrl
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyVault(context: Context) {
    private val prefs = context.getSharedPreferences("private_key_ciphertext", Context.MODE_PRIVATE)
    private val alias = "coin_personal_api_aes_v1"
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "设备加密密钥已丢失，请重新保存 API Key" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    fun hasKey(base: String): Boolean = prefs.contains("ciphertext") && prefs.getString("target", null) == validatedBaseUrl(base).toString()
    @Synchronized fun save(base: String, apiKey: String) {
        require(apiKey.isNotBlank() && apiKey.length <= 4096 && !apiKey.contains('\n') && !apiKey.contains('\r')) { "Key 为空或格式不合法" }
        val target = validatedBaseUrl(base).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(true)) }
        val ciphertext = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("target", target).putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP)).commit()) { "Key 保存失败" }
    }
    @Synchronized fun read(base: String): String {
        if (!hasKey(base)) return ""
        return try {
            val iv = Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)
            val encrypted = Base64.decode(prefs.getString("ciphertext", ""), Base64.NO_WRAP)
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, iv)) }
                .doFinal(encrypted).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            delete()
            throw IllegalStateException("保存的 Key 无法解密，已清除，请重新输入")
        }
    }
    @Synchronized fun delete() {
        check(prefs.edit().clear().commit()) { "删除 Key 失败" }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }
}
