package com.wordhint.teleprompter

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordingOutput {
    const val MIME_TYPE = "video/mp4"
    const val ALBUM_NAME = "提词助手"

    fun displayName(now: Date = Date()): String =
        "VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)}.mp4"

    fun relativePath(movieDirectory: String): String =
        "$movieDirectory/$ALBUM_NAME"
}
