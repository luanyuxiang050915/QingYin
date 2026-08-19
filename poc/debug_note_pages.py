# -*- coding: utf-8 -*-
"""调试：对比多个抖音页面变体，找仍内嵌作品数据的入口"""
import json
import re
import urllib.request

MOBILE_UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)
DESKTOP_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
NOTE_ID = "7631053543941117618"


def fetch(url, ua, referer):
    h = {"User-Agent": ua, "Accept": "*/*", "Referer": referer}
    req = urllib.request.Request(url, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, r.read().decode("utf-8", "ignore")
    except Exception as e:
        return None, f"{type(e).__name__}: {e}"


def analyze(name, url, ua, referer):
    status, body = fetch(url, ua, referer)
    print(f"=== {name}")
    print(f"    HTTP {status}, 长度 {len(body)}")
    if status != 200:
        print(f"    {body[:200]}")
        return
    # 检查各数据标记
    for mark in ("_ROUTER_DATA", "_SSR_DATA", "images", "play_addr", "desc", "验证", "安全验证", "captcha"):
        print(f"    含 {mark}: {mark in body}")
    m = re.search(r"window\._ROUTER_DATA\s*=\s*(\{.*?\})\s*</script>", body, re.S)
    if m:
        try:
            root = json.loads(m.group(1))
            ld = root.get("loaderData", {})
            print("    _ROUTER_DATA loaderData keys:", list(ld.keys())[:8])
            for k, v in ld.items():
                if isinstance(v, dict) and v:
                    keys = list(v.keys())
                    print(f"      {k} -> keys: {keys[:15]}")
        except Exception as e:
            print("    _ROUTER_DATA 解析失败:", e)
    print()


analyze("桌面 www.douyin.com/note", f"https://www.douyin.com/note/{NOTE_ID}", DESKTOP_UA, "https://www.douyin.com/")
analyze("桌面 www.douyin.com/share/note", f"https://www.douyin.com/share/note/{NOTE_ID}", DESKTOP_UA, "https://www.douyin.com/")
analyze("移动 m.douyin.com/share/note", f"https://m.douyin.com/share/note/{NOTE_ID}", MOBILE_UA, "https://www.douyin.com/")
analyze("移动 m.douyin.com/note", f"https://m.douyin.com/note/{NOTE_ID}", MOBILE_UA, "https://www.douyin.com/")
analyze("iesdouyin 桌面UA", f"https://www.iesdouyin.com/share/note/{NOTE_ID}/", DESKTOP_UA, "https://www.douyin.com/")
