package com.example.videosaver

import com.example.videosaver.download.DownloadOutcome
import com.example.videosaver.download.VideoDownloader
import com.example.videosaver.model.DownloadStatus
import com.example.videosaver.model.DownloadTask
import com.example.videosaver.model.VideoInfo
import com.example.videosaver.parser.ParseException
import com.example.videosaver.parser.VideoParserManager
import com.example.videosaver.save.DesktopSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiState(
    val parsing: Boolean = false,
    val video: VideoInfo? = null,
    val tasks: List<DownloadTask> = emptyList(),
    val message: String? = null,
)

/** 桌面版 ViewModel（与 Android 版逻辑一致，保存改为写「下载\视频去水印」文件夹） */
class DesktopViewModel {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val downloader = VideoDownloader()
    private val saver = DesktopSaver()

    private val tempDir: File = File(System.getProperty("java.io.tmpdir"), "qingyin-downloads")

    fun dispose() {
        scope.cancel()
    }

    fun parse(text: String) {
        if (text.isBlank()) {
            _state.update { it.copy(message = "请先粘贴链接") }
            return
        }
        scope.launch {
            _state.update { it.copy(parsing = true, message = null, video = null) }
            try {
                val video = VideoParserManager.parse(text.trim())
                _state.update {
                    it.copy(parsing = false, video = video, message = "解析成功，可开始下载")
                }
            } catch (e: ParseException) {
                _state.update { it.copy(parsing = false, message = e.message ?: "解析失败") }
            } catch (e: Exception) {
                _state.update {
                    it.copy(parsing = false, message = "网络异常：${e.message ?: e::class.java.simpleName}")
                }
            }
        }
    }

    /** 开始下载当前解析结果，生成新任务 */
    fun startDownload() {
        val video = _state.value.video ?: return
        val id = System.currentTimeMillis()
        val task = DownloadTask(id = id, video = video, status = DownloadStatus.DOWNLOADING)
        _state.update { it.copy(tasks = listOf(task) + it.tasks) }
        runDownload(id)
    }

    fun pauseDownload(id: Long) {
        _state.update { s ->
            s.copy(
                tasks = s.tasks.map {
                    if (it.id == id && it.status == DownloadStatus.DOWNLOADING) {
                        it.copy(status = DownloadStatus.PAUSED)
                    } else {
                        it
                    }
                }
            )
        }
    }

    fun resumeDownload(id: Long) {
        val task = _state.value.tasks.firstOrNull { it.id == id } ?: return
        if (task.status != DownloadStatus.PAUSED && task.status != DownloadStatus.FAILED) return
        _state.update { s ->
            s.copy(
                tasks = s.tasks.map {
                    if (it.id == id) it.copy(status = DownloadStatus.DOWNLOADING, error = null) else it
                }
            )
        }
        runDownload(id)
    }

    /** 删除任务：进行中/暂停的删临时文件；已完成的删除保存的文件 */
    fun deleteDownload(id: Long) {
        val task = _state.value.tasks.firstOrNull { it.id == id } ?: return
        _state.update { s -> s.copy(tasks = s.tasks.filterNot { it.id == id }) }
        scope.launch {
            withContext(Dispatchers.IO) {
                task.file?.let { saver.deleteFile(it) }
                if (task.status == DownloadStatus.COMPLETED) {
                    task.savedFiles.forEach { saver.deleteFile(it) }
                }
            }
        }
    }

    private fun runDownload(id: Long) {
        scope.launch {
            val dir = File(tempDir, "downloads").apply { mkdirs() }
            val video = _state.value.tasks.firstOrNull { it.id == id }?.video ?: return@launch
            val safeTitle = video.title
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(40)
                .ifBlank { "video" }
            // 图集走单独的逐张下载流程
            if (video.imageUrls.isNotEmpty()) {
                runImageDownload(id, video, dir, safeTitle)
                return@launch
            }

            // 文件名带任务 id，暂停后继续用同一个文件实现断点续传
            val file = File(dir, "${safeTitle}_$id.mp4")

            _state.update { s ->
                s.copy(
                    tasks = s.tasks.map {
                        if (it.id == id) {
                            it.copy(status = DownloadStatus.DOWNLOADING, file = file, error = null)
                        } else {
                            it
                        }
                    }
                )
            }

            try {
                val outcome = downloader.download(
                    url = video.videoUrl,
                    dest = file,
                    onProgress = { downloaded, total ->
                        _state.update { s ->
                            val t = s.tasks.firstOrNull { it.id == id } ?: return@update s
                            val progress =
                                if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                            s.copy(
                                tasks = s.tasks.map {
                                    if (it.id == id) {
                                        it.copy(
                                            progress = progress,
                                            bytesDownloaded = downloaded,
                                            totalBytes = total,
                                        )
                                    } else {
                                        it
                                    }
                                }
                            )
                        }
                    },
                    shouldStop = {
                        _state.value.tasks.firstOrNull { it.id == id }?.status !=
                            DownloadStatus.DOWNLOADING
                    },
                )

                when (outcome) {
                    is DownloadOutcome.Paused -> {
                        _state.update { s ->
                            s.copy(
                                tasks = s.tasks.map {
                                    if (it.id == id) it.copy(status = DownloadStatus.PAUSED) else it
                                }
                            )
                        }
                    }
                    is DownloadOutcome.Completed -> {
                        val saved = saver.saveToDownloads(outcome.file)
                        outcome.file.delete()
                        _state.update { s ->
                            s.copy(
                                message = "已保存到「下载\\视频去水印」文件夹",
                                tasks = s.tasks.map {
                                    if (it.id == id) {
                                        it.copy(
                                            status = DownloadStatus.COMPLETED,
                                            progress = 1f,
                                            savedFiles = listOf(saved),
                                            file = null,
                                        )
                                    } else {
                                        it
                                    }
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { s ->
                    s.copy(
                        tasks = s.tasks.map {
                            if (it.id == id) {
                                it.copy(status = DownloadStatus.FAILED, error = e.message ?: "下载失败")
                            } else {
                                it
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * 图集下载：逐张下载到临时目录（已下载完整的跳过，实现断点续传），
     * 全部完成后统一复制到「视频去水印」文件夹再清理临时目录。
     */
    private fun runImageDownload(id: Long, video: VideoInfo, parentDir: File, safeTitle: String) {
        scope.launch {
            val imgDir = File(parentDir, "${safeTitle}_$id").apply { mkdirs() }
            _state.update { s ->
                s.copy(
                    tasks = s.tasks.map {
                        if (it.id == id) {
                            it.copy(status = DownloadStatus.DOWNLOADING, file = imgDir, error = null)
                        } else {
                            it
                        }
                    }
                )
            }

            val total = video.imageUrls.size
            try {
                var paused = false
                for ((index, url) in video.imageUrls.withIndex()) {
                    val stopped = _state.value.tasks.firstOrNull { it.id == id }?.status !=
                        DownloadStatus.DOWNLOADING
                    if (stopped) {
                        paused = true
                        break
                    }
                    val ext = when {
                        url.contains(".webp") -> "webp"
                        url.contains(".png") -> "png"
                        else -> "jpg"
                    }
                    val dest = File(imgDir, "${safeTitle}_${id}_${(index + 1).toString().padStart(2, '0')}.$ext")
                    if (dest.exists()) {
                        // 之前已完整下载的图片直接跳过
                        updateImageProgress(id, (index + 1).toFloat() / total)
                        continue
                    }
                    try {
                        downloader.download(
                            url = url,
                            dest = dest,
                            onProgress = { downloaded, totalBytes ->
                                val per =
                                    if (totalBytes > 0) (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                                updateImageProgress(id, (index + per) / total)
                            },
                            shouldStop = { false },
                        )
                    } catch (e: Exception) {
                        dest.delete()
                        throw e
                    }
                }

                if (paused) {
                    _state.update { s ->
                        s.copy(
                            tasks = s.tasks.map {
                                if (it.id == id) it.copy(status = DownloadStatus.PAUSED) else it
                            }
                        )
                    }
                    return@launch
                }

                val files = imgDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
                val saved = files.map { saver.saveToDownloads(it) }
                imgDir.deleteRecursively()
                _state.update { s ->
                    s.copy(
                        message = "已保存 ${saved.size} 张图片到「下载\\视频去水印」文件夹",
                        tasks = s.tasks.map {
                            if (it.id == id) {
                                it.copy(
                                    status = DownloadStatus.COMPLETED,
                                    progress = 1f,
                                    savedFiles = saved,
                                    file = null,
                                )
                            } else {
                                it
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                _state.update { s ->
                    s.copy(
                        tasks = s.tasks.map {
                            if (it.id == id) {
                                it.copy(status = DownloadStatus.FAILED, error = e.message ?: "下载失败")
                            } else {
                                it
                            }
                        }
                    )
                }
            }
        }
    }

    private fun updateImageProgress(id: Long, progress: Float) {
        _state.update { s ->
            s.copy(
                tasks = s.tasks.map {
                    if (it.id == id) it.copy(progress = progress.coerceIn(0f, 1f)) else it
                }
            )
        }
    }
}
