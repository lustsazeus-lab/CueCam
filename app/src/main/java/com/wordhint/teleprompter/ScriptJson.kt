package com.wordhint.teleprompter

import org.json.JSONArray
import org.json.JSONObject

object ScriptJson {
    fun encodeList(scripts: List<Script>): String {
        val array = JSONArray()
        scripts.forEach { script ->
            array.put(
                JSONObject()
                    .put("id", script.id)
                    .put("title", script.title)
                    .put("content", script.content)
                    .put("updatedAt", script.updatedAt)
                    .put("speed", script.speed)
                    .put("fontSize", script.fontSize)
                    .put("orientation", script.orientation.name)
                    .put("textColor", script.textColor)
                    .put("backgroundColor", script.backgroundColor)
            )
        }
        return array.toString()
    }

    fun decodeList(json: String): List<Script> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        Script(
                            id = item.optString("id"),
                            title = item.optString("title", ScriptTitles.fromContent(item.optString("content"))),
                            content = item.optString("content"),
                            updatedAt = item.optLong("updatedAt"),
                            speed = ScriptSettings.clampSpeed(item.optInt("speed", ScriptSettings.DEFAULT_SPEED)),
                            fontSize = ScriptSettings.clampFontSize(
                                item.optInt("fontSize", ScriptSettings.DEFAULT_FONT_SIZE)
                            ),
                            orientation = ScriptOrientation.from(item.optString("orientation")),
                            textColor = item.optInt("textColor", ScriptSettings.DEFAULT_TEXT_COLOR),
                            backgroundColor = item.optInt(
                                "backgroundColor",
                                ScriptSettings.DEFAULT_BACKGROUND_COLOR
                            )
                        )
                    )
                }
            }.filter { it.id.isNotBlank() }
        }.getOrDefault(emptyList())
    }
}
