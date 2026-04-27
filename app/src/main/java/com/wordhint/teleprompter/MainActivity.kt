package com.wordhint.teleprompter

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var repository: ScriptRepository
    private lateinit var titleEdit: EditText
    private lateinit var contentEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var speedLabel: TextView
    private lateinit var fontLabel: TextView
    private lateinit var colorPreview: TextView
    private lateinit var speedSeek: SeekBar
    private lateinit var fontSeek: SeekBar
    private lateinit var orientationGroup: RadioGroup
    private lateinit var portraitRadio: RadioButton
    private lateinit var landscapeRadio: RadioButton
    private lateinit var startButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var currentScript: Script? = null
    private var textColor = ScriptSettings.DEFAULT_TEXT_COLOR
    private var backgroundColor = ScriptSettings.DEFAULT_BACKGROUND_COLOR
    private var suppressSave = false

    private val autoSaveRunnable = Runnable { saveCurrentScript() }
    private val scriptPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        if (data.getBooleanExtra(ScriptPickerActivity.EXTRA_CREATE_NEW, false)) {
            showScript(repository.save(repository.createDraft()))
            return@registerForActivityResult
        }
        val selectedId = data.getStringExtra(ScriptPickerActivity.EXTRA_SCRIPT_ID).orEmpty()
        val selectedScript = repository.loadScript(selectedId) ?: return@registerForActivityResult
        showScript(selectedScript)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = ScriptRepository(this)
        bindViews()
        setupControls()
        loadInitialScript()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autoSaveRunnable)
        saveCurrentScript()
    }

    private fun bindViews() {
        titleEdit = findViewById(R.id.titleEdit)
        contentEdit = findViewById(R.id.contentEdit)
        statusText = findViewById(R.id.statusText)
        speedLabel = findViewById(R.id.speedLabel)
        fontLabel = findViewById(R.id.fontLabel)
        colorPreview = findViewById(R.id.colorPreview)
        speedSeek = findViewById(R.id.speedSeek)
        fontSeek = findViewById(R.id.fontSeek)
        orientationGroup = findViewById(R.id.orientationGroup)
        portraitRadio = findViewById(R.id.portraitRadio)
        landscapeRadio = findViewById(R.id.landscapeRadio)
        startButton = findViewById(R.id.startButton)
        findViewById<Button>(R.id.displaySettingsButton).setOnClickListener { showDisplaySettings() }
        findViewById<Button>(R.id.scriptsButton).setOnClickListener { showScriptPicker() }
    }

    private fun setupControls() {
        speedSeek.max = ScriptSettings.MAX_SPEED - ScriptSettings.MIN_SPEED
        fontSeek.max = ScriptSettings.MAX_FONT_SIZE - ScriptSettings.MIN_FONT_SIZE

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                scheduleAutoSave()
                updateStartState()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        titleEdit.addTextChangedListener(watcher)
        contentEdit.addTextChangedListener(watcher)

        speedSeek.setOnSeekBarChangeListener(simpleSeekListener {
            updateLabels()
            scheduleAutoSave()
        })
        fontSeek.setOnSeekBarChangeListener(simpleSeekListener {
            updateLabels()
            scheduleAutoSave()
        })
        orientationGroup.setOnCheckedChangeListener { _, _ -> scheduleAutoSave() }
        startButton.setOnClickListener { startPrompting() }
    }

    private fun loadInitialScript() {
        val script = repository.loadScripts().firstOrNull() ?: repository.save(repository.createDraft())
        showScript(script)
    }

    private fun showScript(script: Script) {
        suppressSave = true
        currentScript = script
        titleEdit.setText(script.title)
        contentEdit.setText(script.content)
        speedSeek.progress = script.speed - ScriptSettings.MIN_SPEED
        fontSeek.progress = script.fontSize - ScriptSettings.MIN_FONT_SIZE
        textColor = script.textColor
        backgroundColor = script.backgroundColor
        if (script.orientation == ScriptOrientation.LANDSCAPE) {
            landscapeRadio.isChecked = true
        } else {
            portraitRadio.isChecked = true
        }
        suppressSave = false
        updateLabels()
        applyColorPreview()
        updateStartState()
        showSavedStatus(script.updatedAt)
    }

    private fun scheduleAutoSave() {
        if (suppressSave) return
        handler.removeCallbacks(autoSaveRunnable)
        handler.postDelayed(autoSaveRunnable, AUTO_SAVE_DELAY_MS)
    }

    private fun saveCurrentScript(): Script? {
        if (suppressSave) return currentScript
        val existing = currentScript ?: repository.createDraft()
        val content = contentEdit.text.toString()
        val title = titleEdit.text.toString().ifBlank { ScriptTitles.fromContent(content) }
        val script = existing.copy(
            title = title,
            content = content,
            speed = currentSpeed(),
            fontSize = currentFontSize(),
            orientation = currentOrientation(),
            textColor = textColor,
            backgroundColor = backgroundColor
        )
        val saved = repository.save(script)
        currentScript = saved
        showSavedStatus(saved.updatedAt)
        return saved
    }

    private fun showScriptPicker() {
        saveCurrentScript()
        scriptPickerLauncher.launch(Intent(this, ScriptPickerActivity::class.java))
    }

    private fun showDisplaySettings() {
        val choices = arrayOf(getString(R.string.text_color), getString(R.string.background_color))
        AlertDialog.Builder(this)
            .setTitle(R.string.display_settings)
            .setItems(choices) { _, which ->
                if (which == 0) {
                    ColorPalette.showTextColorPicker(this, textColor) { selected ->
                        textColor = selected
                        applyColorPreview()
                        scheduleAutoSave()
                    }
                } else {
                    ColorPalette.showBackgroundColorPicker(this, backgroundColor) { selected ->
                        backgroundColor = selected
                        applyColorPreview()
                        scheduleAutoSave()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startPrompting() {
        val content = contentEdit.text.toString()
        if (content.isBlank()) {
            statusText.text = getString(R.string.enter_script_first)
            return
        }
        val saved = saveCurrentScript() ?: return
        startActivity(
            Intent(this, TeleprompterActivity::class.java)
                .putExtra(TeleprompterActivity.EXTRA_SCRIPT_ID, saved.id)
        )
    }

    private fun updateLabels() {
        speedLabel.text = getString(R.string.speed_label, currentSpeed())
        fontLabel.text = getString(R.string.font_label, currentFontSize())
    }

    private fun applyColorPreview() {
        colorPreview.setTextColor(textColor)
        colorPreview.setBackgroundColor(backgroundColor)
    }

    private fun updateStartState() {
        startButton.isEnabled = contentEdit.text.toString().isNotBlank()
        if (!startButton.isEnabled) {
            statusText.text = getString(R.string.paste_to_start)
        }
    }

    private fun showSavedStatus(updatedAt: Long) {
        if (contentEdit.text.toString().isBlank()) {
            statusText.text = getString(R.string.paste_to_start)
        } else {
            statusText.text = getString(R.string.saved)
        }
    }

    private fun currentSpeed(): Int =
        ScriptSettings.clampSpeed(speedSeek.progress + ScriptSettings.MIN_SPEED)

    private fun currentFontSize(): Int =
        ScriptSettings.clampFontSize(fontSeek.progress + ScriptSettings.MIN_FONT_SIZE)

    private fun currentOrientation(): ScriptOrientation =
        if (orientationGroup.checkedRadioButtonId == R.id.landscapeRadio) {
            ScriptOrientation.LANDSCAPE
        } else {
            ScriptOrientation.PORTRAIT
        }

    private fun simpleSeekListener(onChange: () -> Unit): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onChange()
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }

    companion object {
        private const val AUTO_SAVE_DELAY_MS = 450L
    }
}
