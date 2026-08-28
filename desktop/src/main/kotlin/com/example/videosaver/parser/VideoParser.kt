package com.example.videosaver.parser

import com.example.videosaver.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ParseException(message: String) : Exception(message)

/** 平台解析器：纯 JVM 代码，与 Android 版共用同一套实现 */
interface VideoParser {
    val platform: String
    fun matches(text: String): Boolean
    suspend fun parse(text: String): VideoInfo
}

object VideoParserManager {
    /**
     * 解析器优先级：B站/快手/X/小红书/微博（自研，快速）
     * → 通用（YtDlpParser，兜底，支持 Pornhub 等 1000+ 站点）
     * 抖音桌面版暂不支持（需 WebView 浏览器方案，仅 Android 可用）。
     */
    private val parsers: List<VideoParser> =
        listOf(
            BilibiliParser,
            KuaishouParser,
            XParser,
            XiaohongshuParser,
            WeiboParser,
            YtDlpParser,
        )

    fun detect(text: String): VideoParser? = parsers.firstOrNull { it.matches(text) }

    suspend fun parse(text: String): VideoInfo {
        val parser = detect(text)
            ?: throw ParseException("未找到可用的解析器，请粘贴有效的链接")
        return withContext(Dispatchers.IO) { parser.parse(text) }
    }
}
