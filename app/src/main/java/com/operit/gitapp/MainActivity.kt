package com.operit.gitapp

import android.content.Intent
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
            showLoginDialog()
        }
    }

    private fun showLoginDialog() {
        AlertDialog.Builder(this)
            .setTitle("连接 GitHub")
            .setMessage("请选择一种登录方式。Gitapp 不会保存你的 GitHub 密码，令牌仅加密保存在本机。")
            .setPositiveButton("使用 GitHub 登录") { _, _ -> startBrowserOAuth() }
            .setNeutralButton("使用 PAT 登录") { _, _ -> tokenInputDialog() }
            .setNegativeButton("查看教程") { _, _ -> showPatTutorial() }
            .show()
    }

    private fun startBrowserOAuth() {
        runCatching {
            val start = GitHubOAuthService.beginAuthorization(this)
            startActivity(Intent(Intent.ACTION_VIEW, start.authorizationUrl))
        }.onFailure {
            AlertDialog.Builder(this)
                .setTitle("无法开始 GitHub 登录")
                .setMessage(it.message ?: "未知错误")
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun showPatTutorial() {
        AlertDialog.Builder(this)
            .setTitle("GitHub PAT 教程")
            .setMessage(
                "1. 打开 GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens\n" +
                "2. 点击 Generate new token\n" +
                "3. 选择你的仓库或仅允许指定仓库\n" +
                "4. Repository permissions 里把 Contents 设为 Read and write\n" +
                "5. 如果需要读取仓库信息，可保留 Metadata 只读\n" +
                "6. 复制生成的 token，返回 App 粘贴保存\n\n" +
                "注意：PAT 只会加密保存在本机，不会上传到任何服务器。"
            )
            .setPositiveButton("知道了", null)
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