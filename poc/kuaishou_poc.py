# -*- coding: utf-8 -*-
"""快手解析 POC：分享文本 -> 标题、作者、无水印视频直链（仅用标准库）"""
import json
import re
import sys
import urllib.request

MOBILE_UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)
URL_RE = re.compile(
    r"https?://[a-zA-Z0-9.-]*(?:kuaishou|gifshow)\.com/[A-Za-z0-9?&=/%._~:#+@-]*"
)
ID_RE = re.compile(r"/(?:fw/photo|short-video)/([A-Za-z0-9]+)")


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": MOBILE_UA, "Accept": "*/*"})
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.read().decode("utf-8", "ignore"), resp.geturl()


def first_array(html, key):
    m = re.search(rf'"{key}":(\[.*?\])', html, re.S)
    if not m:
        return None
    try:
        return json.loads(m.group(1))
    except Exception:
        return None


def parse(text):
    m = URL_RE.search(text)
    if not m:
        raise ValueError("未找到快手链接，请粘贴完整的分享文本")
    html, final = fetch(m.group(0))

    photo = ID_RE.search(final)
    if not photo:
        raise ValueError(f"无法从链接中提取视频 ID：{final}")

    mv = first_array(html, "mainMvUrls")
    if not mv or not mv[0].get("url"):
        # 兜底：直接搜页面里的 upic mp4 直链
        fb = re.search(
            r"https://[a-zA-Z0-9.-]*\.(?:yximgs|kwimgs)\.com/upic/[^\"\\]+?\.mp4[^\"\\]*",
            html,
        )
        if not fb:
            raise ValueError("未找到视频播放地址（可能被风控）")
        video_url = fb.group(0)
    else:
        video_url = mv[0]["url"]

    cap = re.search(r'"caption":"(.*?)",', html)
    user = re.search(r'"user_name":"(.*?)"', html)
    cover = first_array(html, "coverUrls")
    dur = re.search(r'"duration":(\d+)', html)
    duration_ms = int(dur.group(1)) if dur else 0

    return {
        "platform": "快手",
        "title": cap.group(1).strip() if cap else "快手视频",
        "author": user.group(1) if user else "",
        "cover": cover[0]["url"] if cover else "",
        "video_url": video_url,
        "duration_sec": duration_ms // 1000 if duration_ms > 1000 else duration_ms,
    }


if __name__ == "__main__":
    arg = sys.argv[1] if len(sys.argv) > 1 else ""
    if not arg:
        arg = input("粘贴快手分享文本：").strip()
    print(json.dumps(parse(arg), ensure_ascii=False, indent=2))
