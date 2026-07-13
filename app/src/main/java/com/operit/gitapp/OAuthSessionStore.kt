package com.operit.gitapp

import android.content.Context

data class OAuthSession(
    val state: String,
    val codeVerifier: String,
    val createdAt: Long = System.currentTimeMillis()
)

class OAuthSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("oauth_session", Context.MODE_PRIVATE)

    fun save(session: OAuthSession) {
        prefs.edit()
            .putString(KEY_STATE, session.state)
            .putString(KEY_VERIFIER, session.codeVerifier)
            .putLong(KEY_CREATED_AT, session.createdAt)
            .apply()
    }

    fun current(): OAuthSession? {
        val state = prefs.getString(KEY_STATE, null) ?: return null
        val verifier = prefs.getString(KEY_VERIFIER, null) ?: return null
        val createdAt = prefs.getLong(KEY_CREATED_AT, 0L)
        return OAuthSession(state, verifier, createdAt)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun requireState(expected: String): OAuthSession {
        val session = current() ?: throw IllegalStateException("OAuth 会话已过期，请重新登录")
        if (session.state != expected) throw IllegalStateException("OAuth state 校验失败")
        return session
    }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_VERIFIER = "verifier"
        const val KEY_CREATED_AT = "created_at"
    }
}
