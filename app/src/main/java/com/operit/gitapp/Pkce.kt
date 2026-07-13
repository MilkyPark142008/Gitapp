package com.operit.gitapp

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object Pkce {
    private val random = SecureRandom()

    fun generateVerifier(byteCount: Int = 64): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun generateState(): String = generateVerifier(32)

    fun challengeFromVerifier(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
