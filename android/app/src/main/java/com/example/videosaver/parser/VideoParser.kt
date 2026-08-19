package com.example.videosaver.parser

import android.content.Context
import com.example.videosaver.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ParseException(message: String) : Exception(message)

interface VideoParser {
    val platform: String
    fun matches(text: String): Boolean
    suspend fun parse(text: String, context: Context): VideoInfo
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

    /**
     * 解析分享文本为作品信息。
     * - 抖音：先走快路径（解析分享页内嵌数据，纯网络请求），失败后回退到
     *   [DouyinWebViewParser]（WebView 真实浏览器，适配平台签名/风控改版）。
     * - 其余平台：网络请求放 IO 线程。
     */
    suspend fun parse(text: String, context: Context): VideoInfo {
        val parser = detect(text)
            ?: throw ParseException("暂不支持该链接，目前支持：抖音 / B站 / 快手 / X(推特) / 小红书 / 微博")
        if (parser is DouyinParser) {
            return try {
                withContext(Dispatchers.IO) { parser.parse(text, context) }
            } catch (e: ParseException) {
                DouyinWebViewParser(context).parse(text, context)
            }
        }
        return withContext(Dispatchers.IO) { parser.parse(text, context) }
    }
}
