# CueCam 提词相机

**CueCam 提词相机** 是一款轻量级 Android 提词录制工具，适合口播创作者、讲师、产品介绍、评测、直播主播、短视频脚本录制和远程演示使用。

它把「全屏滚动提词器」和「手机摄像录制」放在同一个离线 App 里：粘贴脚本，调好速度、字号和颜色，开始提词，也可以同时调用前置或后置摄像头录制视频。录好的视频会保存到系统媒体库，方便在相册里找到。

English name: **CueCam**

## 主要功能

- 全屏提词，支持横屏和竖屏。
- 支持粘贴或编辑中文、英文以及多语言混合稿件。
- 本地自动保存草稿，可从稿件列表快速切换。
- 可设置滚动速度、字体大小、文字颜色和背景颜色。
- 提词过程中也能实时调整速度和字号。
- 点按暂停/继续，上下拖动可以手动改变当前显示位置。
- 内置摄像头预览和视频录制。
- 支持前置/后置摄像头切换。
- 录制时可隐藏摄像头预览，让屏幕保持干净的全屏提词状态。
- 支持录制分辨率和 FPS 目标选择：2160p、1080p、720p、480p，以及 24/30/60fps。
- 录制过程中显示计时器。
- 视频保存到 Android 系统媒体库：`Movies/CueCam`。
- 美颜相机模式：调用系统相机，并用全屏悬浮提词器覆盖相机画面。
- 悬浮提词器可调背景颜色深度、文字透明度、速度和字号，并可临时透传触控来操作系统相机。
- 毛玻璃风格控制面板和重新设计的桌面图标。

## 适合谁使用

- 中文口播和短视频创作者。
- 需要边看稿边录制的讲师、销售、产品经理和运营人员。
- 需要录制教程、评测、介绍视频的人。
- 希望离线使用、不要账号、不要云同步的轻量工具用户。

## 语言支持

当前界面语言：

- 简体中文：完整支持。
- English: full default UI.

后续适合补充的高频语言：

- Spanish
- Portuguese (Brazil)
- Japanese
- Korean
- Hindi

脚本文本本身没有语言限制，可以直接粘贴中文、英文、标点、换行和多语言混合内容。所有稿件都保存在本机。

## 录制逻辑

CueCam 有两种摄像头使用方式：

- **App 内置摄像头**：支持提词器叠加、前后摄像头切换、录制、隐藏预览、分辨率/FPS 选择和录制计时器。
- **美颜相机模式**：打开手机自带相机 App，同时启动 CueCam 的全屏悬浮提词器。这个模式适合使用厂商相机自带美颜、前后摄切换和相机设置。

美颜相机模式需要 Android 的“显示在其他应用上层”权限。悬浮提词器默认用高透明遮罩覆盖摄像头动态图像，保证字幕可读；需要点击系统相机按钮时，可以点“操作相机”临时透传触控，几秒后自动恢复提词控制。

## 视频保存位置

录制完成后，视频通过 Android `MediaStore` 保存，不放在隐藏的 App 私有目录里。

默认位置：

```text
Movies/CueCam/VID_yyyyMMdd_HHmmss.mp4
```

录制后通常可以在手机的「相册」「照片」「视频」或文件管理器中找到。

## Android 兼容性

- 最低系统版本：Android 6.0 / API 23。
- 目标 SDK：API 35。
- APK 类型：universal debug APK，适合侧载测试。
- 需要权限：
  - 摄像头
  - 麦克风
  - Android 9 及以下需要写入存储权限
  - 美颜相机模式需要“显示在其他应用上层”权限

不同手机对分辨率和 FPS 的支持不完全一样。CueCam 会请求你选择的质量和帧率；如果硬件不支持，会按 CameraX 的能力自动回退。

## 构建

```bash
./gradlew clean check assembleDebug
```

Debug APK 默认生成位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安装

本地调试安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

如果旧版 debug 包行为异常，建议先卸载旧版本再安装新版。

## 轻量化路线图

这个项目的方向是保持轻量、离线、好用，不做复杂账号系统。

- 补全西班牙语、葡萄牙语、日语、韩语和印地语界面翻译。
- 增加录制前倒计时。
- 增加简单的稿件导入/导出。
- 增加镜像模式，适配实体提词器玻璃。
- 增加正式签名版 APK/AAB，用于生产发布。

## License

CueCam is licensed under the [Apache License 2.0](LICENSE).

---

# CueCam

**CueCam** is a lightweight Android teleprompter camera app for creators, presenters, teachers, reviewers, livestream hosts, and anyone who needs to speak naturally while reading a script.

It combines a fullscreen scrolling teleprompter with optional in-app camera preview and video recording. The goal is simple: paste a script, tune the reading experience, record, and keep the video easy to find in the system gallery.

Chinese name: **CueCam 提词相机**

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
- Beauty Camera mode launches the system camera and covers it with a fullscreen floating teleprompter.
- Floating teleprompter supports background opacity, text opacity, speed, font size, and temporary touch-through for operating the system camera.
- Glass-style recording controls and refreshed CueCam launcher icon.

## Who It Is For

- Short-form video creators and talking-head creators.
- Teachers, sales teams, product managers, and operators who record scripted videos.
- People recording tutorials, product reviews, explainers, or presentations.
- Users who want an offline, account-free, lightweight teleprompter camera.

## Languages

Current UI coverage:

- Simplified Chinese: full localized UI.
- English: full default UI.

Planned high-demand languages:

- Spanish
- Portuguese (Brazil)
- Japanese
- Korean
- Hindi

The script editor accepts multilingual content directly. Scripts are stored locally on the device.

## Recording Behavior

CueCam has two camera paths:

- **In-app camera**: supports teleprompter overlay, front/back camera switch, recording, preview hiding, quality/FPS options, and recording timer.
- **Beauty Camera mode**: opens the phone's built-in camera app and starts CueCam's fullscreen floating teleprompter. This is the best path when you need vendor beauty filters, camera settings, and front/back switching from the phone's own camera app.

Beauty Camera mode requires Android's "display over other apps" permission. The floating prompter uses a strong readable background over the moving camera preview, and includes a temporary touch-through button so you can operate the system camera underneath.

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
  - Display over other apps for Beauty Camera mode.

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

## Lightweight Roadmap

The app should stay lightweight, offline, and practical.

- Complete translations for Spanish, Portuguese, Japanese, Korean, and Hindi.
- Add optional countdown before recording.
- Add simple script import/export.
- Add mirror mode for external teleprompter glass.
- Add release signing for production distribution.

## License

CueCam is licensed under the [Apache License 2.0](LICENSE).
