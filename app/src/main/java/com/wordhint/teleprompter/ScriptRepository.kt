package com.wordhint.teleprompter

import android.content.Context
import java.util.UUID

class ScriptRepository(context: Context) {
    private val prefs = context.getSharedPreferences("scripts", Context.MODE_PRIVATE)

    fun loadScripts(): List<Script> =
        ScriptJson.decodeList(prefs.getString(KEY_SCRIPTS, "").orEmpty())
            .sortedByDescending { it.updatedAt }

    fun loadScript(id: String): Script? = loadScripts().firstOrNull { it.id == id }

    fun save(script: Script): Script {
        val normalized = script.copy(
            title = script.title.ifBlank { ScriptTitles.fromContent(script.content) },
            speed = ScriptSettings.clampSpeed(script.speed),
            fontSize = ScriptSettings.clampFontSize(script.fontSize),
            updatedAt = System.currentTimeMillis()
        )
        val next = loadScripts()
            .filterNot { it.id == normalized.id }
            .toMutableList()
            .apply { add(0, normalized) }
        prefs.edit().putString(KEY_SCRIPTS, ScriptJson.encodeList(next)).apply()
        return normalized
    }

    fun delete(id: String) {
        val next = loadScripts().filterNot { it.id == id }
        prefs.edit().putString(KEY_SCRIPTS, ScriptJson.encodeList(next)).apply()
    }

    fun createDraft(): Script =
        Script(
            id = UUID.randomUUID().toString(),
            title = ScriptTitles.FALLBACK_TITLE,
            content = "",
            updatedAt = System.currentTimeMillis()
        )

    companion object {
        private const val KEY_SCRIPTS = "items"
    }
}
