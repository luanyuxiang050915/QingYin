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
    /** 已保存到相册的媒体 Uri；视频只有 1 个，图集有多张 */
    val savedUris: List<Uri> = emptyList(),
    val error: String? = null,
)
