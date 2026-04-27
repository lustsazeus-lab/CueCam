package com.wordhint.teleprompter

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

object ColorPalette {
    private val textColors = listOf(
        ColorOption("白色", Color.WHITE),
        ColorOption("暖白", 0xFFFFF2D8.toInt()),
        ColorOption("浅黄", 0xFFFFE878.toInt()),
        ColorOption("薄荷", 0xFFB9FBC0.toInt()),
        ColorOption("天蓝", 0xFFA9DEF9.toInt()),
        ColorOption("粉色", 0xFFFFC8DD.toInt()),
        ColorOption("黑色", Color.BLACK),
        ColorOption("深灰", 0xFF303030.toInt())
    )

    private val backgroundColors = listOf(
        ColorOption("黑色", Color.BLACK),
        ColorOption("深灰", 0xFF111827.toInt()),
        ColorOption("墨绿", 0xFF10251C.toInt()),
        ColorOption("深蓝", 0xFF101A33.toInt()),
        ColorOption("咖啡", 0xFF2A1E17.toInt()),
        ColorOption("暖米", 0xFFFFF9F2.toInt()),
        ColorOption("白色", Color.WHITE),
        ColorOption("浅黄", 0xFFFFF3B0.toInt())
    )

    fun showTextColorPicker(context: Context, selectedColor: Int, onSelected: (Int) -> Unit) {
        showPicker(context, "字体颜色", textColors, selectedColor, onSelected)
    }

    fun showBackgroundColorPicker(context: Context, selectedColor: Int, onSelected: (Int) -> Unit) {
        showPicker(context, "背景颜色", backgroundColors, selectedColor, onSelected)
    }

    private fun showPicker(
        context: Context,
        title: String,
        options: List<ColorOption>,
        selectedColor: Int,
        onSelected: (Int) -> Unit
    ) {
        val grid = GridLayout(context).apply {
            columnCount = 4
            setPadding(24.dp(context), 12.dp(context), 24.dp(context), 8.dp(context))
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(grid)
            .setNegativeButton("取消", null)
            .create()

        options.forEach { option ->
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(8.dp(context), 8.dp(context), 8.dp(context), 8.dp(context))
                setOnClickListener {
                    onSelected(option.color)
                    dialog.dismiss()
                }
            }
            val swatch = View(context).apply {
                background = swatchDrawable(option.color, option.color == selectedColor)
            }
            item.addView(
                swatch,
                LinearLayout.LayoutParams(44.dp(context), 44.dp(context))
            )
            item.addView(
                TextView(context).apply {
                    text = option.label
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF333333.toInt())
                },
                LinearLayout.LayoutParams(72.dp(context), LinearLayout.LayoutParams.WRAP_CONTENT)
            )
            grid.addView(item)
        }

        dialog.show()
    }

    private fun swatchDrawable(color: Int, selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f
            setColor(color)
            setStroke(if (selected) 4 else 1, if (selected) 0xFFFF8E7A.toInt() else 0xFFB8B8B8.toInt())
        }

    private fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()

    private data class ColorOption(val label: String, val color: Int)
}
