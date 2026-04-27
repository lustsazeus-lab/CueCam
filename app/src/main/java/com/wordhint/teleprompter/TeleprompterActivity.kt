package com.wordhint.teleprompter

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.hardware.camera2.CaptureRequest

class TeleprompterActivity : ComponentActivity() {
    private lateinit var repository: ScriptRepository
    private lateinit var scrollView: ScrollView
    private lateinit var prompterText: TextView
    private lateinit var controlPanel: LinearLayout
    private lateinit var playStateText: TextView
    private lateinit var speedLabel: TextView
    private lateinit var fontLabel: TextView
    private lateinit var cameraStatusText: TextView
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
            setStatus(getString(R.string.no_camera_permissions))
            showControls()
            updateCameraButtons()
        }
    }

    private val systemCameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val ok = result.resultCode == RESULT_OK
        setStatus(if (ok) {
            getString(R.string.system_camera_done)
        } else {
            getString(R.string.system_camera_cancelled)
        })
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
        cameraStatusText = findViewById(R.id.cameraStatusText)
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
        setStatus(getString(R.string.recording_status_initial))
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
                setStatus(getString(R.string.camera_closed))
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
                setStatus(getString(R.string.enable_camera_first_preview))
            } else {
                previewHidden = !previewHidden
                updateCameraPreviewVisibility()
                applyColors(script?.textColor ?: Color.WHITE, script?.backgroundColor ?: Color.BLACK)
                setStatus(if (previewHidden) {
                    getString(R.string.preview_hidden_status)
                } else {
                    getString(R.string.preview_shown_status)
                })
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
        val state = if (paused) getString(R.string.paused) else getString(R.string.playing)
        playStateText.text = getString(R.string.prompter_control_hint, state)
        playPauseButton.text = if (paused) getString(R.string.resume) else getString(R.string.pause)
        speedLabel.text = getString(R.string.speed_label, currentSpeed())
        fontLabel.text = getString(R.string.font_label, currentFontSize())
    }

    private fun setStatus(message: String) {
        cameraStatusText.text = message
        playStateText.text = message
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
        val choices = arrayOf(getString(R.string.text_color), getString(R.string.background_color))
        AlertDialog.Builder(this)
            .setTitle(R.string.color_settings)
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
            .setNegativeButton(R.string.cancel, null)
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
        val pendingPermissions = requiredRecordingPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (pendingPermissions.isEmpty()) {
            startCameraPreview()
        } else {
            permissionsLauncher.launch(pendingPermissions.toTypedArray())
        }
    }

    private fun requiredRecordingPermissions(): List<String> =
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
                    getString(R.string.front_camera)
                } else {
                    getString(R.string.back_camera)
                }
                setStatus(if (fpsApplied) {
                    getString(R.string.camera_started, lensLabel, qualityLabel(), selectedFps)
                } else {
                    getString(R.string.camera_started_fps_maybe, lensLabel, qualityLabel(), selectedFps)
                })
            } catch (_: Exception) {
                cameraEnabled = false
                updateCameraButtons()
                setStatus(getString(R.string.camera_failed))
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
            setStatus(getString(R.string.enable_camera_first_record))
            return
        }
        val capture = videoCapture ?: return
        val displayName = RecordingOutput.displayName()
        val options = createMediaStoreOutputOptions(displayName)
        val pending = capture.output.prepareRecording(this, options)
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
                    setStatus(getString(R.string.recording_now, displayName))
                    updateCameraButtons()
                }

                is VideoRecordEvent.Finalize -> {
                    recording = null
                    handler.removeCallbacks(recordingTicker)
                    timerText.visibility = View.GONE
                    updateCameraButtons()
                    setStatus(if (event.hasError()) {
                        getString(R.string.recording_failed)
                    } else {
                        getString(R.string.recording_saved)
                    })
                }
            }
        }
    }

    private fun createMediaStoreOutputOptions(displayName: String): MediaStoreOutputOptions {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, RecordingOutput.MIME_TYPE)
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    RecordingOutput.relativePath(Environment.DIRECTORY_MOVIES)
                )
            }
        }
        return MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()
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
            setStatus(getString(R.string.system_camera_launch))
            systemCameraLauncher.launch(intent)
        } else {
            setStatus(getString(R.string.no_system_camera))
        }
    }

    private fun updateCameraButtons() {
        updateCameraPreviewVisibility()
        cameraToggleButton.text = if (cameraEnabled) getString(R.string.close_camera) else getString(R.string.open_camera)
        lensSwitchButton.text = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            getString(R.string.switch_back)
        } else {
            getString(R.string.switch_front)
        }
        lensSwitchButton.isEnabled = cameraEnabled
        recordButton.text = if (recording == null) getString(R.string.start_recording) else getString(R.string.stop_recording)
        recordButton.isEnabled = cameraEnabled
        previewVisibilityButton.text = if (previewHidden) getString(R.string.show_preview) else getString(R.string.hide_preview)
        previewVisibilityButton.isEnabled = cameraEnabled
        qualityButton.text = getString(R.string.quality_button, qualityLabel(), selectedFps)
    }

    private fun updateCameraPreviewVisibility() {
        previewView.visibility = if (cameraEnabled) View.VISIBLE else View.GONE
        previewView.alpha = if (previewHidden) 0f else 1f
    }

    private fun updateAlphaLabel() {
        alphaLabel.text = getString(R.string.overlay_alpha_label, overlayAlpha)
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
            .setTitle(R.string.choose_resolution)
            .setSingleChoiceItems(qualityItems, qualityIndex) { dialog, which ->
                selectedQuality = qualityValues[which]
                dialog.dismiss()
                showFpsOptionsDialog()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFpsOptionsDialog() {
        val fpsItems = arrayOf("24 fps", "30 fps", getString(R.string.fps_60_maybe))
        val fpsValues = arrayOf(24, 30, 60)
        val currentIndex = fpsValues.indexOf(selectedFps).coerceAtLeast(1)
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_fps)
            .setSingleChoiceItems(fpsItems, currentIndex) { dialog, which ->
                selectedFps = fpsValues[which]
                updateCameraButtons()
                if (cameraEnabled) {
                    startCameraPreview()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
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
    }
}
