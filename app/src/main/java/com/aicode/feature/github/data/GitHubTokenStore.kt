package com.aicode.feature.github.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub Personal Access Token 持久化。
 * 用 EncryptedSharedPreferences 加密存储，防止 root 下直接读出 token。
 * AndroidKeystore 生成的主密钥不在 SharedPreferences 里，卸载 App 才会丢。
 */
@Singleton
class GitHubTokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "github_token_store",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _token = MutableStateFlow<String?>(prefs.getString(KEY_TOKEN, null))
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _username = MutableStateFlow<String?>(prefs.getString(KEY_USERNAME, null))
    val username: StateFlow<String?> = _username.asStateFlow()

    fun save(token: String, username: String?) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .apply()
        _token.value = token
        _username.value = username
    }

    fun clear() {
        prefs.edit().clear().apply()
        _token.value = null
        _username.value = null
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USERNAME = "username"
    }
}
