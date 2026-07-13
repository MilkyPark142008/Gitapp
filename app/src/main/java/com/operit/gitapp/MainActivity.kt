package com.operit.gitapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var tokenStore: SecureTokenStore
    private lateinit var repositoryStore: RepositoryStore
    private lateinit var content: LinearLayout
    private val worker = Executors.newSingleThreadExecutor()
    private val padding by lazy { (20 * resources.displayMetrics.density).toInt() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStore = SecureTokenStore(this)
        repositoryStore = RepositoryStore(this)
        showHome()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) showHome()
    }

    private fun showHome() {
        val root = FrameLayout(this).apply { setBackgroundColor(0xfff6f8fa.toInt()) }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(MaterialToolbar(this).apply {
            title = "Gitapp"
            subtitle = if (tokenStore.token() == null) "未连接 GitHub" else "GitHub 已连接"
            setOnClickListener { showAccountDialog() }
        }, LinearLayout.LayoutParams(-1, (64 * resources.displayMetrics.density).toInt()))
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        page.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(page)
        root.addView(
            FloatingActionButton(this).apply {
                setImageResource(android.R.drawable.ic_input_add)
                setOnClickListener { showAddDialog() }
            },
            FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).apply { setMargins(0, 0, padding, padding) }
        )
        setContentView(root)
        renderRepositories()
    }

    private fun renderRepositories() {
        content.removeAllViews()
        content.addView(TextView(this).apply {
            text = "本地仓库"
            textSize = 24f
            setTextColor(0xff1f2328.toInt())
        })
        content.addView(TextView(this).apply {
            text = "克隆、拉取和推送你的 GitHub 项目"
            textSize = 14f
            setPadding(0, 8, 0, padding)
        })
        val repos = repositoryStore.all()
        if (repos.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "还没有仓库\n点击右下角 +，从 GitHub 克隆一个仓库。"
                textSize = 16f
                setPadding(0, padding * 2, 0, 0)
            })
        }
        repos.forEach { repo ->
            content.addView(repositoryCard(repo), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        }
    }

    private fun repositoryCard(repo: LocalRepository): View = MaterialCardView(this).apply {
        radius = 18f
        setCardBackgroundColor(0xffffffff.toInt())
        setContentPadding(padding, padding, padding, padding)
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = repo.name
                textSize = 19f
                setTextColor(0xff0969da.toInt())
            })
            addView(TextView(this@MainActivity).apply {
                text = repo.remoteUrl.ifBlank { repo.directory }
                textSize = 12f
                setPadding(0, 8, 0, 12)
            })
            addView(LinearLayout(this@MainActivity).apply {
                addView(MaterialButton(this@MainActivity).apply {
                    text = "拉取"
                    setOnClickListener { runGit(repo, false) }
                })
                addView(MaterialButton(this@MainActivity).apply {
                    text = "推送"
                    setOnClickListener { runGit(repo, true) }
                })
            })
        })
    }

    private fun showAccountDialog() {
        val token = tokenStore.token()
        if (token != null) {
            AlertDialog.Builder(this)
                .setTitle("GitHub 已连接")
                .setMessage("访问令牌仅以 Android Keystore 加密形式保存在本机应用私有存储中，不会上传到本应用的服务器。")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出登录") { _, _ ->
                    tokenStore.clear()
                    showHome()
                }
                .show()
        } else {
            showLoginChoices()
        }
    }
    private fun showLoginChoices() {
        AlertDialog.Builder(this)
            .setTitle("连接 GitHub")
            .setMessage("推荐使用 GitHub 设备码登录。Gitapp 不会保存你的 GitHub 密码，令牌仅加密保存在本机。\n\n注意：请在 GitHub OAuth App 设置里启用 Enable Device Flow。")
            .setPositiveButton("使用 GitHub 登录") { _, _ -> startDeviceFlowLogin() }
            .setNeutralButton("使用 Token") { _, _ -> tokenInputDialog() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startDeviceFlowLogin() {
        progress("正在向 GitHub 请求设备码…")
        worker.execute {
            runCatching {
                val deviceCode = GitHubDeviceFlowService.requestDeviceCode(this)
                runOnUiThread { showDeviceCodeDialog(deviceCode) }
                GitHubDeviceFlowService.pollForToken(this, deviceCode) { message ->
                    runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
                }
            }.onSuccess { token ->
                tokenStore.save(token)
                done("GitHub 登录成功")
            }.onFailure { error ->
                done("登录失败：${error.message}")
            }
        }
    }

    private fun showDeviceCodeDialog(deviceCode: GitHubDeviceCode) {
        AlertDialog.Builder(this)
            .setTitle("GitHub 设备码登录")
            .setMessage("请在 GitHub 页面输入验证码：\n\n${deviceCode.userCode}\n\nApp 会在你授权后自动完成登录。")
            .setPositiveButton("打开 GitHub") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, deviceCode.verificationUriAsUri))
            }
            .setNeutralButton("复制验证码", null)
            .setNegativeButton("关闭", null)
            .show()
    }


    private fun tokenInputDialog() {
        val input = EditText(this).apply {
            hint = "github_pat_..."
            setSingleLine(false)
        }
        AlertDialog.Builder(this)
            .setTitle("Personal Access Token")
            .setMessage("高级登录方式。请粘贴拥有目标仓库 Contents: Read and write 权限的 GitHub Token。令牌只加密保存在本机。")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("安全保存") { _, _ ->
                if (input.text.isNullOrBlank()) toast("请输入令牌") else {
                    tokenStore.save(input.text.toString())
                    showHome()
                }
            }
            .show()
    }

    private fun showAddDialog() {
        if (tokenStore.token() == null) {
            toast("请先点击顶部 Gitapp 连接 GitHub")
            return
        }
        val url = EditText(this).apply { hint = "https://github.com/用户名/仓库.git" }
        AlertDialog.Builder(this)
            .setTitle("克隆 GitHub 仓库")
            .setMessage("仓库将克隆到 App 的本地私有工作区。")
            .setView(url)
            .setNegativeButton("取消", null)
            .setPositiveButton("克隆") { _, _ -> clone(url.text.toString()) }
            .show()
    }

    private fun clone(url: String) {
        if (!url.startsWith("https://github.com/") || !url.endsWith(".git")) {
            toast("请输入有效的 GitHub HTTPS 仓库地址")
            return
        }
        val name = url.substringAfterLast('/').removeSuffix(".git")
        val target = File(filesDir, "repositories/$name")
        if (target.exists()) {
            toast("此仓库已存在")
            return
        }
        progress("正在克隆 $name…")
        worker.execute {
            runCatching { GitService.clone(url, target, tokenStore.token()!!) }
                .onSuccess { repositoryStore.add(it); done("克隆完成") }
                .onFailure { done("失败：${it.message}") }
        }
    }

    private fun runGit(repo: LocalRepository, push: Boolean) {
        val token = tokenStore.token() ?: run {
            toast("请先连接 GitHub")
            return
        }
        progress("正在${if (push) "推送" else "拉取"}…")
        worker.execute {
            runCatching {
                if (push) GitService.push(File(repo.directory), token) else GitService.pull(File(repo.directory), token)
            }.onSuccess { done(it) }.onFailure { done("失败：${it.message}") }
        }
    }

    private fun progress(message: String) = runOnUiThread {
        content.alpha = .45f
        toast(message)
    }

    private fun done(message: String) = runOnUiThread {
        content.alpha = 1f
        toast(message)
        renderRepositories()
    }

    private fun toast(message: String) = runOnUiThread {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}