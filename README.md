# CueCam

**CueCam** is a lightweight Android teleprompter camera app for creators, presenters, teachers, reviewers, livestream hosts, and anyone who needs to speak naturally while reading a script.

It combines a fullscreen scrolling teleprompter with optional in-app camera preview and video recording. The goal is simple: paste a script, tune the reading experience, record, and keep the video easy to find in the system gallery.

中文名：**CueCam 提词相机**

## Highlights

- Fullscreen teleprompter with portrait and landscape support.
- Paste or write multilingual scripts, with Chinese and English handled well by default.
- Auto-save local drafts and quickly switch scripts from a fullscreen picker.
- Tune scroll speed, font size, text color, and background color.
- Adjust speed and font size while prompting.
- Tap to pause/resume, drag to manually reposition the prompt.
- In-app camera preview and recording with front/back camera switching.
- Hide the camera preview while recording, so the screen can stay as a clean fullscreen teleprompter.
- Recording resolution and FPS selector: 2160p, 1080p, 720p, 480p and 24/30/60fps target options.
- Recording timer while capturing video.
- Saves recordings to the Android media library: `Movies/CueCam`.
- Optional system camera launch for devices whose built-in camera app provides extra settings.
- Glass-style recording controls and refreshed CueCam launcher icon.

## Languages

Current UI coverage:

- English: default UI.
- Simplified Chinese: full localized UI.
- Spanish, Portuguese (Brazil), Japanese, Korean, and Hindi: planned next, because partial translations are worse than a consistent English fallback.

The app supports multilingual script content directly in the editor; scripts are stored locally as plain text data in the app.

## Recording Behavior

CueCam has two camera paths:

- **In-app camera**: supports teleprompter overlay, front/back camera switch, recording, preview hiding, quality/FPS options, and recording timer.
- **System camera**: opens the phone's built-in camera app. This is useful for vendor camera settings, but Android does not allow CueCam to draw its teleprompter over another camera app without high-risk overlay permissions, so fullscreen prompting is not available inside the external camera app.

For normal use, use the in-app camera.

## Where Videos Are Saved

Recorded videos are saved through Android `MediaStore`, not in a hidden app-private folder.

Expected location:

```text
Movies/CueCam/VID_yyyyMMdd_HHmmss.mp4
```

They should appear in the phone's Gallery, Photos, or Videos app after recording completes.

## Android Compatibility

- Minimum Android version: Android 6.0, API 23.
- Target SDK: API 35.
- APK type: universal debug APK for sideload testing.
- Required runtime permissions:
  - Camera
  - Microphone
  - Storage write permission only on Android 9 and older.

Camera resolution and FPS support depends on the device. CueCam requests the selected quality/FPS and falls back when the hardware does not support the exact target.

## Build

```bash
./gradlew clean check assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install

For a local debug install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If an older debug build behaves unexpectedly, uninstall the old app first, then install the new APK.

## Roadmap

Keeping the app lightweight matters. Sensible next steps:

- Complete translations for Spanish, Portuguese, Japanese, Korean, and Hindi.
- Add optional countdown before recording.
- Add simple script import/export.
- Add mirror mode for external teleprompter glass.
- Add release signing for production distribution.

## License

No license has been selected yet. Add one before encouraging external contributions.
