package com.example.videosaver.download

import com.example.videosaver.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

sealed class DownloadOutcome {
    data class Completed(val file: File) : DownloadOutcome()
    object Paused : DownloadOutcome()
}

class VideoDownloader {

    /**
     * 支持暂停/续传的下载：
     * - 目标文件已存在时，自动从已有字节数继续（服务端需支持 Range，不支持则从头下载）；
     * - [shouldStop] 返回 true 时停止并保留已下载部分，之后可继续；
     * - [referer] 显式指定防盗链 Referer（页面域名），为空时按已知平台/直链同域名推断。
     */
    suspend fun download(
        url: String,
        dest: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        shouldStop: () -> Boolean,
        referer: String? = null,
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        val resumeFrom = if (dest.exists()) dest.length() else 0L
        val builder = Request.Builder().url(url).header("User-Agent", Http.UA_MOBILE)
        if (!referer.isNullOrBlank()) {
            // 解析器提供的页面域名（如 Pornhub CDN 需要 pornhub 页面做来源）
            builder.header("Referer", referer)
        } else if (url.contains("bilivideo.com") || url.contains("bilibili")) {
            builder.header("Referer", "https://www.bilibili.com/")
        } else if (url.contains("twimg.com")) {
            builder.header("Referer", "https://x.com/")
        } else if (url.contains("weibocdn.com")) {
            builder.header("Referer", "https://weibo.com/")
        } else {
            // 通用兜底：按视频 URL 的域名带 Referer
            val host = runCatching { url.toHttpUrlOrNull()?.host }.getOrNull()
            if (!host.isNullOrBlank()) {
                builder.header("Referer", "https://$host/")
            }
        }
        if (resumeFrom > 0) {
            builder.header("Range", "bytes=$resumeFrom-")
        }

        Http.client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("下载失败: HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("响应体为空")
            when {
                resp.code == 206 -> {
                    val total =
                        if (body.contentLength() > 0) resumeFrom + body.contentLength() else resumeFrom
                    streamToFile(
                        dest = dest,
                        input = body.byteStream(),
                        append = true,
                        offset = resumeFrom,
                        total = total,
                        onProgress = onProgress,
                        shouldStop = shouldStop,
                    )
                }
                resp.code == 200 -> {
                    streamToFile(
                        dest = dest,
                        input = body.byteStream(),
                        append = false,
                        offset = 0,
                        total = body.contentLength(),
                        onProgress = onProgress,
                        shouldStop = shouldStop,
                    )
                }
                else -> throw IOException("下载失败: HTTP ${resp.code}")
            }
        }

        if (shouldStop()) DownloadOutcome.Paused else DownloadOutcome.Completed(dest)
    }

    private fun streamToFile(
        dest: File,
        input: InputStream,
        append: Boolean,
        offset: Long,
        total: Long,
        onProgress: (Long, Long) -> Unit,
        shouldStop: () -> Boolean,
    ) {
        FileOutputStream(dest, append).use { output ->
            val buffer = ByteArray(64 * 1024)
            var downloaded = offset
            input.use { inp ->
                while (true) {
                    if (shouldStop()) break
                    val n = inp.read(buffer)
                    if (n <= 0) break
                    output.write(buffer, 0, n)
                    downloaded += n
                    onProgress(downloaded, total)
                }
            }
        }
    }
}
