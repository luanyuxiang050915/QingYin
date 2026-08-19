package com.example.videosaver.parser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.videosaver.model.VideoInfo
import com.example.videosaver.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 抖音解析器（WebView 真实浏览器方案）
 *
 * 背景：2026-08 抖音改版后，分享页不再内嵌作品数据，改为前端调用带 a_bogus 签名
 * 的接口异步获取；纯 HTTP 无法伪造真实浏览器状态（s_v_web_id / msToken / uifid）。
 *
 * 本方案：用 WebView 加载 www.douyin.com/note(或video)/{id} 页面，让页面自身的
 * JS 完成签名与环境校验，注入钩子截获 aweme/post、aweme/detail、slidesinfo 等
 * 接口响应，从 JSON 里提取图集原图直链 / 视频无水印直链。
 *
 * 已在 POC（poc/douyin_playwright_poc.js）验证通过。
 */
class DouyinWebViewParser(private val appContext: Context) : VideoParser {

    override val platform = "抖音"

    private val urlRegex = Regex(
        "https?://[a-zA-Z0-9.-]*(?:douyin|iesdouyin)\\.com/[A-Za-z0-9?&=/%._~:#+@-]*"
    )
    private val idRegex = Regex("/(?:video|note)/(\\d+)")
    private val modalIdRegex = Regex("[?&]modal_id=(\\d+)")

    /** 页面加载/接口等待总超时（毫秒） */
    private val timeoutMs = 15_000L

    override fun matches(text: String): Boolean =
        Regex("(v\\.douyin\\.com|www\\.douyin\\.com|iesdouyin\\.com|douyin\\.com)")
            .containsMatchIn(text)

    override suspend fun parse(text: String, context: Context): VideoInfo {
        // 1) 提取作品 ID 与类型（网络请求放 IO）
        val (awemeId, isNote) = withContext(Dispatchers.IO) { extractId(text) }
        // 2) WebView 必须在主线程创建与操作
        return withContext(Dispatchers.Main) {
            parseWithWebView(awemeId, isNote)
        }
    }

    /** 解析链接 → (作品ID, 是否图文：true=note / false=video / null=未知两个都试) */
    private fun extractId(text: String): Pair<String, Boolean?> {
        val rawUrl = urlRegex.find(text)?.value
            ?: throw ParseException("未找到抖音链接，请粘贴完整的分享文本")
        var finalUrl = rawUrl
        if (finalUrl.contains("v.douyin.com")) {
            val resp = Http.client.newCall(
                Request.Builder().url(rawUrl).header("User-Agent", Http.UA_DESKTOP).build()
            ).execute()
            resp.use {
                if (!it.isSuccessful) throw ParseException("短链跳转失败: HTTP ${it.code}")
                finalUrl = it.request.url.toString()
            }
        }
        val id = idRegex.find(finalUrl)?.groupValues?.get(1)
            ?: modalIdRegex.find(finalUrl)?.groupValues?.get(1)
            ?: throw ParseException("无法从链接中提取作品 ID：$finalUrl")
        val isNote = when {
            finalUrl.contains("/note/") -> true
            finalUrl.contains("/video/") -> false
            else -> null
        }
        return id to isNote
    }

    // ------------------------------------------------------------------
    // WebView 解析核心
    // ------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private suspend fun parseWithWebView(awemeId: String, isNote: Boolean?): VideoInfo =
        suspendCancellableCoroutine { cont ->
            val mainHandler = Handler(Looper.getMainLooper())
            val finished = AtomicBoolean(false)
            var webView: WebView? = null

            fun finish(info: VideoInfo?, error: ParseException?) {
                if (!finished.compareAndSet(false, true)) return
                if (error != null) cont.resumeWithException(error) else cont.resume(info!!)
            }

            // JS 钩子把截获的接口响应回传（在 WebView 后台线程调用）
            val bridge = object {
                @JavascriptInterface
                fun capture(json: String) {
                    val info = try {
                        parseAwemeJson(json, awemeId)
                    } catch (e: Exception) {
                        null
                    }
                    if (info != null) {
                        mainHandler.post {
                            webView?.stopLoading()
                            finish(info, null)
                        }
                    }
                }
            }

            val wv = WebView(appContext)
            webView = wv
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.userAgentString = Http.UA_DESKTOP
            wv.addJavascriptInterface(bridge, "AndroidBridge")
            wv.webViewClient = object : WebViewClient() {
                // 页面开始加载时注入 fetch/XHR 钩子（早于页面自身脚本）
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    view?.evaluateJavascript(JS_HOOK, null)
                }
            }

            // note 与 video 类型未知时先试 note，失败再试 video
            val urls = when (isNote) {
                true -> listOf("https://www.douyin.com/note/$awemeId")
                false -> listOf("https://www.douyin.com/video/$awemeId")
                null -> listOf(
                    "https://www.douyin.com/note/$awemeId",
                    "https://www.douyin.com/video/$awemeId",
                )
            }
            var urlIndex = 0

            fun loadNext() {
                if (urlIndex >= urls.size) {
                    finish(null, ParseException("抖音解析失败，页面数据获取失败，请稍后重试"))
                    return
                }
                wv.loadUrl(urls[urlIndex++])
            }

            // 超时兜底：还有备用页面就换页重试；否则尝试 DOM 图片提取
            fun onTimeout() {
                if (finished.get()) return
                if (urlIndex < urls.size) {
                    loadNext()
                    mainHandler.postDelayed({ onTimeout() }, timeoutMs)
                    return
                }
                wv.evaluateJavascript(DOM_EXTRACT_JS) { value ->
                    val info = parseDomJson(value, awemeId)
                    if (info != null) {
                        wv.stopLoading()
                        finish(info, null)
                    } else {
                        finish(null, ParseException("抖音解析超时，页面数据获取失败，请稍后重试"))
                    }
                }
            }
            mainHandler.postDelayed({ onTimeout() }, timeoutMs)

            // 协程取消时销毁 WebView，防止泄漏
            cont.invokeOnCancellation {
                mainHandler.post {
                    finished.set(true)
                    wv.stopLoading()
                    wv.destroy()
                }
            }

            loadNext()
        }

    // ------------------------------------------------------------------
    // 响应解析
    // ------------------------------------------------------------------

    /**
     * 从接口 JSON 里找目标作品，提取图集/视频信息。
     * 兼容 aweme_list（用户作品列表）、aweme_detail、aweme_details 结构。
     */
    private fun parseAwemeJson(json: String, awemeId: String): VideoInfo? {
        val root = JSONObject(json)
        val list = when {
            root.has("aweme_list") -> root.getJSONArray("aweme_list")
            root.has("aweme_detail") -> JSONArray().put(root.getJSONObject("aweme_detail"))
            root.has("aweme_details") -> root.getJSONArray("aweme_details")
            else -> return null
        }
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val id = item.optString("aweme_id", "")
            // 容错：接口偶尔返回缺首位的 ID
            if (id != awemeId && !id.endsWith(awemeId) && !awemeId.endsWith(id)) continue
            toVideoInfo(item)?.let { return it }
        }
        return null
    }

    private fun toVideoInfo(item: JSONObject): VideoInfo? {
        val title = item.optString("desc").trim().ifBlank { "抖音作品" }
        val author = item.optJSONObject("author")?.optString("nickname").orEmpty()
        val cover = item.optJSONObject("video")
            ?.optJSONObject("cover")?.optJSONArray("url_list")?.optString(0).orEmpty()

        // 图集：images 数组的 url_list 是无水印原图直链
        val images = mutableListOf<String>()
        item.optJSONArray("images")?.let { arr ->
            for (i in 0 until arr.length()) {
                val img = arr.optJSONObject(i) ?: continue
                val u = img.optJSONArray("url_list")?.let { ul ->
                    (0 until ul.length()).firstNotNullOfOrNull { j -> ul.optString(j).takeIf { it.isNotBlank() } }
                }
                if (!u.isNullOrBlank()) images.add(u)
            }
        }
        if (images.isNotEmpty()) {
            return VideoInfo(platform, title, author, cover.ifBlank { images.first() }, "", 0, images)
        }

        // 视频：play_addr 把 playwm 替换成 play 得到无水印直链
        val play = item.optJSONObject("video")
            ?.optJSONObject("play_addr")?.optJSONArray("url_list")
            ?.let { ul -> (0 until ul.length()).firstNotNullOfOrNull { j -> ul.optString(j).takeIf { it.isNotBlank() } } }
            .orEmpty()
        if (play.isNotBlank()) {
            val clean = play.replace("playwm", "play")
            return VideoInfo(platform, title, author, cover, clean, item.optLong("duration"))
        }
        return null
    }

    /** DOM 兜底：解析页面渲染出的图片（标题 + 图片，作者未知） */
    private fun parseDomJson(value: String?, awemeId: String): VideoInfo? {
        if (value.isNullOrBlank() || value == "null" || value == "\"\"") return null
        return try {
            val obj = JSONObject(value)
            val title = obj.optString("title").trim().ifBlank { "抖音作品" }
            val imgs = mutableListOf<String>()
            obj.optJSONArray("imgs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val u = arr.optString(i)
                    if (u.isNotBlank()) imgs.add(u)
                }
            }
            if (imgs.isEmpty()) null
            else VideoInfo(platform, title, "", imgs.first(), "", 0, imgs)
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // 注入脚本
    // ------------------------------------------------------------------

    companion object {
        /** 截获 fetch / XMLHttpRequest 响应，命中作品数据接口时回传 */
        private val JS_HOOK = """
            (function(){
              if (window.__dyHooked) return;
              window.__dyHooked = true;
              var PAT = /aweme\/post|aweme\/detail|slidesinfo|iteminfo|aweme\/v1\/web\/aweme/;
              function tryCapture(text){
                try {
                  if (text && text.length > 50 && window.AndroidBridge) {
                    window.AndroidBridge.capture(text);
                  }
                } catch(e){}
              }
              var of = window.fetch;
              if (of) {
                window.fetch = function(){
                  var args = arguments;
                  var url = (typeof args[0] === 'string') ? args[0] : (args[0] && args[0].url) || '';
                  return of.apply(this, args).then(function(resp){
                    try {
                      if (PAT.test(url)) {
                        resp.clone().text().then(function(t){ tryCapture(t); });
                      }
                    } catch(e){}
                    return resp;
                  });
                };
              }
              var XHR = window.XMLHttpRequest;
              if (XHR && XHR.prototype) {
                var oOpen = XHR.prototype.open;
                XHR.prototype.open = function(m, u){
                  this.__dyUrl = u;
                  return oOpen.apply(this, arguments);
                };
                var oSend = XHR.prototype.send;
                XHR.prototype.send = function(){
                  var self = this;
                  this.addEventListener('load', function(){
                    try {
                      var u = self.__dyUrl || '';
                      if (PAT.test(u)) tryCapture(self.responseText);
                    } catch(e){}
                  });
                  return oSend.apply(this, arguments);
                };
              }
            })();
        """.trimIndent()

        /** DOM 兜底：收集页面渲染出的图集图片直链 */
        private val DOM_EXTRACT_JS = """
            (function(){
              var imgs = [];
              var nodes = document.querySelectorAll('img');
              for (var i = 0; i < nodes.length; i++) {
                var s = nodes[i].src || nodes[i].getAttribute('data-src') || '';
                if (s.indexOf('douyinpic') >= 0 && s.indexOf('tplv-dy-aweme-images') >= 0) {
                  imgs.push(s);
                }
              }
              var uniq = [];
              var seen = {};
              for (var j = 0; j < imgs.length; j++) {
                if (!seen[imgs[j]]) { seen[imgs[j]] = 1; uniq.push(imgs[j]); }
              }
              var d = document.querySelector('meta[name="description"]');
              return {
                imgs: uniq,
                title: document.title,
                desc: d ? d.content : ''
              };
            })()
        """.trimIndent()
    }
}
