package com.example.videosaver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videosaver.download.DownloadOutcome
import com.example.videosaver.download.VideoDownloader
import com.example.videosaver.model.DownloadStatus
import com.example.videosaver.model.DownloadTask
import com.example.videosaver.model.VideoInfo
import com.example.videosaver.parser.ParseException
import com.example.videosaver.parser.VideoParserManager
import com.example.videosaver.save.MediaSaver
import kotlinx.coroutines.Dispatchers
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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val downloader = VideoDownloader()
    private val saver = MediaSaver(application)

    fun parse(text: String) {
        if (text.isBlank()) {
            _state.update { it.copy(message = "请先粘贴链接") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(parsing = true, message = null, video = null) }
            try {
                // 解析器内部自行调度线程：抖音失败会回退 WebView 方案（需主线程创建 WebView），
                // 其余平台在 IO 线程执行网络请求
                val video = VideoParserManager.parse(text.trim(), getApplication())
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

    /** 删除任务：进行中/暂停的删临时文件；已完成的删除相册中的文件 */
    fun deleteDownload(id: Long) {
        val task = _state.value.tasks.firstOrNull { it.id == id } ?: return
        _state.update { s -> s.copy(tasks = s.tasks.filterNot { it.id == id }) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                task.file?.let { f ->
                    if (f.exists()) {
                        // 图集的临时文件是一个目录
                        if (f.isDirectory) f.deleteRecursively() else f.delete()
                    }
                }
                if (task.status == DownloadStatus.COMPLETED) {
                    task.savedUris.forEach { saver.deleteSaved(it) }
                }
            }
        }
    }

    private fun runDownload(id: Long) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val dir = File(app.cacheDir, "downloads").apply { mkdirs() }
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
                // 速度/剩余时间计算（onProgress 高频回调）
                var lastTime = System.currentTimeMillis()
                var lastBytes = 0L
                val outcome = downloader.download(
                    url = video.videoUrl,
                    dest = file,
                    referer = video.referer.ifBlank { null },
                    onProgress = { downloaded, total ->
                        val now = System.currentTimeMillis()
                        val dt = now - lastTime
                        val db = downloaded - lastBytes
                        val speed = if (dt > 0) db * 1000 / dt else 0L
                        lastTime = now
                        lastBytes = downloaded
                        val eta = if (speed > 0 && total > downloaded) (total - downloaded) / speed else 0L
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
                                            speedBps = speed,
                                            etaSec = eta,
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
                        val uri = saver.saveToGallery(outcome.file)
                        outcome.file.delete()
                        _state.update { s ->
                            s.copy(
                                message = "已保存到相册「视频去水印」",
                                tasks = s.tasks.map {
                                    if (it.id == id) {
                                        it.copy(
                                            status = DownloadStatus.COMPLETED,
                                            progress = 1f,
                                            savedUris = listOf(uri),
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
     * 全部完成后统一存入相册再清理临时文件。
     * 单张图片很小，暂停在两张图片之间生效；不完整的图片会被删除，重试时重新下载。
     */
    private suspend fun runImageDownload(id: Long, video: VideoInfo, parentDir: File, safeTitle: String) {
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
                        referer = video.referer.ifBlank { null },
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
                return
            }

            val files = imgDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
            val uris = files.map { saver.saveToGallery(it, imageMime(it.name)) }
            imgDir.deleteRecursively()
            _state.update { s ->
                s.copy(
                    message = "已保存 ${uris.size} 张图片到相册「视频去水印」",
                    tasks = s.tasks.map {
                        if (it.id == id) {
                            it.copy(
                                status = DownloadStatus.COMPLETED,
                                progress = 1f,
                                savedUris = uris,
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

    private fun updateImageProgress(id: Long, progress: Float) {
        _state.update { s ->
            s.copy(
                tasks = s.tasks.map {
                    if (it.id == id) it.copy(progress = progress.coerceIn(0f, 1f)) else it
                }
            )
        }
    }

    private fun imageMime(fileName: String): String = when {
        fileName.endsWith(".webp") -> "image/webp"
        fileName.endsWith(".png") -> "image/png"
        else -> "image/jpeg"
    }
}
