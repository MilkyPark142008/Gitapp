package com.operit.gitapp

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Stores the GitHub access token only in encrypted app-private storage. */
class SecureTokenStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context, "github_secrets",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun token(): String? = preferences.getString(KEY_TOKEN, null)
    fun save(token: String) = preferences.edit().putString(KEY_TOKEN, token.trim()).apply()
    fun clear() = preferences.edit().clear().apply()
    private companion object { const val KEY_TOKEN = "github_access_token" }
}
