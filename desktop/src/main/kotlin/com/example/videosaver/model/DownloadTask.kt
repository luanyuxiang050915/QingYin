package com.example.videosaver.model

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
    /** 已保存到本地「视频去水印」文件夹的文件；视频只有 1 个，图集有多张 */
    val savedFiles: List<File> = emptyList(),
    val error: String? = null,
)
