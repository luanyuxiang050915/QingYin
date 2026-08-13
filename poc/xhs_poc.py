# -*- coding: utf-8 -*-
"""小红书解析 POC：分享文本 -> 标题、作者、无水印视频直链（仅用标准库）"""
import json
import re
import sys
import urllib.request

MOBILE_UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)
URL_RE = re.compile(
    r"https?://[a-zA-Z0-9.-]*(?:xiaohongshu\.com|xhslink\.cn)/[A-Za-z0-9?&=/%._~:#+@-]*"
)
ID_RE = re.compile(r"/(?:explore|discovery/item|item)/([0-9a-f]{24})")


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": MOBILE_UA, "Accept": "*/*"})
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.read().decode("utf-8", "ignore"), resp.geturl()


def note_data(html):
    m = re.search(r"window\.__SETUP_SERVER_STATE__\s*=\s*(\{.*?\})\s*</script>", html, re.S)
    if not m:
        return None
    cleaned = re.sub(r":undefined(?=[,}])", ":null", m.group(1))
    try:
        return json.loads(cleaned)["LAUNCHER_SSR_STORE_PAGE_DATA"]["noteData"]
    except Exception:
        return None


def parse(text):
    m = URL_RE.search(text)
    if not m:
        raise ValueError("未找到小红书链接，请粘贴完整的分享文本")
    html, final = fetch(m.group(0))
    if not ID_RE.search(final):
        raise ValueError(f"无法从链接中提取笔记 ID：{final}")

    note = note_data(html)
    video_url = None
    duration_sec = 0
    if note:
        h264 = (
            note.get("video", {}).get("media", {}).get("stream", {}).get("h264") or []
        )
        if h264:
            video_url = h264[0].get("masterUrl") or (
                h264[0].get("backupUrls") or [""]
            )[0]
            duration_sec = (h264[0].get("videoDuration") or 0) // 1000
        title = (note.get("title") or note.get("desc") or "").strip() or "小红书视频"
        author = (note.get("user") or {}).get("nickName", "")
        cover = ""
        imgs = note.get("imageList") or []
        if imgs:
            info = (imgs[0].get("infoList") or [])
            if info:
                cover = info[0].get("url", "")
    else:
        fb = re.search(r'"masterUrl":"(http[^"]+)"', html)
        if not fb:
            raise ValueError("页面数据缺失（可能被风控，可切换WiFi/流量后重试）")
        video_url = fb.group(1)
        title, author, cover = "小红书视频", "", ""

    return {
        "platform": "小红书",
        "title": title,
        "author": author,
        "cover": cover,
        "video_url": video_url,
        "duration_sec": duration_sec,
    }


if __name__ == "__main__":
    arg = sys.argv[1] if len(sys.argv) > 1 else ""
    if not arg:
        arg = input("粘贴小红书分享文本：").strip()
    print(json.dumps(parse(arg), ensure_ascii=False, indent=2))
