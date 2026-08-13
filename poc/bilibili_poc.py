# -*- coding: utf-8 -*-
"""B站解析 POC：链接/BV号 -> 标题、作者、无水印视频直链（仅用标准库）"""
import json
import re
import sys
import urllib.request

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)


def fetch_json(url, referer=None):
    headers = {"User-Agent": UA, "Accept": "*/*"}
    if referer:
        headers["Referer"] = referer
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def resolve(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.geturl()


def parse(text):
    m = re.search(r"https?://b23\.tv/[A-Za-z0-9]+", text)
    if m:
        text = resolve(m.group(0))

    bv = re.search(r"(BV[0-9A-Za-z]{10})", text)
    if not bv:
        raise ValueError("未找到 B 站 BV 号，请粘贴 B 站链接或 BV 号")
    bvid = bv.group(1)

    info = fetch_json(
        f"https://api.bilibili.com/x/web-interface/view?bvid={bvid}"
    )["data"]
    cid = info["cid"]

    play = fetch_json(
        "https://api.bilibili.com/x/player/playurl"
        f"?bvid={bvid}&cid={cid}&qn=64&platform=html5&high_quality=1",
        referer="https://www.bilibili.com",
    )["data"]
    durl = play["durl"][0]
    video_url = durl["url"] or (durl.get("backup_url") or [""])[0]

    return {
        "platform": "B站",
        "title": info["title"],
        "author": info["owner"]["name"],
        "cover": info["pic"],
        "video_url": video_url,
        "duration_sec": info["duration"],
        "quality": play.get("quality"),
    }


if __name__ == "__main__":
    arg = sys.argv[1] if len(sys.argv) > 1 else ""
    if not arg:
        arg = input("粘贴 B 站分享链接或 BV 号：").strip()
    print(json.dumps(parse(arg), ensure_ascii=False, indent=2))
