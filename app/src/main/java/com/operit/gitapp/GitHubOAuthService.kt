package com.operit.gitapp

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GitHubOAuthException(message: String) : IllegalStateException(message)

object GitHubOAuthService {
    private const val AUTH_URL = "https://github.com/login/oauth/authorize"
    private const val TOKEN_URL = "https://github.com/login/oauth/access_token"
    private const val REDIRECT_URI = "gitapp://oauth"

    data class AuthorizationStart(
        val authorizationUrl: Uri,
        val session: OAuthSession
    )

    fun beginAuthorization(context: Context): AuthorizationStart {
        val clientId = context.getString(R.string.github_client_id).trim()
        if (clientId.isBlank() || clientId.startsWith("REPLACE_WITH_")) {
            throw GitHubOAuthException("请先配置 GitHub OAuth Client ID")
        }

        val session = OAuthSession(
            state = Pkce.generateState(),
            codeVerifier = Pkce.generateVerifier()
        )
        OAuthSessionStore(context).save(session)

        val authorizationUrl = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", "repo read:user")
            .appendQueryParameter("state", session.state)
            .appendQueryParameter("code_challenge", Pkce.challengeFromVerifier(session.codeVerifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        return AuthorizationStart(authorizationUrl, session)
    }

    fun exchangeCode(context: Context, code: String, state: String): String {
        val clientId = context.getString(R.string.github_client_id).trim()
        if (clientId.isBlank() || clientId.startsWith("REPLACE_WITH_")) {
            throw GitHubOAuthException("请先配置 GitHub OAuth Client ID")
        }

        val session = OAuthSessionStore(context).requireState(state)
        val params = linkedMapOf(
            "client_id" to clientId,
            "code" to code,
            "redirect_uri" to REDIRECT_URI,
            "code_verifier" to session.codeVerifier
        )

        val response = postForm(TOKEN_URL, params)
        val json = JSONObject(response)
        val error = json.optString("error")
        if (error.isNotBlank()) {
            throw GitHubOAuthException(json.optString("error_description", error))
        }
        val token = json.optString("access_token")
        if (token.isBlank()) {
            throw GitHubOAuthException("GitHub 未返回 access_token")
        }
        OAuthSessionStore(context).clear()
        return token
    }

    private fun postForm(url: String, params: Map<String, String>): String {
        val body = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connectTimeout = 15000
            readTimeout = 15000
        }
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        if (connection.responseCode !in 200..299) {
            throw GitHubOAuthException("GitHub 授权失败：${connection.responseCode} $response")
        }
        return response
    }
}
