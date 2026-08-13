package com.example.videosaver.parser

import com.example.videosaver.model.VideoInfo

class ParseException(message: String) : Exception(message)

interface VideoParser {
    val platform: String
    fun matches(text: String): Boolean
    suspend fun parse(text: String): VideoInfo
}

object VideoParserManager {
    private val parsers: List<VideoParser> =
        listOf(
            DouyinParser,
            BilibiliParser,
            KuaishouParser,
            XParser,
            XiaohongshuParser,
            WeiboParser,
        )

    fun detect(text: String): VideoParser? = parsers.firstOrNull { it.matches(text) }

    suspend fun parse(text: String): VideoInfo {
        val parser = detect(text)
            ?: throw ParseException("暂不支持该链接，目前支持：抖音 / B站 / 快手 / X(推特) / 小红书 / 微博")
        return parser.parse(text)
    }
}
