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
                // 网络请求必须在 IO 线程执行，否则安卓会直接拦截（NetworkOnMainThreadException）
                val video = withContext(Dispatchers.IO) {
                    VideoParserManager.parse(text.trim())
                }
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
                task.file?.let { f -> if (f.exists()) f.delete() }
                if (task.status == DownloadStatus.COMPLETED) {
                    saver.deleteSaved(task.savedUri)
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
                                            savedUri = uri,
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
}
