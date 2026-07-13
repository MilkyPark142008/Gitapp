package com.operit.gitapp

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

object GitService {
    private fun credentials(token: String) = UsernamePasswordCredentialsProvider("oauth2", token)
    fun clone(remoteUrl: String, target: File, token: String): LocalRepository {
        Git.cloneRepository().setURI(remoteUrl).setDirectory(target).setCredentialsProvider(credentials(token)).call().close()
        return LocalRepository(target.name, target.absolutePath, remoteUrl)
    }
    fun pull(directory: File, token: String): String = Git.open(directory).use { git ->
        val result = git.pull().setCredentialsProvider(credentials(token)).call()
        if (result.isSuccessful) "拉取完成" else "拉取未完成：${result.mergeResult?.mergeStatus ?: "请检查冲突或远程配置"}"
    }
    fun push(directory: File, token: String): String = Git.open(directory).use { git ->
        git.push().setCredentialsProvider(credentials(token)).call().joinToString { it.messages.ifBlank { "推送完成" } }
    }
}
