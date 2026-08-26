package com.example.videosaver.parser

import com.example.videosaver.model.VideoInfo
import com.example.videosaver.net.Http
import okhttp3.Request
import org.json.JSONArray

object KuaishouParser : VideoParser {
    override val platform = "快手"

    private val urlRegex = Regex(
        "https?://[a-zA-Z0-9.-]*(?:kuaishou|gifshow)\\.com/[A-Za-z0-9?&=/%._~:#+@-]*"
    )
    private val idRegex = Regex("/(?:fw/photo|short-video)/([A-Za-z0-9]+)")

    override fun matches(text: String): Boolean =
        Regex("(kuaishou\\.com|gifshow\\.com)").containsMatchIn(text)

    override suspend fun parse(text: String): VideoInfo {
        val rawUrl = urlRegex.find(text)?.value
            ?: throw ParseException("未找到快手链接，请粘贴完整的分享文本")

        // 一次请求即可：v.kuaishou.com 短链会自动跳到 m.gifshow.com 分享页，响应里带全部数据
        val html: String
        val finalUrl: String
        Http.client.newCall(
            Request.Builder().url(rawUrl).header("User-Agent", Http.UA_MOBILE).build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw ParseException("快手页面请求失败: HTTP ${resp.code}（可能被风控）")
            }
            html = resp.body?.string() ?: throw ParseException("快手页面内容为空")
            finalUrl = resp.request.url.toString()
        }

        val photoId = idRegex.find(finalUrl)?.groupValues?.get(1)
            ?: throw ParseException("无法从链接中提取视频 ID：$finalUrl")

        val videoUrl = firstUrlOfArray(html, "mainMvUrls")
            ?: fallbackMp4(html)
            ?: throw ParseException("未找到视频播放地址（可能被风控，可切换WiFi/流量后重试）")
        val title = firstString(html, "caption")?.trim()?.ifBlank { "快手视频" } ?: "快手视频"
        val author = firstString(html, "user_name").orEmpty()
        val cover = firstUrlOfArray(html, "coverUrls").orEmpty()
        val durationMs = Regex("\"duration\":(\\d+)")
            .find(html)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val durationSec = if (durationMs > 1000) durationMs / 1000 else durationMs

        return VideoInfo(platform, title, author, cover, videoUrl, durationSec)
    }

    private fun firstUrlOfArray(html: String, key: String): String? {
        val m = Regex("\"$key\":(\\[.*?\\])", RegexOption.DOT_MATCHES_ALL).find(html)
            ?: return null
        return try {
            val arr = JSONArray(m.groupValues[1])
            if (arr.length() > 0) {
                arr.getJSONObject(0).optString("url").takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun firstString(html: String, key: String): String? {
        val m = Regex("\"$key\":\"(.*?)\"").find(html) ?: return null
        return try {
            org.json.JSONObject("{\"$key\":\"${m.groupValues[1]}\"}").optString(key)
        } catch (e: Exception) {
            m.groupValues[1]
        }
    }

    /** 兜底：直接搜页面里 upic 的 mp4 直链 */
    private fun fallbackMp4(html: String): String? =
        Regex("https://[a-zA-Z0-9.-]*\\.(?:yximgs|kwimgs)\\.com/upic/[^\"\\\\]+?\\.mp4[^\"\\\\]*")
            .find(html)?.value
}
