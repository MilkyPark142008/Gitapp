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
        val clientSecret = runCatching {
            context.getString(R.string.github_client_secret).trim()
        }.getOrDefault("")

        val params = linkedMapOf(
            "client_id" to clientId,
            "code" to code,
            "redirect_uri" to REDIRECT_URI,
            "code_verifier" to session.codeVerifier
        )

        // GitHub OAuth + PKCE 通常不需要 client_secret。
        // 如果你的 OAuth App 返回 incorrect_client_credentials，
        // 在 strings.xml 填 github_client_secret 后这里会自动带上。
        if (clientSecret.isNotBlank() && !clientSecret.startsWith("REPLACE_WITH_")) {
            params["client_secret"] = clientSecret
        }

        val response = postForm(TOKEN_URL, params)
        val json = JSONObject(response)
        val error = json.optString("error")
        if (error.isNotBlank()) {
            val description = json.optString("error_description", error)
            throw GitHubOAuthException(toFriendlyOAuthError(error, description, clientSecret))
        }

        val token = json.optString("access_token")
        if (token.isBlank()) {
            throw GitHubOAuthException("GitHub 未返回 access_token，响应内容：$response")
        }

        OAuthSessionStore(context).clear()
        return token
    }

    private fun toFriendlyOAuthError(error: String, description: String, clientSecret: String): String = when (error) {
        "incorrect_client_credentials" -> {
            val secretTip = if (clientSecret.isBlank() || clientSecret.startsWith("REPLACE_WITH_")) {
                "当前没有配置 github_client_secret。如果你的 GitHub OAuth App 不支持纯 PKCE，请在 res/values/strings.xml 填入同一个 OAuth App 的 Client secret。"
            } else {
                "当前已配置 github_client_secret，请确认它和 github_client_id 来自同一个 GitHub OAuth App。"
            }
            "GitHub OAuth 凭据不正确。\n\n$secretTip\n\n" +
                "同时确认 GitHub 后台 Authorization callback URL 必须完全等于：$REDIRECT_URI\n\n" +
                "GitHub 返回：$description"
        }

        "bad_verification_code" ->
            "GitHub 授权码无效、已过期或已被使用。\n\n请返回首页重新点击“使用 GitHub 登录”，不要重复打开旧回调链接。\n\nGitHub 返回：$description"

        "redirect_uri_mismatch" ->
            "GitHub OAuth 回调地址不匹配。\n\n请到 GitHub OAuth App 设置中把 Authorization callback URL 改为：$REDIRECT_URI\n\nGitHub 返回：$description"

        else -> "GitHub 授权失败：$description"
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

        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val response = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

        if (connection.responseCode !in 200..299) {
            throw GitHubOAuthException("GitHub 授权失败：${connection.responseCode} $response")
        }

        return response
    }
}
