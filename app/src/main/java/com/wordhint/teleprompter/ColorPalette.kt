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
        ColorOption(R.string.white, Color.WHITE),
        ColorOption(R.string.warm_white, 0xFFFFF2D8.toInt()),
        ColorOption(R.string.soft_yellow, 0xFFFFE878.toInt()),
        ColorOption(R.string.mint, 0xFFB9FBC0.toInt()),
        ColorOption(R.string.sky_blue, 0xFFA9DEF9.toInt()),
        ColorOption(R.string.pink, 0xFFFFC8DD.toInt()),
        ColorOption(R.string.black, Color.BLACK),
        ColorOption(R.string.dark_gray, 0xFF303030.toInt())
    )

    private val backgroundColors = listOf(
        ColorOption(R.string.black, Color.BLACK),
        ColorOption(R.string.dark_gray, 0xFF111827.toInt()),
        ColorOption(R.string.deep_green, 0xFF10251C.toInt()),
        ColorOption(R.string.deep_blue, 0xFF101A33.toInt()),
        ColorOption(R.string.coffee, 0xFF2A1E17.toInt()),
        ColorOption(R.string.warm_cream, 0xFFFFF9F2.toInt()),
        ColorOption(R.string.white, Color.WHITE),
        ColorOption(R.string.soft_yellow, 0xFFFFF3B0.toInt())
    )

    fun showTextColorPicker(context: Context, selectedColor: Int, onSelected: (Int) -> Unit) {
        showPicker(context, context.getString(R.string.text_color), textColors, selectedColor, onSelected)
    }

    fun showBackgroundColorPicker(context: Context, selectedColor: Int, onSelected: (Int) -> Unit) {
        showPicker(context, context.getString(R.string.background_color), backgroundColors, selectedColor, onSelected)
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
            .setNegativeButton(R.string.cancel, null)
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
                    text = context.getString(option.labelRes)
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

    private data class ColorOption(val labelRes: Int, val color: Int)
}
