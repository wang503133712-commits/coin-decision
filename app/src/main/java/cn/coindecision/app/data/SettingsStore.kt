package cn.coindecision.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import cn.coindecision.app.domain.AiConfig
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")
const val DEFAULT_GITHUB_REPOSITORY = "wang503133712-commits/coin-decision"
data class AppSettings(val online: Boolean = true, val config: AiConfig = AiConfig(), val vibration: Boolean = true, val sound: Boolean = true,
    val githubRepository: String = DEFAULT_GITHUB_REPOSITORY)
class SettingsStore(private val context: Context) {
    // 旧版默认离线；新版优先 AI，旧接口配置与密钥仍保留。
    private val online = booleanPreferencesKey("ai_questions_preferred")
    private val url = stringPreferencesKey("base_url")
    private val model = stringPreferencesKey("model")
    private val vibration = booleanPreferencesKey("vibration")
    private val sound = booleanPreferencesKey("sound")
    private val githubRepo = stringPreferencesKey("github_repository")
    val flow = context.settingsDataStore.data.map { p ->
        AppSettings(p[online] ?: true, AiConfig(p[url] ?: "https://api.deepseek.com", p[model] ?: "deepseek-flash"), p[vibration] ?: true, p[sound] ?: true,
            p[githubRepo]?.takeIf { it.isNotBlank() } ?: DEFAULT_GITHUB_REPOSITORY)
    }
    suspend fun save(value: AppSettings) = context.settingsDataStore.edit { p ->
        p[online] = value.online; p[url] = value.config.baseUrl; p[model] = value.config.model; p[vibration] = value.vibration
        p[sound] = value.sound
    }
    suspend fun saveGithubRepository(value: String) = context.settingsDataStore.edit { p ->
        p[githubRepo] = if (value.isBlank()) "" else githubRepository(value)
    }
}
