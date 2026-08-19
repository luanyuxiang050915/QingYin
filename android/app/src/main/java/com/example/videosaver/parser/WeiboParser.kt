package com.example.videosaver.parser

import com.example.videosaver.model.VideoInfo
import com.example.videosaver.net.Http
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object WeiboParser : VideoParser {
    override val platform = "微博"

    private val urlRegex = Regex(
        "https?://[a-zA-Z0-9.-]*(?:weibo\\.com|weibo\\.cn)/[A-Za-z0-9?&=/%._~:#+@-]*"
    )
    private val midRegex =
        Regex("(?:m\\.weibo\\.cn/status|weibo\\.com/[A-Za-z0-9_]+)/(\\d+)")
    private val tvRegex = Regex("weibo\\.com/tv/show/1034:\\d+")

    override fun matches(text: String): Boolean =
        Regex("(weibo\\.com|weibo\\.cn)").containsMatchIn(text)

    override suspend fun parse(text: String, context: android.content.Context): VideoInfo {
        val rawUrl = urlRegex.find(text)?.value
            ?: throw ParseException("未找到微博链接，请粘贴分享链接")
        if (tvRegex.containsMatchIn(rawUrl)) {
            throw ParseException("该微博视频链接格式暂不支持，请从微博 App 点分享→复制链接后重试")
        }
        val mid = midRegex.find(rawUrl)?.groupValues?.get(1)
            ?: throw ParseException("无法从链接中提取微博 ID：$rawUrl")

        val tid = fetchVisitorTid()
        val body = fetchStatus(mid, tid)
        val data = body.optJSONObject("data")
            ?: throw ParseException("微博接口无数据（可能被限流）")
        val pageInfo = data.optJSONObject("page_info")
            ?: throw ParseException("该微博没有视频")
        val mediaInfo = pageInfo.optJSONObject("media_info")
        val videoUrl = mediaInfo?.optString("stream_url_hd")?.takeIf { it.isNotBlank() }
            ?: mediaInfo?.optString("stream_url")?.takeIf { it.isNotBlank() }
            ?: throw ParseException("未找到视频地址（可能被风控，可切换WiFi/流量后重试）")
        val title = pageInfo.optString("page_title")
            .ifBlank { pageInfo.optString("title").ifBlank { "微博视频" } }
            .trim()
        val author = data.optJSONObject("user")?.optString("screen_name").orEmpty()
        val cover = pageInfo.optJSONObject("page_pic")?.optString("url").orEmpty()
        val duration = mediaInfo?.optLong("duration") ?: 0

        return VideoInfo(platform, title, author, cover, videoUrl, duration)
    }

    /** 生成微博访客票据，绕过"访客系统"验证 */
    private suspend fun fetchVisitorTid(): String {
        val form = "cb=gen_callback&fp=&t=${System.currentTimeMillis() / 1000}"
        val resp = Http.client.newCall(
            Request.Builder()
                .url("https://passport.weibo.com/visitor/genvisitor")
                .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .header("User-Agent", Http.UA_DESKTOP)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()
        ).execute()
        resp.use {
            if (!it.isSuccessful) throw ParseException("微博访客验证失败: HTTP ${it.code}")
            val body = it.body?.string() ?: throw ParseException("微博访客验证返回为空")
            return Regex("\"tid\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?: throw ParseException("微博访客验证失败")
        }
    }

    private suspend fun fetchStatus(mid: String, tid: String): JSONObject {
        val body = Http.client.newCall(
            Request.Builder()
                .url("https://m.weibo.cn/statuses/show?id=$mid")
                .header("User-Agent", Http.UA_MOBILE)
                .header("Accept", "application/json, text/plain, */*")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://m.weibo.cn/")
                .header("Cookie", "TMPL=$tid")
                .build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw ParseException("微博接口请求失败: HTTP ${resp.code}")
            resp.body?.string() ?: throw ParseException("微博接口返回为空")
        }
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            throw ParseException("微博接口返回格式异常")
        }
    }
}
