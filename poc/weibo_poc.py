# -*- coding: utf-8 -*-
"""微博解析 POC：分享链接 -> 标题、作者、无水印视频直链（仅用标准库）"""
import json
import re
import sys
import time
import urllib.parse
import urllib.request

UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)
URL_RE = re.compile(r"https?://[a-zA-Z0-9.-]*(?:weibo\.com|weibo\.cn)/[A-Za-z0-9?&=/%._~:#+@-]*")
MID_RE = re.compile(r"(?:m\.weibo\.cn/status|weibo\.com/[A-Za-z0-9_]+)/(\d+)")
TV_RE = re.compile(r"weibo\.com/tv/show/1034:\d+")


def visitor_tid():
    body = urllib.parse.urlencode(
        {"cb": "gen_callback", "fp": "", "t": str(int(time.time()))}
    ).encode()
    req = urllib.request.Request(
        "https://passport.weibo.com/visitor/genvisitor",
        data=body,
        headers={
            "User-Agent": UA,
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        text = resp.read().decode("utf-8", "ignore")
    m = re.search(r'"tid":"([^"]+)"', text)
    if not m:
        raise ValueError("微博访客验证失败")
    return m.group(1)


def fetch_status(mid, tid):
    req = urllib.request.Request(
        f"https://m.weibo.cn/statuses/show?id={mid}",
        headers={
            "User-Agent": UA,
            "Accept": "application/json, text/plain, */*",
            "X-Requested-With": "XMLHttpRequest",
            "Referer": "https://m.weibo.cn/",
            "Cookie": f"TMPL={tid}",
        },
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8", "ignore"))


def parse(text):
    m = URL_RE.search(text)
    if not m:
        raise ValueError("未找到微博链接，请粘贴分享链接")
    url = m.group(0)
    if TV_RE.search(url):
        raise ValueError("该微博视频链接格式暂不支持，请从微博 App 点分享→复制链接后重试")
    mid = MID_RE.search(url)
    if not mid:
        raise ValueError(f"无法从链接中提取微博 ID：{url}")

    tid = visitor_tid()
    data = fetch_status(mid.group(1), tid).get("data", {})
    page_info = data.get("page_info") or {}
    media_info = page_info.get("media_info") or {}
    video_url = (
        media_info.get("stream_url_hd")
        or media_info.get("stream_url")
        or ""
    )
    if not video_url:
        raise ValueError("未找到视频地址（可能被风控，可切换WiFi/流量后重试）")

    return {
        "platform": "微博",
        "title": (page_info.get("page_title") or page_info.get("title") or "微博视频").strip(),
        "author": (data.get("user") or {}).get("screen_name", ""),
        "cover": ((page_info.get("page_pic") or {}).get("url") or ""),
        "video_url": video_url,
        "duration_sec": media_info.get("duration") or 0,
    }


if __name__ == "__main__":
    arg = sys.argv[1] if len(sys.argv) > 1 else ""
    if not arg:
        arg = input("粘贴微博分享链接：").strip()
    print(json.dumps(parse(arg), ensure_ascii=False, indent=2))
