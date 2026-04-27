package com.wordhint.teleprompter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptStoreTest {
    @Test
    fun titleFromContentUsesFirstNonBlankLineAndTrimsLongTitles() {
        val title = ScriptTitles.fromContent(
            "\n  这是一个很长很长的中文口播标题，用来测试自动标题是否会被安全截断，避免列表显示过长\n第二行"
        )

        assertEquals("这是一个很长很长的中文口播标题，用来测试自动标题是否会被安全截断，避免列表显示过长".take(28), title)
    }

    @Test
    fun jsonRoundTripPreservesScriptSettings() {
        val original = listOf(
            Script(
                id = "script-1",
                title = "中文口播",
                content = "大家好\nHello",
                updatedAt = 123L,
                speed = 42,
                fontSize = 36,
                orientation = ScriptOrientation.LANDSCAPE,
                textColor = 0xFFFFF2D8.toInt(),
                backgroundColor = 0xFF101010.toInt()
            )
        )

        val decoded = ScriptJson.decodeList(ScriptJson.encodeList(original))

        assertEquals(original, decoded)
    }

    @Test
    fun settingRangesClampUnsafeValues() {
        assertEquals(10, ScriptSettings.clampSpeed(-5))
        assertEquals(360, ScriptSettings.clampSpeed(999))
        assertEquals(18, ScriptSettings.clampFontSize(1))
        assertEquals(72, ScriptSettings.clampFontSize(120))
    }

    @Test
    fun responsivePromptPaddingKeepsLandscapeContentAreaLarge() {
        val padding = PromptViewportPadding.calculate(widthPx = 2400, heightPx = 1080, density = 3f)

        assertTrue(padding.left <= 72)
        assertTrue(padding.right <= 72)
        assertTrue(padding.top <= 108)
        assertTrue(padding.bottom <= 216)
        assertTrue(1080 - padding.top - padding.bottom >= 756)
    }

    @Test
    fun emptyJsonFallsBackToNoScripts() {
        assertTrue(ScriptJson.decodeList("").isEmpty())
        assertTrue(ScriptJson.decodeList("not-json").isEmpty())
    }

    @Test
    fun legacyJsonWithoutColorsUsesReadableDefaults() {
        val decoded = ScriptJson.decodeList(
            """
            [{"id":"old","title":"旧稿件","content":"内容","updatedAt":1,"speed":36,"fontSize":34,"orientation":"PORTRAIT"}]
            """.trimIndent()
        )

        assertEquals(ScriptSettings.DEFAULT_TEXT_COLOR, decoded.single().textColor)
        assertEquals(ScriptSettings.DEFAULT_BACKGROUND_COLOR, decoded.single().backgroundColor)
    }
}
