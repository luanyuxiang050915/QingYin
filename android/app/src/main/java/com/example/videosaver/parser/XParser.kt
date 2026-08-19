package com.example.videosaver.parser

import com.example.videosaver.model.VideoInfo
import com.example.videosaver.net.Http
import okhttp3.Request
import org.json.JSONObject

object XParser : VideoParser {
    override val platform = "X(推特)"

    private val statusRegex =
        Regex("https?://(?:x|twitter)\\.com/[A-Za-z0-9_]{1,20}/status/(\\d+)")
    private val userRegex =
        Regex("https?://(?:x|twitter)\\.com/([A-Za-z0-9_]{1,20})/status/")
    private val tcoRegex = Regex("https?://t\\.co/[A-Za-z0-9]+")

    override fun matches(text: String): Boolean =
        Regex("(x\\.com|twitter\\.com|t\\.co)").containsMatchIn(text)

    override suspend fun parse(text: String, context: android.content.Context): VideoInfo {
        var statusUrl = statusRegex.find(text)?.value
        if (statusUrl == null) {
            // t.co 短链：跟随跳转拿真实推文链接
            val short = tcoRegex.find(text)?.value
                ?: throw ParseException("未找到 X 链接，请粘贴推文链接")
            val resp = Http.client.newCall(
                Request.Builder().url(short).header("User-Agent", Http.UA_DESKTOP).build()
            ).execute()
            resp.use {
                if (!it.isSuccessful) throw ParseException("短链跳转失败: HTTP ${it.code}")
                statusUrl = statusRegex.find(it.request.url.toString())?.value
            }
        }
        val url = statusUrl
        val match = url?.let { statusRegex.find(it) }
            ?: throw ParseException("无法从链接中提取推文 ID")
        val statusId = match.groupValues[1]
        val user = url?.let { userRegex.find(it) }?.groupValues?.get(1)

        var lastError: Exception? = null
        try {
            parseViaSyndication(statusId)?.let { return it }
        } catch (e: Exception) {
            lastError = e
        }
        if (user != null) {
            try {
                parseViaVxTwitter(user, statusId)?.let { return it }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: ParseException("该推文没有可下载的视频（可能是图片/文字推文）")
    }

    /** 官方 syndication 接口（无需登录），挑最高清的 mp4 */
    private suspend fun parseViaSyndication(statusId: String): VideoInfo? {
        val data = getJson("https://cdn.syndication.twimg.com/tweet-result?id=$statusId&token=!")
        val video = data.optJSONObject("video") ?: return null
        val variants = video.optJSONArray("variants") ?: return null
        var best: String? = null
        var bestArea = -1L
        for (i in 0 until variants.length()) {
            val v = variants.optJSONObject(i)
            if (v?.optString("type") == "video/mp4") {
                val src = v.optString("src")
                val m = Regex("(\\d+)x(\\d+)").find(src)
                val area = if (m != null) {
                    m.groupValues[1].toLong() * m.groupValues[2].toLong()
                } else {
                    0
                }
                if (area >= bestArea) {
                    bestArea = area
                    best = src
                }
            }
        }
        val videoUrl = best ?: return null
        val user = data.optJSONObject("user")
        return VideoInfo(
            platform = platform,
            title = data.optString("text").trim().ifBlank { "X 视频" },
            author = user?.optString("name").orEmpty(),
            coverUrl = video.optString("poster"),
            videoUrl = videoUrl,
            durationSec = video.optLong("durationMs") / 1000,
        )
    }

    /** 备用：vxtwitter 第三方解析接口 */
    private suspend fun parseViaVxTwitter(user: String, statusId: String): VideoInfo? {
        val data = getJson("https://api.vxtwitter.com/$user/status/$statusId")
        val media = data.optJSONArray("media_extended") ?: return null
        var videoUrl: String? = null
        var thumb = ""
        var durationMs = 0L
        for (i in 0 until media.length()) {
            val m = media.optJSONObject(i)
            if (m?.optString("type") == "video") {
                videoUrl = m.optString("url").takeIf { it.isNotBlank() } ?: continue
                thumb = m.optString("thumbnail_url")
                durationMs = m.optLong("duration_millis")
                break
            }
        }
        val url = videoUrl ?: return null
        return VideoInfo(
            platform = platform,
            title = data.optString("text").trim().ifBlank { "X 视频" },
            author = data.optString("user_name"),
            coverUrl = thumb,
            videoUrl = url,
            durationSec = durationMs / 1000,
        )
    }

    private suspend fun getJson(url: String): JSONObject {
        val body = Http.client.newCall(
            Request.Builder().url(url).header("User-Agent", Http.UA_DESKTOP).build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw ParseException("X 接口请求失败: HTTP ${resp.code}")
            resp.body?.string() ?: throw ParseException("X 接口返回为空")
        }
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            throw ParseException("X 接口返回格式异常")
        }
    }
}
