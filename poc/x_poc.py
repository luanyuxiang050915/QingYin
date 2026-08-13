# -*- coding: utf-8 -*-
"""X(推特) 解析 POC：推文链接 -> 标题、作者、无水印视频直链（仅用标准库）"""
import json
import re
import sys
import urllib.request

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
STATUS_RE = re.compile(r"https?://(?:x|twitter)\.com/[A-Za-z0-9_]{1,20}/status/(\d+)")
TCO_RE = re.compile(r"https?://t\.co/[A-Za-z0-9]+")
USER_RE = re.compile(r"https?://(?:x|twitter)\.com/([A-Za-z0-9_]{1,20})/status/")


def fetch_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*"})
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8", "ignore"))


def resolve(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.geturl()


def via_syndication(status_id):
    data = fetch_json(
        f"https://cdn.syndication.twimg.com/tweet-result?id={status_id}&token=!"
    )
    video = data.get("video")
    if not video:
        return None
    best, best_area = None, -1
    for v in video.get("variants", []):
        if v.get("type") == "video/mp4":
            m = re.search(r"(\d+)x(\d+)", v.get("src", ""))
            area = int(m.group(1)) * int(m.group(2)) if m else 0
            if area >= best_area:
                best_area, best = area, v.get("src")
    if not best:
        return None
    user = data.get("user") or {}
    return {
        "platform": "X(推特)",
        "title": data.get("text", "").strip() or "X 视频",
        "author": user.get("name", ""),
        "cover": video.get("poster", ""),
        "video_url": best,
        "duration_sec": video.get("durationMs", 0) // 1000,
    }


def via_vxtwitter(user, status_id):
    data = fetch_json(f"https://api.vxtwitter.com/{user}/status/{status_id}")
    url = thumb = None
    dur = 0
    for m in data.get("media_extended") or []:
        if m.get("type") == "video":
            url = m.get("url")
            thumb = m.get("thumbnail_url")
            dur = m.get("duration_millis", 0)
            break
    if not url:
        return None
    return {
        "platform": "X(推特)",
        "title": data.get("text", "").strip() or "X 视频",
        "author": data.get("user_name", ""),
        "cover": thumb or "",
        "video_url": url,
        "duration_sec": dur // 1000,
    }


def parse(text):
    status_url = STATUS_RE.search(text)
    if not status_url:
        short = TCO_RE.search(text)
        if not short:
            raise ValueError("未找到 X 链接，请粘贴推文链接")
        status_url = STATUS_RE.search(resolve(short.group(0)))
    if not status_url:
        raise ValueError("无法从链接中提取推文 ID")
    url = status_url.group(0)
    status_id = status_url.group(1)
    user = USER_RE.search(url)

    result = via_syndication(status_id)
    if not result and user:
        result = via_vxtwitter(user.group(1), status_id)
    if not result:
        raise ValueError("该推文没有可下载的视频（可能是图片/文字推文）")
    return result


if __name__ == "__main__":
    arg = sys.argv[1] if len(sys.argv) > 1 else ""
    if not arg:
        arg = input("粘贴 X 推文链接：").strip()
    print(json.dumps(parse(arg), ensure_ascii=False, indent=2))
