package com.wordhint.teleprompter

data class PromptPadding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

object PromptViewportPadding {
    fun calculate(widthPx: Int, heightPx: Int, density: Float): PromptPadding {
        val shortSide = minOf(widthPx, heightPx)
        val minSidePadding = (12 * density).toInt()
        val maxSidePadding = (24 * density).toInt()
        val sidePadding = (shortSide * 0.04f).toInt().coerceIn(minSidePadding, maxSidePadding)

        val topPadding = (heightPx * 0.05f).toInt()
            .coerceIn((12 * density).toInt(), (36 * density).toInt())
        val bottomPadding = (heightPx * 0.10f).toInt()
            .coerceIn((28 * density).toInt(), (72 * density).toInt())

        return PromptPadding(
            left = sidePadding,
            top = topPadding,
            right = sidePadding,
            bottom = bottomPadding
        )
    }
}
