package cn.coindecision.app

import android.app.Application
import cn.coindecision.app.data.*

class CoinApplication : Application() {
    val database by lazy { CoinDatabase.open(this) }
    val repository by lazy { CoinRepository(database) }
    val settings by lazy { SettingsStore(this) }
    val vault by lazy { KeyVault(this) }
    val systemPrompt by lazy { assets.open("deepseek-system-v2.txt").bufferedReader().use { it.readText() } }
}
