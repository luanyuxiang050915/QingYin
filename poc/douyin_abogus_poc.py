# -*- coding: utf-8 -*-
"""
抖音解析 POC v2：a_bogus 签名方案（实验性，当前不可用）

背景：2026-08 抖音改版后，分享页不再在 _ROUTER_DATA 内嵌作品数据，改由前端调用
      /aweme/v1/web/aweme/detail/ 接口异步获取，该接口要求 a_bogus 签名。

⚠️ 实验结论（2026-08-19 实测）：纯 HTTP + a_bogus 签名仍会被服务端拒绝
   （200 空 body / 403 / "Url doesn't match"），因为服务端还会校验真实浏览器状态
   （s_v_web_id cookie、真实 msToken、安全 SDK 指纹 uifid 等）。本脚本保留作为
   签名算法参考（lib/abogus_*.js 为 V 1.0.1.19-fix.01 版算法，源自
   https://github.com/brock7/douyin_sign ，Apache-2.0）。

✅ 当前可用方案：WebView 真实浏览器（Android 端 DouyinWebViewParser.kt，
   对应验证脚本 poc/douyin_playwright_poc.js）。

用法（仅供参考，当前会被服务端拒绝）：
    python poc/douyin_abogus_poc.py "分享文本或链接"
输出：JSON（标题、作者、封面、无水印视频直链 / 图集原图直链）
"""
import json
import random
import re
import string
import subprocess
import sys
import urllib.parse
import urllib.request

UA_DESKTOP = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
)
HOST = "https://www.douyin.com"
SIGN_CLI = "poc/lib/abogus_cli.js"

URL_RE = re.compile(
    r"https?://[a-zA-Z0-9.-]*(?:douyin|iesdouyin)\.com/[A-Za-z0-9?&=/%._~:#+@-]*"
)
ID_RE = re.compile(r"/(?:video|note)/(\d+)")
MODAL_ID_RE = re.compile(r"[?&]modal_id=(\d+)")

# 与前端一致的公共参数（参考 MediaCrawler）
COMMON_PARAMS = {
    "device_platform": "webapp",
    "aid": "6383",
    "channel": "channel_pc_web",
    "version_code": "190600",
    "version_name": "19.6.0",
    "update_version_code": "170400",
    "pc_client_type": "1",
    "cookie_enabled": "true",
    "browser_language": "zh-CN",
    "browser_platform": "MacIntel",
    "browser_name": "Chrome",
    "browser_version": "125.0.0.0",
    "browser_online": "true",
    "engine_name": "Blink",
    "os_name": "Mac OS",
    "os_version": "10.15.7",
    "cpu_core_num": "8",
    "device_memory": "8",
    "engine_version": "109.0",
    "platform": "PC",
    "screen_width": "2560",
    "screen_height": "1440",
    "effective_type": "4g",
    "round_trip_time": "50",
}


def rand_webid():
    """生成随机 webid（MediaCrawler 同款算法）"""
    def e(t):
        if t is not None:
            return str(t ^ (int(16 * random.random()) >> (t // 4)))
        return "".join([str(int(1e7)), "-", str(int(1e3)), "-",
                        str(int(4e3)), "-", str(int(8e3)), "-", str(int(1e11))])
    web_id = "".join(e(int(x)) if x in "018" else x for x in e(None))
    return web_id.replace("-", "")[:19]


def rand_ms_token(length=120):
    base = string.ascii_letters + string.digits + "="
    return "".join(random.choice(base) for _ in range(length))


def register_ttwid():
    """注册访客 cookie ttwid，返回 cookie 字符串（失败返回空）"""
    try:
        body = json.dumps({
            "region": "CN",
            "aid": 6383,
            "needFid": False,
            "service": "www.douyin.com",
            "migrate_info": {"ticket": "", "source": "node"},
            "cbUrlProtocol": "https",
            "union": True,
        }).encode()
        req = urllib.request.Request(
            "https://ttwid.bytedance.com/ttwid/union/register/",
            data=body,
            headers={"User-Agent": UA_DESKTOP, "Content-Type": "application/json",
                     "Origin": "https://www.douyin.com", "Referer": "https://www.douyin.com/"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=20) as resp:
            data = json.loads(resp.read().decode("utf-8", "ignore"))
        redirect_url = data.get("redirect_url", "")
        if not redirect_url:
            return ""
        # 跟随回调 URL，Set-Cookie 里带 ttwid
        req2 = urllib.request.Request(redirect_url, headers={"User-Agent": UA_DESKTOP})
        with urllib.request.urlopen(req2, timeout=20) as resp2:
            cookie_header = resp2.headers.get("Set-Cookie", "")
        m = re.search(r"ttwid=([^;]+)", cookie_header)
        return f"ttwid={m.group(1)}" if m else ""
    except Exception as e:
        print(f"[warn] ttwid 注册失败: {e}")
        return ""


def get_a_bogus(query_string, ua):
    """调用 Node + douyin.js 生成 a_bogus"""
    proc = subprocess.run(
        ["node", SIGN_CLI, query_string, ua],
        capture_output=True, text=True, timeout=30,
    )
    if proc.returncode != 0:
        raise RuntimeError(f"a_bogus 签名失败: {proc.stderr[-300:]}")
    sig = proc.stdout.strip()
    if len(sig) < 100:
        raise RuntimeError(f"a_bogus 结果异常: {sig[:100]}")
    return sig


def fetch_json(url, cookie=""):
    headers = {
        "User-Agent": UA_DESKTOP,
        "Accept": "application/json, text/plain, */*",
        "Referer": "https://www.douyin.com/",
        "sec-fetch-site": "same-origin",
        "sec-fetch-mode": "cors",
        "sec-fetch-dest": "empty",
        "accept-language": "zh-CN,zh;q=0.9,en;q=0.8",
    }
    if cookie:
        headers["Cookie"] = cookie
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            body = resp.read().decode("utf-8", "ignore")
            return resp.status, body
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "ignore")


def resolve_short_url(url):
    """跟随短链跳转，返回真实 URL"""
    req = urllib.request.Request(url, headers={"User-Agent": UA_DESKTOP})
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.geturl()


def find_first(node, key):
    if isinstance(node, dict):
        if key in node:
            return node[key]
        for v in node.values():
            r = find_first(v, key)
            if r is not None:
                return r
    elif isinstance(node, list):
        for v in node:
            r = find_first(v, key)
            if r is not None:
                return r
    return None


def parse_aweme(aweme):
    """从 aweme_detail 提取视频/图集信息"""
    title = (aweme.get("desc") or "").strip() or "抖音作品"
    author = ((aweme.get("author") or {}).get("nickname") or "").strip()
    cover = ""
    cover_obj = aweme.get("video", {}).get("cover")
    if cover_obj:
        urls = cover_obj.get("url_list") or []
        if urls:
            cover = urls[0]

    # 图集：images 数组的 url_list 是无水印原图直链
    images = aweme.get("images")
    if isinstance(images, list) and images:
        img_urls = []
        for img in images:
            for u in (img.get("url_list") or []):
                if u:
                    img_urls.append(u)
                    break
        if img_urls:
            return {
                "platform": "抖音",
                "type": "图集",
                "title": title,
                "author": author,
                "cover": cover or img_urls[0],
                "image_urls": img_urls,
            }

    # 视频：play_addr 把 playwm 替换成 play 得到无水印直链
    play = aweme.get("video", {}).get("play_addr")
    if play:
        urls = play.get("url_list") or []
        if urls:
            wm = urls[0]
            clean = wm.replace("playwm", "play")
            duration = aweme.get("duration") or 0
            return {
                "platform": "抖音",
                "type": "视频",
                "title": title,
                "author": author,
                "cover": cover,
                "video_url": clean,
                "duration_sec": duration,
            }
    raise ValueError("aweme_detail 中未找到视频或图片地址")


def parse(text):
    raw = URL_RE.search(text)
    if not raw:
        raise ValueError("未找到抖音链接，请粘贴完整的分享文本")
    url = raw.group(0)

    final_url = url
    if "v.douyin.com" in url:
        print("[1] 短链跳转...")
        final_url = resolve_short_url(url)
        print("    最终地址:", final_url[:120])

    m = ID_RE.search(final_url) or MODAL_ID_RE.search(final_url)
    if not m:
        raise ValueError(f"无法从链接中提取作品 ID：{final_url}")
    aweme_id = m.group(1)
    print(f"[2] 作品 ID: {aweme_id}")

    cookie = register_ttwid()
    print(f"[3] ttwid: {'OK' if cookie else '无（尝试无 cookie 请求）'}")

    params = dict(COMMON_PARAMS)
    params.update({
        "webid": rand_webid(),
        "msToken": rand_ms_token(),
        "aweme_id": aweme_id,
    })
    query = urllib.parse.urlencode(params)
    print("[4] 生成 a_bogus 签名...")
    a_bogus = get_a_bogus(query, UA_DESKTOP)
    print(f"    a_bogus 长度: {len(a_bogus)}")

    full_url = f"{HOST}/aweme/v1/web/aweme/detail/?{query}&a_bogus={urllib.parse.quote(a_bogus)}"
    status, body = fetch_json(full_url, cookie)
    print(f"[5] detail 接口: HTTP {status}, 返回长度 {len(body)}")
    if status != 200:
        raise ValueError(f"detail 接口失败 HTTP {status}: {body[:200]}")
    if not body or body.strip() in ("", "blocked"):
        raise ValueError("detail 接口返回空（签名或 cookie 校验失败）")

    data = json.loads(body)
    aweme = data.get("aweme_detail")
    if not aweme:
        print("   接口返回:", body[:400])
        raise ValueError("接口无 aweme_detail 数据（可能被风控）")

    return parse_aweme(aweme)


if __name__ == "__main__":
    arg = sys.argv[1] if len(sys.argv) > 1 else ""
    if not arg:
        arg = input("粘贴抖音分享文本：").strip()
    print(json.dumps(parse(arg), ensure_ascii=False, indent=2))
