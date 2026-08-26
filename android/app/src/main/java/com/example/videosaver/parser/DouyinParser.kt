package com.example.videosaver.parser

import com.example.videosaver.model.VideoInfo
import com.example.videosaver.net.Http
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

object DouyinParser : VideoParser {
    override val platform = "抖音"

    private val urlRegex = Regex(
        "https?://[a-zA-Z0-9.-]*(?:douyin|iesdouyin)\\.com/[A-Za-z0-9?&=/%._~:#+@-]*"
    )
    private val idRegex = Regex("/(?:video|note)/(\\d+)")
    private val modalIdRegex = Regex("[?&]modal_id=(\\d+)")

    override fun matches(text: String): Boolean =
        Regex("(v\\.douyin\\.com|www\\.douyin\\.com|iesdouyin\\.com|douyin\\.com)")
            .containsMatchIn(text)

    override suspend fun parse(text: String): VideoInfo {
        val rawUrl = urlRegex.find(text)?.value
            ?: throw ParseException("未找到抖音链接，请粘贴完整的分享文本")
        val client = Http.client

        // 短链先跟随跳转，拿到真实页面地址
        var finalUrl = rawUrl
        var fullShareUrl: String? = null
        if (finalUrl.contains("v.douyin.com")) {
            finalUrl = followRedirect(client, rawUrl)
            fullShareUrl = finalUrl
        }

        val videoId = idRegex.find(finalUrl)?.groupValues?.get(1)
            ?: modalIdRegex.find(finalUrl)?.groupValues?.get(1)
            ?: throw ParseException("无法从链接中提取作品 ID：$finalUrl")

        // 图集（/note/）和视频（/video/）对应不同的分享页，多策略尝试提高成功率
        val isNote = finalUrl.contains("/note/")
        val shareUrl = if (isNote) {
            "https://www.iesdouyin.com/share/note/$videoId/"
        } else {
            "https://www.iesdouyin.com/share/video/$videoId/"
        }
        val fallbackShareUrl = if (isNote) {
            "https://www.iesdouyin.com/share/video/$videoId/"
        } else {
            "https://www.iesdouyin.com/share/note/$videoId/"
        }
        val attempts = mutableListOf<Pair<String, String>>()
        attempts.add(shareUrl to "分享页")
        fullShareUrl?.let { attempts.add(it to "完整跳转链接") }
        attempts.add(fallbackShareUrl to "备用分享页")

        var lastError: Exception? = null
        for ((url, label) in attempts) {
            try {
                return parseSharePage(client, url, label)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: ParseException("解析失败，请稍后重试")
    }

    private suspend fun followRedirect(client: OkHttpClient, url: String): String {
        val resp = client.newCall(
            Request.Builder().url(url).header("User-Agent", Http.UA_MOBILE).build()
        ).execute()
        resp.use {
            if (!it.isSuccessful && it.code !in 300..399) {
                throw ParseException("短链跳转失败: HTTP ${it.code}")
            }
            return it.request.url.toString()
        }
    }

    private suspend fun parseSharePage(client: OkHttpClient, url: String, label: String): VideoInfo {
        // 请求移动端分享页，数据在 window._ROUTER_DATA 中
        val html = client.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", Http.UA_MOBILE)
                .header("Referer", "https://www.douyin.com/")
                .build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw ParseException("$label: 抖音页面请求失败 HTTP ${resp.code}（可能被风控）")
            }
            resp.body?.string() ?: throw ParseException("$label: 抖音页面内容为空")
        }

        val routerJs = Regex(
            "window\\._ROUTER_DATA\\s*=\\s*(.*?)</script>",
            RegexOption.DOT_MATCHES_ALL
        ).find(html)?.groupValues?.get(1)
            ?: throw ParseException("$label: 页面数据缺失（可能被风控，可切换WiFi/流量后重试）")

        val root = try {
            JSONObject(routerJs.trim().removeSuffix(";"))
        } catch (e: Exception) {
            throw ParseException("$label: 页面数据解析失败")
        }

        val title = (findFirstString(root, "desc") ?: "").trim().ifBlank { "抖音作品" }
        val author = (findFirstString(root, "nickname") ?: "").trim()

        // 优先按视频解析：有 play_addr 的就是视频（保持原有行为）
        val playAddr = findFirstObject(root, "play_addr")
        if (playAddr != null) {
            val wmUrl = playAddr.optJSONArray("url_list")?.let { arr ->
                (0 until arr.length()).firstNotNullOfOrNull { i ->
                    arr.optString(i).takeIf { it.isNotBlank() }
                }
            } ?: throw ParseException("播放地址为空")

            val cleanUrl = wmUrl.replace("playwm", "play")
            val cover = findCoverUrl(root)
            val duration = findFirstLong(root, "duration") ?: 0
            return VideoInfo(platform, title, author, cover, cleanUrl, duration)
        }

        // 没有播放地址则是图文笔记：images 数组的 url_list 就是无水印原图直链
        val images = findImageUrls(root)
        if (images.isNotEmpty()) {
            val cover = findCoverUrl(root).ifBlank { images.first() }
            return VideoInfo(
                platform = platform,
                title = title,
                author = author,
                coverUrl = cover,
                videoUrl = "",
                durationSec = 0,
                imageUrls = images,
            )
        }
        throw ParseException("未找到视频或图片地址")
    }

    /** 递归查找图文作品的 images 数组，提取每张图 url_list 中第一个可用直链 */
    private fun findImageUrls(node: Any?): List<String> {
        when (node) {
            is JSONObject -> {
                val arr = node.optJSONArray("images")
                if (arr != null && arr.length() > 0) {
                    val urls = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val urlList = arr.optJSONObject(i)?.optJSONArray("url_list") ?: continue
                        val url = (0 until urlList.length()).firstNotNullOfOrNull { j ->
                            urlList.optString(j).takeIf { it.isNotBlank() }
                        } ?: continue
                        urls.add(url)
                    }
                    if (urls.isNotEmpty()) return urls
                }
                val iter = node.keys()
                while (iter.hasNext()) {
                    val r = findImageUrls(node.opt(iter.next()))
                    if (r.isNotEmpty()) return r
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val r = findImageUrls(node.opt(i))
                    if (r.isNotEmpty()) return r
                }
            }
        }
        return emptyList()
    }

    private fun findFirstObject(node: Any?, key: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.opt(key)?.let { if (it is JSONObject) return it }
                val iter = node.keys()
                while (iter.hasNext()) {
                    findFirstObject(node.opt(iter.next()), key)?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findFirstObject(node.opt(i), key)?.let { return it }
                }
            }
        }
        return null
    }

    private fun findFirstString(node: Any?, key: String): String? {
        when (node) {
            is JSONObject -> {
                node.opt(key)?.let { if (it is String) return it }
                val iter = node.keys()
                while (iter.hasNext()) {
                    findFirstString(node.opt(iter.next()), key)?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findFirstString(node.opt(i), key)?.let { return it }
                }
            }
        }
        return null
    }

    private fun findFirstLong(node: Any?, key: String): Long? {
        when (node) {
            is JSONObject -> {
                node.opt(key)?.let {
                    if (it is Number) return it.toLong()
                }
                val iter = node.keys()
                while (iter.hasNext()) {
                    findFirstLong(node.opt(iter.next()), key)?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findFirstLong(node.opt(i), key)?.let { return it }
                }
            }
        }
        return null
    }

    private fun findCoverUrl(node: Any?): String {
        if (node is JSONObject) {
            (node.opt("cover") as? JSONObject)?.optJSONArray("url_list")?.let { arr ->
                if (arr.length() > 0) return arr.optString(0)
            }
            val iter = node.keys()
            while (iter.hasNext()) {
                findCoverUrl(node.opt(iter.next())).takeIf { it.isNotBlank() }?.let { return it }
            }
        } else if (node is JSONArray) {
            for (i in 0 until node.length()) {
                findCoverUrl(node.opt(i)).takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return ""
    }
}
