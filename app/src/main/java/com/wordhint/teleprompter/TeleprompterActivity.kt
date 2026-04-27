package com.wordhint.teleprompter

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Range
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.hardware.camera2.CaptureRequest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TeleprompterActivity : ComponentActivity() {
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
    private lateinit var previewView: PreviewView
    private lateinit var cameraToggleButton: Button
    private lateinit var lensSwitchButton: Button
    private lateinit var recordButton: Button
    private lateinit var previewVisibilityButton: Button
    private lateinit var systemCameraButton: Button
    private lateinit var alphaSeek: SeekBar
    private lateinit var alphaLabel: TextView
    private lateinit var qualityButton: Button
    private lateinit var timerText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var script: Script? = null
    private var paused = false
    private var downY = 0f
    private var downScrollY = 0
    private var dragging = false
    private var scrollRemainder = 0f

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraEnabled = false
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var overlayAlpha = DEFAULT_OVERLAY_ALPHA
    private var selectedQuality = Quality.FHD
    private var selectedFps = 30
    private var recordingStartMs = 0L
    private var previewHidden = false

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        if (allGranted) {
            startCameraPreview()
        } else {
            playStateText.text = "未获得摄像头/麦克风权限，无法录制。"
            showControls()
            updateCameraButtons()
        }
    }

    private val systemCameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val ok = result.resultCode == RESULT_OK
        playStateText.text = if (ok) {
            "系统相机已完成录制，已返回提词页面。"
        } else {
            "系统相机已取消录制。"
        }
        showControls()
    }

    private val scrollTick = object : Runnable {
        override fun run() {
            if (!paused) {
                scrollBySpeed()
            }
            handler.postDelayed(this, FRAME_MS)
        }
    }

    private val hideControlsRunnable = Runnable { controlPanel.visibility = View.GONE }
    private val recordingTicker = object : Runnable {
        override fun run() {
            if (recording != null) {
                updateRecordingTimer()
                handler.postDelayed(this, 1000L)
            }
        }
    }

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
        stopRecordingIfNeeded()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        cameraProvider?.unbindAll()
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
        previewView = findViewById(R.id.cameraPreview)
        cameraToggleButton = findViewById(R.id.cameraToggleButton)
        lensSwitchButton = findViewById(R.id.switchLensButton)
        recordButton = findViewById(R.id.recordButton)
        previewVisibilityButton = findViewById(R.id.previewVisibilityButton)
        systemCameraButton = findViewById(R.id.systemCameraButton)
        alphaSeek = findViewById(R.id.overlayAlphaSeek)
        alphaLabel = findViewById(R.id.overlayAlphaLabel)
        qualityButton = findViewById(R.id.qualityButton)
        timerText = findViewById(R.id.recordTimerText)

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
        updateCameraButtons()
        alphaSeek.progress = overlayAlpha - MIN_OVERLAY_ALPHA
        updateAlphaLabel()
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

        cameraToggleButton.setOnClickListener {
            if (cameraEnabled) {
                disableCameraPreview()
                playStateText.text = "摄像头已关闭，提词继续全屏。"
            } else {
                ensureCameraPermissionsAndStart()
            }
            showControls()
        }
        lensSwitchButton.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            if (cameraEnabled) {
                startCameraPreview()
            }
            showControls()
        }
        recordButton.setOnClickListener {
            if (recording == null) {
                startRecording()
            } else {
                stopRecordingIfNeeded()
            }
            showControls()
        }
        previewVisibilityButton.setOnClickListener {
            if (!cameraEnabled) {
                playStateText.text = "请先开启 App 内摄像头，再隐藏或显示录制画面。"
            } else {
                previewHidden = !previewHidden
                updateCameraPreviewVisibility()
                applyColors(script?.textColor ?: Color.WHITE, script?.backgroundColor ?: Color.BLACK)
                playStateText.text = if (previewHidden) {
                    "录制画面已隐藏，提词器保持全屏显示；录制不会停止。"
                } else {
                    "录制画面已显示，提词器以半透明遮罩覆盖。"
                }
            }
            showControls()
        }
        systemCameraButton.setOnClickListener {
            launchSystemCamera()
            showControls()
        }
        alphaSeek.max = MAX_OVERLAY_ALPHA - MIN_OVERLAY_ALPHA
        alphaSeek.setOnSeekBarChangeListener(simpleSeekListener {
            overlayAlpha = alphaSeek.progress + MIN_OVERLAY_ALPHA
            updateAlphaLabel()
            applyColors(
                script?.textColor ?: Color.WHITE,
                script?.backgroundColor ?: Color.BLACK
            )
            showControls()
        })
        qualityButton.setOnClickListener {
            showVideoOptionsDialog()
            showControls()
        }
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
        val promptBg = if (cameraEnabled && !previewHidden) withAlpha(backgroundColor, overlayAlpha) else backgroundColor
        findViewById<View>(R.id.root).setBackgroundColor(promptBg)
        scrollView.setBackgroundColor(promptBg)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun ensureCameraPermissionsAndStart() {
        val pendingPermissions = CAMERA_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (pendingPermissions.isEmpty()) {
            startCameraPreview()
        } else {
            permissionsLauncher.launch(pendingPermissions.toTypedArray())
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun startCameraPreview() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        selectedQuality,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                    )
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(this, selector, preview, videoCapture)
                val targetRange = Range(selectedFps, selectedFps)
                val fpsApplied = runCatching {
                    Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(
                        CaptureRequestOptions.Builder()
                            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetRange)
                            .build()
                    )
                }.isSuccess
                cameraEnabled = true
                updateCameraPreviewVisibility()
                applyColors(script?.textColor ?: Color.WHITE, script?.backgroundColor ?: Color.BLACK)
                updateCameraButtons()
                val lensLabel = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    "前置摄像头"
                } else {
                    "后置摄像头"
                }
                playStateText.text = if (fpsApplied) {
                    "$lensLabel 已开启（${qualityLabel()} / ${selectedFps}fps）。"
                } else {
                    "$lensLabel 已开启（${qualityLabel()}，当前设备可能不支持 ${selectedFps}fps 强制设置）。"
                }
            } catch (_: Exception) {
                cameraEnabled = false
                updateCameraButtons()
                playStateText.text = "摄像头启动失败，请检查设备是否占用摄像头。"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun disableCameraPreview() {
        stopRecordingIfNeeded()
        cameraProvider?.unbindAll()
        cameraEnabled = false
        previewHidden = false
        previewView.visibility = View.GONE
        previewView.alpha = 1f
        applyColors(script?.textColor ?: Color.WHITE, script?.backgroundColor ?: Color.BLACK)
        updateCameraButtons()
    }

    private fun startRecording() {
        if (!cameraEnabled) {
            ensureCameraPermissionsAndStart()
            playStateText.text = "请先开启摄像头预览，再开始录制。"
            return
        }
        val capture = videoCapture ?: return
        val outputDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Teleprompter")
        if (!outputDir.exists()) outputDir.mkdirs()
        val file = File(outputDir, "VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4")
        val options = FileOutputOptions.Builder(file).build()
        val pending: PendingRecording = capture.output.prepareRecording(this, options)
        val hasAudio = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasAudio) {
            pending.withAudioEnabled()
        }
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    recordingStartMs = System.currentTimeMillis()
                    timerText.visibility = View.VISIBLE
                    handler.post(recordingTicker)
                    playStateText.text = "正在录制：${file.name}"
                    updateCameraButtons()
                }

                is VideoRecordEvent.Finalize -> {
                    recording = null
                    handler.removeCallbacks(recordingTicker)
                    timerText.visibility = View.GONE
                    updateCameraButtons()
                    playStateText.text = if (event.hasError()) {
                        "录制失败，请重试。"
                    } else {
                        "录制完成：${file.absolutePath}"
                    }
                }
            }
        }
    }

    private fun stopRecordingIfNeeded() {
        recording?.stop()
        recording = null
        handler.removeCallbacks(recordingTicker)
        timerText.visibility = View.GONE
        updateCameraButtons()
    }

    private fun launchSystemCamera() {
        val intent = Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(android.provider.MediaStore.EXTRA_VIDEO_QUALITY, 1)
        }
        if (intent.resolveActivity(packageManager) != null) {
            playStateText.text = "将打开系统相机。系统相机模式下，提词器不能覆盖在相机 App 上。"
            systemCameraLauncher.launch(intent)
        } else {
            playStateText.text = "当前设备没有可用的系统录像应用。"
        }
    }

    private fun updateCameraButtons() {
        updateCameraPreviewVisibility()
        cameraToggleButton.text = if (cameraEnabled) "关摄像头" else "开摄像头"
        lensSwitchButton.text = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "切后置" else "切前置"
        lensSwitchButton.isEnabled = cameraEnabled
        recordButton.text = if (recording == null) "开始录制" else "停止录制"
        recordButton.isEnabled = cameraEnabled
        previewVisibilityButton.text = if (previewHidden) "显示录制画面" else "隐藏录制画面"
        previewVisibilityButton.isEnabled = cameraEnabled
        qualityButton.text = "画质/FPS：${qualityLabel()} ${selectedFps}fps"
    }

    private fun updateCameraPreviewVisibility() {
        previewView.visibility = if (cameraEnabled) View.VISIBLE else View.GONE
        previewView.alpha = if (previewHidden) 0f else 1f
    }

    private fun updateAlphaLabel() {
        alphaLabel.text = "提词遮罩透明度：${overlayAlpha}/255"
    }

    private fun qualityLabel(): String = when (selectedQuality) {
        Quality.UHD -> "2160p"
        Quality.FHD -> "1080p"
        Quality.HD -> "720p"
        else -> "480p"
    }

    private fun updateRecordingTimer() {
        val elapsedSec = ((System.currentTimeMillis() - recordingStartMs) / 1000L).coerceAtLeast(0L)
        val mm = (elapsedSec / 60).toString().padStart(2, '0')
        val ss = (elapsedSec % 60).toString().padStart(2, '0')
        timerText.text = "REC $mm:$ss"
    }

    private fun showVideoOptionsDialog() {
        val qualityItems = arrayOf("2160p", "1080p", "720p", "480p")
        val qualityValues = arrayOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
        val qualityIndex = qualityValues.indexOf(selectedQuality).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("选择录制分辨率")
            .setSingleChoiceItems(qualityItems, qualityIndex) { dialog, which ->
                selectedQuality = qualityValues[which]
                dialog.dismiss()
                showFpsOptionsDialog()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showFpsOptionsDialog() {
        val fpsItems = arrayOf("24 fps", "30 fps", "60 fps（设备支持时生效）")
        val fpsValues = arrayOf(24, 30, 60)
        val currentIndex = fpsValues.indexOf(selectedFps).coerceAtLeast(1)
        AlertDialog.Builder(this)
            .setTitle("选择目标帧率")
            .setSingleChoiceItems(fpsItems, currentIndex) { dialog, which ->
                selectedFps = fpsValues[which]
                updateCameraButtons()
                if (cameraEnabled) {
                    startCameraPreview()
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
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
        private const val DEFAULT_OVERLAY_ALPHA = 112
        private const val MIN_OVERLAY_ALPHA = 48
        private const val MAX_OVERLAY_ALPHA = 220
        private val CAMERA_PERMISSIONS = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
    }
}
