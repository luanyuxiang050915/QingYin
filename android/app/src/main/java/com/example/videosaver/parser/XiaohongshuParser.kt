package com.example.videosaver.parser

import com.example.videosaver.model.VideoInfo
import com.example.videosaver.net.Http
import okhttp3.Request
import org.json.JSONObject

object XiaohongshuParser : VideoParser {
    override val platform = "小红书"

    private val urlRegex = Regex(
        "https?://[a-zA-Z0-9.-]*(?:xiaohongshu\\.com|xhslink\\.cn)/[A-Za-z0-9?&=/%._~:#+@-]*"
    )
    private val idRegex = Regex("/(?:explore|discovery/item|item)/([0-9a-f]{24})")

    override fun matches(text: String): Boolean =
        Regex("(xiaohongshu\\.com|xhslink\\.cn)").containsMatchIn(text)

    override suspend fun parse(text: String, context: android.content.Context): VideoInfo {
        val rawUrl = urlRegex.find(text)?.value
            ?: throw ParseException("未找到小红书链接，请粘贴完整的分享文本")

        // xhslink.cn 短链会自动跳转到笔记页，响应里带全部数据
        val html: String
        val finalUrl: String
        Http.client.newCall(
            Request.Builder().url(rawUrl).header("User-Agent", Http.UA_MOBILE).build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw ParseException("小红书页面请求失败: HTTP ${resp.code}（可能被风控）")
            }
            html = resp.body?.string() ?: throw ParseException("小红书页面内容为空")
            finalUrl = resp.request.url.toString()
        }
        idRegex.find(finalUrl)?.groupValues?.get(1)
            ?: throw ParseException("无法从链接中提取笔记 ID：$finalUrl")

        val note = parseNoteData(html)
        if (note != null) {
            val h264 = note.optJSONObject("video")
                ?.optJSONObject("media")
                ?.optJSONObject("stream")
                ?.optJSONArray("h264")
            if (h264 != null && h264.length() > 0) {
                val first = h264.getJSONObject(0)
                val videoUrl = first.optString("masterUrl").ifBlank {
                    first.optJSONArray("backupUrls")?.optString(0).orEmpty()
                }
                if (videoUrl.isNotBlank()) {
                    return VideoInfo(
                        platform = platform,
                        title = note.optString("title").trim().ifBlank {
                            note.optString("desc").trim().ifBlank { "小红书视频" }
                        },
                        author = note.optJSONObject("user")?.optString("nickName").orEmpty(),
                        coverUrl = coverUrl(note),
                        videoUrl = videoUrl,
                        durationSec = first.optLong("videoDuration") / 1000,
                    )
                }
            }
        }

        // 兜底：直接抓页面里的 masterUrl
        val fallback = Regex("\"masterUrl\":\"(http[^\"]+)\"").find(html)?.groupValues?.get(1)
        if (fallback != null) {
            return VideoInfo(platform, "小红书视频", "", "", fallback, 0)
        }
        throw ParseException("页面数据缺失（可能被风控，可切换WiFi/流量后重试）")
    }

    private fun parseNoteData(html: String): JSONObject? {
        val m = Regex(
            "window\\.__SETUP_SERVER_STATE__\\s*=\\s*(\\{.*?\\})\\s*</script>",
            RegexOption.DOT_MATCHES_ALL
        ).find(html) ?: return null
        // 页面 JSON 里有 JS 特有的 undefined，先替换成 null
        val cleaned = m.groupValues[1].replace(Regex(":undefined(?=[,}])"), ":null")
        return try {
            JSONObject(cleaned)
                .optJSONObject("LAUNCHER_SSR_STORE_PAGE_DATA")
                ?.optJSONObject("noteData")
        } catch (e: Exception) {
            null
        }
    }

    private fun coverUrl(note: JSONObject): String {
        val list = note.optJSONArray("imageList") ?: return ""
        if (list.length() == 0) return ""
        val info = list.getJSONObject(0).optJSONArray("infoList") ?: return ""
        if (info.length() == 0) return ""
        return info.getJSONObject(0).optString("url")
    }
}
