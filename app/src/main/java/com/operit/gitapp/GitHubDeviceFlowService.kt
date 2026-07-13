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

class GitHubDeviceFlowException(message: String) : IllegalStateException(message)

data class GitHubDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int
) {
    val verificationUriAsUri: Uri get() = Uri.parse(verificationUri)
}

object GitHubDeviceFlowService {
    private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val TOKEN_URL = "https://github.com/login/oauth/access_token"

    fun requestDeviceCode(context: Context): GitHubDeviceCode {
        val clientId = clientId(context)
        val json = postForm(
            DEVICE_CODE_URL,
            linkedMapOf(
                "client_id" to clientId,
                "scope" to "repo read:user"
            )
        )
        val error = json.optString("error")
        if (error.isNotBlank()) {
            throw GitHubDeviceFlowException(json.optString("error_description", error))
        }
        return GitHubDeviceCode(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUri = json.getString("verification_uri"),
            expiresIn = json.optInt("expires_in", 900),
            interval = json.optInt("interval", 5)
        )
    }

    fun pollForToken(context: Context, deviceCode: GitHubDeviceCode, onWaiting: (String) -> Unit = {}): String {
        val clientId = clientId(context)
        val startedAt = System.currentTimeMillis()
        var intervalSeconds = deviceCode.interval.coerceAtLeast(5)

        while (System.currentTimeMillis() - startedAt < deviceCode.expiresIn * 1000L) {
            Thread.sleep(intervalSeconds * 1000L)
            val json = postForm(
                TOKEN_URL,
                linkedMapOf(
                    "client_id" to clientId,
                    "device_code" to deviceCode.deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
                )
            )

            val token = json.optString("access_token")
            if (token.isNotBlank()) return token

            when (val error = json.optString("error")) {
                "authorization_pending" -> onWaiting("等待你在 GitHub 完成授权…")
                "slow_down" -> {
                    intervalSeconds += 5
                    onWaiting("GitHub 要求降低轮询频率，继续等待…")
                }
                "expired_token" -> throw GitHubDeviceFlowException("设备码已过期，请重新登录")
                "access_denied" -> throw GitHubDeviceFlowException("你取消了 GitHub 授权")
                else -> if (error.isNotBlank()) throw GitHubDeviceFlowException(json.optString("error_description", error))
            }
        }
        throw GitHubDeviceFlowException("设备码已过期，请重新登录")
    }

    private fun clientId(context: Context): String {
        val clientId = context.getString(R.string.github_client_id).trim()
        if (clientId.isBlank() || clientId.startsWith("REPLACE_WITH_")) {
            throw GitHubDeviceFlowException("请先配置 GitHub OAuth Client ID")
        }
        return clientId
    }

    private fun postForm(url: String, params: Map<String, String>): JSONObject {
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
        val json = JSONObject(response)
        if (connection.responseCode !in 200..299) {
            val error = json.optString("error_description", response)
            throw GitHubDeviceFlowException("GitHub 设备码登录失败：${connection.responseCode} $error")
        }
        return json
    }
}