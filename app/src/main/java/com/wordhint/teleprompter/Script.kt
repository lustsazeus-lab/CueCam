package com.wordhint.teleprompter

enum class ScriptOrientation {
    PORTRAIT,
    LANDSCAPE;

    companion object {
        fun from(value: String?): ScriptOrientation =
            entries.firstOrNull { it.name == value } ?: PORTRAIT
    }
}

data class Script(
    val id: String,
    val title: String,
    val content: String,
    val updatedAt: Long,
    val speed: Int = ScriptSettings.DEFAULT_SPEED,
    val fontSize: Int = ScriptSettings.DEFAULT_FONT_SIZE,
    val orientation: ScriptOrientation = ScriptOrientation.PORTRAIT,
    val textColor: Int = ScriptSettings.DEFAULT_TEXT_COLOR,
    val backgroundColor: Int = ScriptSettings.DEFAULT_BACKGROUND_COLOR
)

object ScriptSettings {
    const val MIN_SPEED = 10
    const val MAX_SPEED = 360
    const val DEFAULT_SPEED = 36

    const val MIN_FONT_SIZE = 18
    const val MAX_FONT_SIZE = 72
    const val DEFAULT_FONT_SIZE = 34

    const val DEFAULT_TEXT_COLOR = -1
    const val DEFAULT_BACKGROUND_COLOR = -16777216

    fun clampSpeed(value: Int): Int = value.coerceIn(MIN_SPEED, MAX_SPEED)

    fun clampFontSize(value: Int): Int = value.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
}

object ScriptTitles {
    private const val MAX_TITLE_LENGTH = 28
    private const val FALLBACK_TITLE = "未命名稿件"

    fun fromContent(content: String): String {
        val firstLine = content
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: FALLBACK_TITLE
        return firstLine.take(MAX_TITLE_LENGTH)
    }
}
