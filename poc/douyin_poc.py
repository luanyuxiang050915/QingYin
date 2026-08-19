# -*- coding: utf-8 -*-
"""抖音解析 POC：分享文本 -> 标题、作者、无水印视频直链 / 图集原图直链（仅用标准库）"""
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


def find_images(node):
    """递归查找图文作品的 images 数组，提取每张图 url_list 中第一个可用直链"""
    if isinstance(node, dict):
        imgs = node.get("images")
        if isinstance(imgs, list) and imgs:
            urls = []
            for img in imgs:
                if not isinstance(img, dict):
                    continue
                for u in img.get("url_list") or []:
                    if u:
                        urls.append(u)
                        break
            if urls:
                return urls
        for v in node.values():
            r = find_images(v)
            if r:
                return r
    elif isinstance(node, list):
        for v in node:
            r = find_images(v)
            if r:
                return r
    return []


def parse(text):
    raw_url = URL_RE.search(text)
    if not raw_url:
        raise ValueError("未找到抖音链接，请粘贴完整的分享文本")
    url = raw_url.group(0)

    if "v.douyin.com" in url:
        _, url = fetch_text(url)  # 跟随短链跳转拿真实地址

    m = ID_RE.search(url) or MODAL_ID_RE.search(url)
    if not m:
        raise ValueError(f"无法从链接中提取作品 ID：{url}")
    video_id = m.group(1)

    # 图集（/note/）和视频（/video/）对应不同的分享页，失败时互换兜底
    is_note = "/note/" in url
    kinds = ["note", "video"] if is_note else ["video", "note"]
    html = None
    for kind in kinds:
        try:
            html, _ = fetch_text(
                f"https://www.iesdouyin.com/share/{kind}/{video_id}/",
                referer="https://www.douyin.com/",
            )
            break
        except Exception:
            continue
    if html is None:
        raise ValueError("抖音页面请求失败（可能被风控），请稍后再试")

    m = re.search(
        r"<script[^>]*>\s*window\._ROUTER_DATA\s*=\s*(.*?)</script>",
        html,
        re.S,
    )
    if not m:
        raise ValueError("抖音页面数据缺失（可能被风控），请稍后再试")
    root = json.loads(m.group(1).strip().rstrip(";"))

    title = (find_first(root, "desc") or "").strip() or "抖音作品"
    author = (find_first(root, "nickname") or "").strip()

    # 优先按视频解析：有 play_addr 的就是视频
    play_addr = find_first(root, "play_addr")
    if play_addr:
        wm_url = play_addr["url_list"][0]
        no_wm_url = wm_url.replace("playwm", "play")
        return {
            "platform": "抖音",
            "type": "视频",
            "title": title,
            "author": author,
            "cover": find_cover(root),
            "video_url": no_wm_url,
            "duration_sec": find_first(root, "duration") or 0,
        }

    # 没有播放地址则是图文笔记：images 数组的 url_list 就是无水印原图直链
    images = find_images(root)
    if images:
        return {
            "platform": "抖音",
            "type": "图集",
            "title": title,
            "author": author,
            "cover": find_cover(root) or images[0],
            "image_urls": images,
            "duration_sec": 0,
        }
    raise ValueError("未找到视频或图片地址")


if __name__ == "__main__":
    arg = sys.argv[1] if len(sys.argv) > 1 else ""
    if not arg:
        arg = input("粘贴抖音分享文本：").strip()
    print(json.dumps(parse(arg), ensure_ascii=False, indent=2))
