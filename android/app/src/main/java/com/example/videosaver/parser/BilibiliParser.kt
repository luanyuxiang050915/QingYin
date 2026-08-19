package com.example.videosaver.parser

import com.example.videosaver.model.VideoInfo
import com.example.videosaver.net.Http
import okhttp3.Request
import org.json.JSONObject

object BilibiliParser : VideoParser {
    override val platform = "B站"

    private val b23Regex = Regex("https?://b23\\.tv/[A-Za-z0-9]+")
    private val bvRegex = Regex("BV[0-9A-Za-z]{10}")

    override fun matches(text: String): Boolean =
        Regex("(bilibili\\.com|b23\\.tv)").containsMatchIn(text)

    override suspend fun parse(text: String, context: android.content.Context): VideoInfo {
        var input = text

        b23Regex.find(input)?.let { short ->
            val resp = Http.client.newCall(
                Request.Builder().url(short.value).header("User-Agent", Http.UA_DESKTOP).build()
            ).execute()
            resp.use {
                if (!it.isSuccessful) throw ParseException("短链跳转失败: HTTP ${it.code}")
                input = it.request.url.toString()
            }
        }

        val bvid = bvRegex.find(input)?.value
            ?: throw ParseException("未找到 B 站 BV 号")

        val info = getJson("https://api.bilibili.com/x/web-interface/view?bvid=$bvid", null)
        val data = info.optJSONObject("data")
            ?: throw ParseException("B站接口无数据（可能被限流）")
        val title = data.optString("title").ifBlank { "B站视频" }
        val author = data.optJSONObject("owner")?.optString("name").orEmpty()
        val cover = data.optString("pic")
        val cid = data.optLong("cid")
        val duration = data.optLong("duration")

        val play = getJson(
            "https://api.bilibili.com/x/player/playurl" +
                "?bvid=$bvid&cid=$cid&qn=64&platform=html5&high_quality=1",
            "https://www.bilibili.com"
        )
        val durl = play.optJSONObject("data")?.optJSONArray("durl")
            ?: throw ParseException("B站播放地址获取失败")
        val first = durl.optJSONObject(0)
            ?: throw ParseException("B站播放地址为空")
        val videoUrl = first.optString("url").ifBlank {
            first.optJSONArray("backup_url")?.optString(0).orEmpty()
        }
        if (videoUrl.isBlank()) throw ParseException("B站播放地址为空")

        return VideoInfo(platform, title, author, cover, videoUrl, duration)
    }

    private suspend fun getJson(url: String, referer: String?): JSONObject {
        val builder = Request.Builder().url(url).header("User-Agent", Http.UA_DESKTOP)
        if (referer != null) builder.header("Referer", referer)
        val body = Http.client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw ParseException("B站接口请求失败: HTTP ${resp.code}")
            resp.body?.string() ?: throw ParseException("B站接口返回为空")
        }
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            throw ParseException("B站接口返回格式异常")
        }
    }
}
