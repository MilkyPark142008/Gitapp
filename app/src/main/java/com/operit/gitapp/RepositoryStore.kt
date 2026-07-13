package com.operit.gitapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LocalRepository(val name: String, val directory: String, val remoteUrl: String = "")

class RepositoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("repositories", Context.MODE_PRIVATE)
    fun all(): List<LocalRepository> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        List(array.length()) { i -> array.getJSONObject(i).let { LocalRepository(it.getString("name"), it.getString("directory"), it.optString("remote")) } }
    }.getOrDefault(emptyList())
    fun add(repo: LocalRepository) {
        val repos = all().filterNot { it.directory == repo.directory } + repo
        val arr = JSONArray(); repos.forEach { arr.put(JSONObject().put("name", it.name).put("directory", it.directory).put("remote", it.remoteUrl)) }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
