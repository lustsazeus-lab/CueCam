package com.wordhint.teleprompter

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class TeleprompterActivity : Activity() {
    private lateinit var repository: ScriptRepository
    private lateinit var scrollView: ScrollView
    private lateinit var prompterText: TextView
    private lateinit var controlPanel: LinearLayout
    private lateinit var playStateText: TextView
    private lateinit var speedLabel: TextView
    private lateinit var fontLabel: TextView
    private lateinit var speedSeek: SeekBar
    private lateinit var fontSeek: SeekBar
    private lateinit var playPauseButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var script: Script? = null
    private var paused = false
    private var downY = 0f
    private var downScrollY = 0
    private var dragging = false
    private var scrollRemainder = 0f

    private val scrollTick = object : Runnable {
        override fun run() {
            if (!paused) {
                scrollBySpeed()
            }
            handler.postDelayed(this, FRAME_MS)
        }
    }

    private val hideControlsRunnable = Runnable { controlPanel.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teleprompter)

        repository = ScriptRepository(this)
        bindViews()
        loadScript()
        setupControls()
        applyResponsivePromptPadding()
        enterFullScreen()
    }

    override fun onResume() {
        super.onResume()
        handler.post(scrollTick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(scrollTick)
        saveSettings()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun bindViews() {
        scrollView = findViewById(R.id.prompterScroll)
        prompterText = findViewById(R.id.prompterText)
        controlPanel = findViewById(R.id.controlPanel)
        playStateText = findViewById(R.id.playStateText)
        speedLabel = findViewById(R.id.playSpeedLabel)
        fontLabel = findViewById(R.id.playFontLabel)
        speedSeek = findViewById(R.id.playSpeedSeek)
        fontSeek = findViewById(R.id.playFontSeek)
        playPauseButton = findViewById(R.id.playPauseButton)
        findViewById<Button>(R.id.colorButton).setOnClickListener { showColorSettings() }
        findViewById<Button>(R.id.exitButton).setOnClickListener { finish() }
    }

    private fun loadScript() {
        val id = intent.getStringExtra(EXTRA_SCRIPT_ID).orEmpty()
        val loaded = repository.loadScript(id)
        if (loaded == null || loaded.content.isBlank()) {
            finish()
            return
        }
        script = loaded
        requestedOrientation = if (loaded.orientation == ScriptOrientation.LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        prompterText.text = loaded.content
        prompterText.textSize = loaded.fontSize.toFloat()
        applyColors(loaded.textColor, loaded.backgroundColor)
        speedSeek.max = ScriptSettings.MAX_SPEED - ScriptSettings.MIN_SPEED
        fontSeek.max = ScriptSettings.MAX_FONT_SIZE - ScriptSettings.MIN_FONT_SIZE
        speedSeek.progress = loaded.speed - ScriptSettings.MIN_SPEED
        fontSeek.progress = loaded.fontSize - ScriptSettings.MIN_FONT_SIZE
        updateControlLabels()
    }

    private fun setupControls() {
        scrollView.setOnTouchListener { _, event -> handlePromptTouch(event) }
        playPauseButton.setOnClickListener {
            paused = !paused
            updateControlLabels()
            showControls()
        }
        speedSeek.setOnSeekBarChangeListener(simpleSeekListener {
            updateControlLabels()
            showControls()
            saveSettings()
        })
        fontSeek.setOnSeekBarChangeListener(simpleSeekListener {
            prompterText.textSize = currentFontSize().toFloat()
            updateControlLabels()
            showControls()
            saveSettings()
        })
    }

    private fun applyResponsivePromptPadding() {
        scrollView.post {
            val padding = PromptViewportPadding.calculate(
                widthPx = scrollView.width,
                heightPx = scrollView.height,
                density = resources.displayMetrics.density
            )
            scrollView.setPadding(padding.left, padding.top, padding.right, padding.bottom)
        }
    }

    private fun handlePromptTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                downScrollY = scrollView.scrollY
                dragging = false
                handler.removeCallbacks(hideControlsRunnable)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = downY - event.y
                if (kotlin.math.abs(delta) > DRAG_THRESHOLD_PX) {
                    dragging = true
                    paused = true
                    scrollView.scrollTo(0, (downScrollY + delta.toInt()).coerceIn(0, maxScrollY()))
                    updateControlLabels()
                    showControls()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) {
                    paused = !paused
                    showControls()
                    updateControlLabels()
                } else {
                    showControls()
                }
                return true
            }
        }
        return false
    }

    private fun scrollBySpeed() {
        val max = maxScrollY()
        if (max <= 0 || scrollView.scrollY >= max) {
            paused = true
            updateControlLabels()
            return
        }
        scrollRemainder += currentSpeed() / 60f
        val delta = scrollRemainder.toInt()
        if (delta > 0) {
            scrollRemainder -= delta
            scrollView.scrollTo(0, (scrollView.scrollY + delta).coerceAtMost(max))
        }
    }

    private fun maxScrollY(): Int {
        val child = scrollView.getChildAt(0) ?: return 0
        return (child.height - scrollView.height).coerceAtLeast(0)
    }

    private fun showControls() {
        controlPanel.visibility = View.VISIBLE
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS)
    }

    private fun updateControlLabels() {
        val state = if (paused) "已暂停" else "播放中"
        playStateText.text = "$state · 点按画面暂停/继续，拖动调整位置"
        playPauseButton.text = if (paused) "继续" else "暂停"
        speedLabel.text = "滚动速度：${currentSpeed()}"
        fontLabel.text = "字体大小：${currentFontSize()}sp"
    }

    private fun saveSettings() {
        val current = script ?: return
        script = repository.save(
            current.copy(
                speed = currentSpeed(),
                fontSize = currentFontSize()
            )
        )
    }

    private fun showColorSettings() {
        val choices = arrayOf("字体颜色", "背景颜色")
        AlertDialog.Builder(this)
            .setTitle("颜色设置")
            .setItems(choices) { _, which ->
                val current = script ?: return@setItems
                if (which == 0) {
                    ColorPalette.showTextColorPicker(this, current.textColor) { selected ->
                        updateColors(textColor = selected, backgroundColor = current.backgroundColor)
                    }
                } else {
                    ColorPalette.showBackgroundColorPicker(this, current.backgroundColor) { selected ->
                        updateColors(textColor = current.textColor, backgroundColor = selected)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
        showControls()
    }

    private fun updateColors(textColor: Int, backgroundColor: Int) {
        val current = script ?: return
        applyColors(textColor, backgroundColor)
        script = repository.save(
            current.copy(
                speed = currentSpeed(),
                fontSize = currentFontSize(),
                textColor = textColor,
                backgroundColor = backgroundColor
            )
        )
        showControls()
    }

    private fun applyColors(textColor: Int, backgroundColor: Int) {
        prompterText.setTextColor(textColor)
        findViewById<View>(R.id.root).setBackgroundColor(backgroundColor)
        scrollView.setBackgroundColor(backgroundColor)
    }

    private fun enterFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            window.insetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun currentSpeed(): Int =
        ScriptSettings.clampSpeed(speedSeek.progress + ScriptSettings.MIN_SPEED)

    private fun currentFontSize(): Int =
        ScriptSettings.clampFontSize(fontSeek.progress + ScriptSettings.MIN_FONT_SIZE)

    private fun simpleSeekListener(onChange: () -> Unit): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onChange()
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }

    companion object {
        const val EXTRA_SCRIPT_ID = "script_id"
        private const val FRAME_MS = 16L
        private const val CONTROLS_HIDE_DELAY_MS = 3500L
        private const val DRAG_THRESHOLD_PX = 8
    }
}
