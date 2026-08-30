package com.example.videosaver.parser

import com.example.videosaver.model.VideoInfo
import org.json.JSONObject
import java.io.File

/**
 * 通用链接解析器：基于 yt-dlp（支持 1000+ 站点，含 Pornhub 等）
 *
 * 流程：`yt-dlp --dump-json` 拿元数据 → `yt-dlp --get-url -f best[ext=mp4]` 拿直链
 *      → 交给现有下载器（Range 断点续传）。仅支持直链 mp4 格式（不需要 ffmpeg）。
 *
 * 反爬加固（Pornhub WAF 会偶发/持续 410 Gone、403）：
 *  - 域名切换：cn.pornhub.com 与 www.pornhub.com 互换重试；
 *  - 自动重试：最多 3 次，间隔递增，--extractor-retries 兜底；
 *  - UA 轮换：每次重试换一个浏览器 UA。
 *
 * yt-dlp 查找顺序：系统属性 qingyin.ytdlp → 安装目录 app/yt-dlp.exe →
 *     工作目录 yt-dlp.exe → 系统 PATH。
 */
object YtDlpParser : VideoParser {

    override val platform = "通用"

    private val urlRegex = Regex("https?://[^\\s\"'<>]+")

    /** 匹配任意 URL（优先级最低，作为兜底解析器） */
    override fun matches(text: String): Boolean = urlRegex.containsMatchIn(text)

    override suspend fun parse(text: String): VideoInfo {
        val url = urlRegex.find(text)?.value
            ?: throw ParseException("未找到链接，请粘贴视频页面 URL")

        // 域名变体：pornhub 的 cn/www 互换，其余站点保持原样
        val variants = mutableListOf(url)
        if (url.contains("cn.pornhub.com")) {
            variants.add(url.replace("cn.pornhub.com", "www.pornhub.com"))
        } else if (url.contains("www.pornhub.com")) {
            variants.add(url.replace("www.pornhub.com", "cn.pornhub.com"))
        }

        var lastError: Exception? = null
        for (variant in variants) {
            try {
                return parseOnce(variant)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: ParseException("解析失败，请稍后重试")
    }

    private fun parseOnce(url: String): VideoInfo {
        // 1) 元数据
        val metaOut = runYtDlpRetry("--dump-json", "--no-warnings", url)
        val meta = try {
            JSONObject(metaOut.trim().lineSequence().last { it.isNotBlank() })
        } catch (e: Exception) {
            throw ParseException("yt-dlp 返回格式异常：${metaOut.takeLast(200)}")
        }

        // 2) 最佳直链 mp4（protocol^=http 排除 m3u8 分片流，不需要 ffmpeg）
        val direct = try {
            runYtDlpRetry("--get-url", "-f", "best[ext=mp4][protocol^=http]/best", "--no-warnings", url)
                .trim().lineSequence().last { it.isNotBlank() }
        } catch (e: ParseException) {
            throw ParseException("获取直链失败：${e.message}")
        }
        if (direct.contains(".m3u8") || direct.contains("m3u8")) {
            throw ParseException("该站点只有分片流（m3u8），暂不支持（需要 ffmpeg）")
        }

        // 防盗链 Referer：用视频页面域名（如 Pornhub CDN 需要 pornhub 页面做来源）
        val referer = Regex("^https?://([^/]+)").find(url)
            ?.groupValues?.get(1)
            ?.let { "https://$it/" }
            .orEmpty()

        return VideoInfo(
            platform = platform,
            title = meta.optString("title").trim().ifBlank { "视频" },
            author = meta.optString("uploader").ifBlank { "" },
            coverUrl = meta.optString("thumbnail").ifBlank { "" },
            videoUrl = direct,
            durationSec = meta.optLong("duration"),
            referer = referer,
        )
    }

    /** 轮换使用的浏览器 UA（Pornhub WAF 可能标记特定 UA） */
    private val UA_POOL = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    )

    /**
     * 带重试的 yt-dlp 调用（反爬偶发 410/403/空输出时自动重试，UA 逐次轮换）
     */
    private fun runYtDlpRetry(vararg args: String, maxAttempts: Int = 3): String {
        var lastError: ParseException? = null
        for (attempt in 1..maxAttempts) {
            val ua = UA_POOL[(attempt - 1) % UA_POOL.size]
            try {
                val output = runYtDlp(
                    "--extractor-retries", "3",
                    "--user-agent", ua,
                    *args,
                )
                if (output.isNotBlank()) return output
                lastError = ParseException("yt-dlp 输出为空")
            } catch (e: ParseException) {
                lastError = e
            }
            if (attempt < maxAttempts) {
                Thread.sleep(1500L * attempt)
            }
        }
        throw lastError ?: ParseException("yt-dlp 解析失败")
    }

    private fun runYtDlp(vararg args: String): String {
        val exe = findYtDlp()
            ?: throw ParseException("未找到 yt-dlp.exe，请将 yt-dlp.exe 放在程序目录下")
        val proc = try {
            ProcessBuilder(listOf(exe) + args)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            throw ParseException("启动 yt-dlp 失败：${e.message}")
        }
        val output = proc.inputStream.readBytes().toString(Charsets.UTF_8)
        val exit = proc.waitFor()
        if (exit != 0) {
            throw ParseException("yt-dlp 解析失败：${output.trim().takeLast(200)}")
        }
        return output
    }

    private fun findYtDlp(): String? {
        System.getProperty("qingyin.ytdlp")?.takeIf { File(it).exists() }?.let { return it }
        // 运行 jar 所在目录（jpackage 安装包结构：<安装目录>/app/yt-dlp.exe）
        runCatching {
            YtDlpParser::class.java.protectionDomain.codeSource?.location?.toURI()?.let { File(it) }
        }.getOrNull()?.let { jarFile ->
            if (jarFile.isFile) jarFile.parentFile?.let { dir ->
                File(dir, "yt-dlp.exe").takeIf { it.exists() }?.let { return it.absolutePath }
            }
        }
        listOf(
            File(System.getProperty("user.dir"), "app/yt-dlp.exe"),
            File(System.getProperty("user.dir"), "yt-dlp.exe"),
        ).firstOrNull { it.exists() }?.let { return it.absolutePath }
        System.getenv("PATH")?.split(";")?.forEach { d ->
            val f = File(d.trim(), "yt-dlp.exe")
            if (f.exists()) return f.absolutePath
        }
        return null
    }
}
