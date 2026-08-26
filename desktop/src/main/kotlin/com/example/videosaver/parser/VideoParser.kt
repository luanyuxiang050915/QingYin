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
    /** 桌面版支持平台（抖音暂不支持：需 WebView 浏览器方案，仅 Android 可用） */
    private val parsers: List<VideoParser> =
        listOf(
            BilibiliParser,
            KuaishouParser,
            XParser,
            XiaohongshuParser,
            WeiboParser,
        )

    fun detect(text: String): VideoParser? = parsers.firstOrNull { it.matches(text) }

    suspend fun parse(text: String): VideoInfo {
        val parser = detect(text)
            ?: throw ParseException("暂不支持该链接，桌面版支持：B站 / 快手 / X(推特) / 小红书 / 微博（抖音暂未支持）")
        return withContext(Dispatchers.IO) { parser.parse(text) }
    }
}
