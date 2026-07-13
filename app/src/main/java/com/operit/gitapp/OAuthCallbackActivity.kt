package com.operit.gitapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class OAuthCallbackActivity : AppCompatActivity() {
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data
        if (data == null) {
            finishWithMessage("未收到 GitHub 回调")
            return
        }
        val error = data.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            finishWithMessage(data.getQueryParameter("error_description") ?: error)
            return
        }
        val code = data.getQueryParameter("code")
        val state = data.getQueryParameter("state")
        if (code.isNullOrBlank() || state.isNullOrBlank()) {
            finishWithMessage("回调参数不完整")
            return
        }

        worker.execute {
            runCatching {
                val token = GitHubOAuthService.exchangeCode(this, code, state)
                SecureTokenStore(this).save(token)
            }.onSuccess {
                runOnUiThread {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    finish()
                }
            }.onFailure { finishWithMessage(it.message ?: "授权失败") }
        }
    }

    private fun finishWithMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        }
    }
}
