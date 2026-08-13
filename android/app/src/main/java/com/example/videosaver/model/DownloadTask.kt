package com.example.videosaver.model

import android.net.Uri
import java.io.File

enum class DownloadStatus {
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
}

data class DownloadTask(
    val id: Long,
    val video: VideoInfo,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val file: File? = null,
    val savedUri: Uri? = null,
    val error: String? = null,
)
