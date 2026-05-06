package com.wordhint.teleprompter

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class FloatingPrompterService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var overlayRoot: FrameLayout
    private lateinit var scrollView: ScrollView
    private lateinit var textView: TextView
    private lateinit var controls: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var speedLabel: TextView
    private lateinit var fontLabel: TextView
    private lateinit var bgAlphaLabel: TextView
    private lateinit var textAlphaLabel: TextView
    private lateinit var pauseButton: Button
    private lateinit var touchThroughButton: Button
    private lateinit var windowParams: WindowManager.LayoutParams

    private var paused = false
    private var dragging = false
    private var downY = 0f
    private var downScrollY = 0
    private var scrollRemainder = 0f
    private var speed = ScriptSettings.DEFAULT_SPEED
    private var fontSize = ScriptSettings.DEFAULT_FONT_SIZE
    private var textColor = ScriptSettings.DEFAULT_TEXT_COLOR
    private var backgroundColor = ScriptSettings.DEFAULT_BACKGROUND_COLOR
    private var backgroundAlpha = DEFAULT_BACKGROUND_ALPHA
    private var textAlpha = DEFAULT_TEXT_ALPHA
    private var touchThrough = false
    private var controlsHiddenOnDown = false

    private val scrollTick = object : Runnable {
        override fun run() {
            if (!paused) scrollBySpeed()
            handler.postDelayed(this, FRAME_MS)
        }
    }

    private val hideControlsRunnable = Runnable {
        if (!touchThrough && ::controls.isInitialized) {
            controls.visibility = View.GONE
        }
    }

    private val restoreTouchable = Runnable {
        touchThrough = false
        updateWindowTouchMode()
        setStatus(getString(R.string.floating_touch_restored))
        showControls()
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        speed = ScriptSettings.clampSpeed(intent?.getIntExtra(EXTRA_SPEED, speed) ?: speed)
        fontSize = ScriptSettings.clampFontSize(intent?.getIntExtra(EXTRA_FONT_SIZE, fontSize) ?: fontSize)
        textColor = intent?.getIntExtra(EXTRA_TEXT_COLOR, textColor) ?: textColor
        backgroundColor = intent?.getIntExtra(EXTRA_BACKGROUND_COLOR, backgroundColor) ?: backgroundColor
        val content = intent?.getStringExtra(EXTRA_CONTENT).orEmpty()

        if (!::overlayRoot.isInitialized) {
            createOverlay(content)
            windowManager.addView(overlayRoot, windowParams)
            showControls()
            handler.post(scrollTick)
        } else {
            textView.text = content
            applyDisplay()
            updateLabels()
            showControls()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::overlayRoot.isInitialized) {
            runCatching { windowManager.removeView(overlayRoot) }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createOverlay(content: String) {
        overlayRoot = FrameLayout(this).apply {
            setOnClickListener {
                if (!touchThrough) {
                    paused = !paused
                    updateLabels()
                    showControls()
                }
            }
        }
        scrollView = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setOnTouchListener { _, event -> handlePromptTouch(event) }
        }
        textView = TextView(this).apply {
            text = content
            includeFontPadding = false
            setLineSpacing(dp(10).toFloat(), 1f)
        }
        scrollView.addView(
            textView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        overlayRoot.addView(
            scrollView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        controls = buildControls()
        overlayRoot.addView(
            controls,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                bottomMargin = dp(12)
            }
        )
        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            baseWindowFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 1f
        }
        applyDisplay()
        applyResponsivePadding()
        updateLabels()
        enterImmersiveOverlay()
    }

    private fun buildControls(): LinearLayout {
        statusText = TextView(this).apply {
            text = getString(R.string.floating_status)
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        speedLabel = label()
        fontLabel = label()
        bgAlphaLabel = label()
        textAlphaLabel = label()
        pauseButton = controlButton(getString(R.string.pause)) {
            paused = !paused
            updateLabels()
        }
        touchThroughButton = controlButton(getString(R.string.touch_camera)) {
            enableTouchThrough()
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.argb(188, 18, 22, 28))
            addView(statusText)
            addView(speedLabel)
            addView(seekBar(0, ScriptSettings.MAX_SPEED - ScriptSettings.MIN_SPEED, speed - ScriptSettings.MIN_SPEED) {
                speed = ScriptSettings.clampSpeed(it + ScriptSettings.MIN_SPEED)
                updateLabels()
            })
            addView(fontLabel)
            addView(seekBar(0, ScriptSettings.MAX_FONT_SIZE - ScriptSettings.MIN_FONT_SIZE, fontSize - ScriptSettings.MIN_FONT_SIZE) {
                fontSize = ScriptSettings.clampFontSize(it + ScriptSettings.MIN_FONT_SIZE)
                applyDisplay()
                updateLabels()
            })
            addView(bgAlphaLabel)
            addView(seekBar(MIN_BACKGROUND_ALPHA, MAX_BACKGROUND_ALPHA, backgroundAlpha) {
                backgroundAlpha = it
                applyDisplay()
                updateLabels()
            })
            addView(textAlphaLabel)
            addView(seekBar(MIN_TEXT_ALPHA, MAX_TEXT_ALPHA, textAlpha) {
                textAlpha = it
                applyDisplay()
                updateLabels()
            })
            addView(LinearLayout(this@FloatingPrompterService).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(pauseButton, LinearLayout.LayoutParams(0, dp(44), 1f))
                addView(touchThroughButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) })
                addView(controlButton(getString(R.string.close)) { stopSelf() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) })
            })
        }
    }

    private fun label(): TextView =
        TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, dp(8), 0, 0)
        }

    private fun controlButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(150, 255, 255, 255))
            setOnClickListener {
                showControls()
                onClick()
            }
        }

    private fun seekBar(min: Int, max: Int, value: Int, onChange: (Int) -> Unit): SeekBar =
        SeekBar(this).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) this.min = min
            this.max = max
            progress = value.coerceIn(min, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    onChange(progress)
                    if (fromUser) showControls()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    showControls()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    showControls()
                }
            })
        }

    private fun applyDisplay() {
        val bg = Color.argb(backgroundAlpha, Color.red(backgroundColor), Color.green(backgroundColor), Color.blue(backgroundColor))
        val fg = Color.argb(textAlpha, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
        overlayRoot.setBackgroundColor(bg)
        scrollView.setBackgroundColor(bg)
        textView.setTextColor(fg)
        textView.textSize = fontSize.toFloat()
    }

    private fun applyResponsivePadding() {
        overlayRoot.post {
            val padding = PromptViewportPadding.calculate(
                widthPx = overlayRoot.width,
                heightPx = overlayRoot.height,
                density = resources.displayMetrics.density
            )
            scrollView.setPadding(padding.left, padding.top, padding.right, padding.bottom + dp(96))
        }
    }

    private fun handlePromptTouch(event: MotionEvent): Boolean {
        if (touchThrough) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                downScrollY = scrollView.scrollY
                dragging = false
                controlsHiddenOnDown = ::controls.isInitialized && controls.visibility != View.VISIBLE
                showControls()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = downY - event.y
                if (kotlin.math.abs(delta) > DRAG_THRESHOLD_PX) {
                    dragging = true
                    paused = true
                    scrollView.scrollTo(0, (downScrollY + delta.toInt()).coerceIn(0, maxScrollY()))
                    updateLabels()
                    showControls()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging && !controlsHiddenOnDown) paused = !paused
                updateLabels()
                showControls()
                controlsHiddenOnDown = false
                return true
            }
        }
        return false
    }

    private fun scrollBySpeed() {
        val max = maxScrollY()
        if (max <= 0 || scrollView.scrollY >= max) {
            paused = true
            updateLabels()
            return
        }
        scrollRemainder += speed / 60f
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

    private fun enableTouchThrough() {
        touchThrough = true
        paused = false
        updateWindowTouchMode()
        setStatus(getString(R.string.floating_touch_camera_status))
        handler.removeCallbacks(hideControlsRunnable)
        handler.removeCallbacks(restoreTouchable)
        handler.postDelayed(restoreTouchable, TOUCH_THROUGH_MS)
    }

    private fun updateWindowTouchMode() {
        if (!::windowParams.isInitialized) return
        windowParams.flags = if (touchThrough) {
            baseWindowFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseWindowFlags()
        }
        windowParams.alpha = if (touchThrough) TOUCH_THROUGH_WINDOW_ALPHA else 1f
        overlayRoot.visibility = if (touchThrough) View.INVISIBLE else View.VISIBLE
        windowManager.updateViewLayout(overlayRoot, windowParams)
        controls.visibility = if (touchThrough) View.GONE else View.VISIBLE
    }

    private fun baseWindowFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

    private fun showControls() {
        if (!touchThrough && ::controls.isInitialized) {
            controls.visibility = View.VISIBLE
            handler.removeCallbacks(hideControlsRunnable)
            handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS)
        }
    }

    private fun setStatus(message: String) {
        if (::statusText.isInitialized) statusText.text = message
    }

    private fun updateLabels() {
        if (!::speedLabel.isInitialized) return
        pauseButton.text = if (paused) getString(R.string.resume) else getString(R.string.pause)
        speedLabel.text = getString(R.string.speed_label, speed)
        fontLabel.text = getString(R.string.font_label, fontSize)
        bgAlphaLabel.text = getString(R.string.floating_bg_alpha_label, backgroundAlpha)
        textAlphaLabel.text = getString(R.string.floating_text_alpha_label, textAlpha)
    }

    private fun enterImmersiveOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            overlayRoot.windowInsetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            overlayRoot.windowInsetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val FRAME_MS = 16L
        private const val CONTROLS_HIDE_DELAY_MS = 3500L
        private const val DRAG_THRESHOLD_PX = 8
        private const val DEFAULT_BACKGROUND_ALPHA = 218
        private const val DEFAULT_TEXT_ALPHA = 255
        private const val MIN_BACKGROUND_ALPHA = 96
        private const val MAX_BACKGROUND_ALPHA = 255
        private const val MIN_TEXT_ALPHA = 120
        private const val MAX_TEXT_ALPHA = 255
        private const val TOUCH_THROUGH_MS = 7000L
        private const val TOUCH_THROUGH_WINDOW_ALPHA = 0f

        const val ACTION_STOP = "com.wordhint.teleprompter.action.STOP_FLOATING_PROMPTER"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_FONT_SIZE = "font_size"
        const val EXTRA_TEXT_COLOR = "text_color"
        const val EXTRA_BACKGROUND_COLOR = "background_color"
    }
}
