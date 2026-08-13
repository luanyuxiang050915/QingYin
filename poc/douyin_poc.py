# -*- coding: utf-8 -*-
"""抖音解析 POC：分享文本 -> 标题、作者、无水印视频直链（仅用标准库）"""
import json
import re
import sys
import urllib.request

MOBILE_UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)

URL_RE = re.compile(
    r"https?://[a-zA-Z0-9.-]*(?:douyin|iesdouyin)\.com/[A-Za-z0-9?&=/%._~:#+@-]*"
)
ID_RE = re.compile(r"/(?:video|note)/(\d+)")
MODAL_ID_RE = re.compile(r"[?&]modal_id=(\d+)")


def fetch_text(url, referer=None):
    headers = {"User-Agent": MOBILE_UA, "Accept": "*/*"}
    if referer:
        headers["Referer"] = referer
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.read().decode("utf-8", "ignore"), resp.geturl()


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


def find_cover(node):
    cover = find_first(node, "cover")
    if isinstance(cover, dict):
        urls = cover.get("url_list") or []
        if urls:
            return urls[0]
    return ""


def parse(text):
    raw_url = URL_RE.search(text)
    if not raw_url:
        raise ValueError("未找到抖音链接，请粘贴完整的分享文本")
    url = raw_url.group(0)

    if "v.douyin.com" in url:
        _, url = fetch_text(url)  # 跟随短链跳转拿真实地址

    m = ID_RE.search(url) or MODAL_ID_RE.search(url)
    if not m:
        raise ValueError(f"无法从链接中提取视频 ID：{url}")
    video_id = m.group(1)

    html, _ = fetch_text(
        f"https://www.iesdouyin.com/share/video/{video_id}/",
        referer="https://www.douyin.com/",
    )
    m = re.search(
        r"<script[^>]*>\s*window\._ROUTER_DATA\s*=\s*(.*?)</script>",
        html,
        re.S,
    )
    if not m:
        raise ValueError("抖音页面数据缺失（可能被风控），请稍后再试")
    root = json.loads(m.group(1).strip().rstrip(";"))

    play_addr = find_first(root, "play_addr")
    if not play_addr:
        raise ValueError("未找到播放地址")
    wm_url = play_addr["url_list"][0]
    no_wm_url = wm_url.replace("playwm", "play")

    return {
        "platform": "抖音",
        "title": (find_first(root, "desc") or "").strip() or "抖音视频",
        "author": (find_first(root, "nickname") or "").strip(),
        "cover": find_cover(root),
        "video_url": no_wm_url,
        "duration_sec": find_first(root, "duration") or 0,
    }


if __name__ == "__main__":
    arg = sys.argv[1] if len(sys.argv) > 1 else ""
    if not arg:
        arg = input("粘贴抖音分享文本：").strip()
    print(json.dumps(parse(arg), ensure_ascii=False, indent=2))
