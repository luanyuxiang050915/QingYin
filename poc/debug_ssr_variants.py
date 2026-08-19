# -*- coding: utf-8 -*-
"""调试：快速测试各种页面/接口变体，找仍能返回作品数据的免签名入口"""
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
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, r.read().decode("utf-8", "ignore")
    except Exception as e:
        return None, f"{type(e).__name__}: {e}"


cases = [
    ("ies share/note + from_ssr=0", f"https://www.iesdouyin.com/share/note/{NOTE_ID}/?from_ssr=0", MOBILE_UA, "https://www.douyin.com/"),
    ("ies share/note + is_spider=1", f"https://www.iesdouyin.com/share/note/{NOTE_ID}/?is_spider=1", MOBILE_UA, "https://www.douyin.com/"),
    ("www iteminfo", f"https://www.douyin.com/web/api/v2/aweme/iteminfo/?item_ids={NOTE_ID}", DESKTOP_UA, "https://www.douyin.com/"),
    ("m.douyin iteminfo", f"https://m.douyin.com/web/api/v2/aweme/iteminfo/?item_ids={NOTE_ID}", MOBILE_UA, "https://m.douyin.com/"),
    ("ies share/note modal_id", f"https://www.iesdouyin.com/share/note/{NOTE_ID}/?modal_id={NOTE_ID}", MOBILE_UA, "https://www.douyin.com/"),
    ("ies share/note desktop UA", f"https://www.iesdouyin.com/share/note/{NOTE_ID}/", DESKTOP_UA, "https://www.douyin.com/"),
]
for name, url, ua, ref in cases:
    status, body = fetch(url, ua, ref)
    has_router = "_ROUTER_DATA" in body
    has_data = any(k in body for k in ("imagesList", "\"images\"", "play_addr", "\"desc\":"))
    print(f"=== {name}: HTTP {status} 长度{len(body)} _ROUTER_DATA={has_router} 作品数据={has_data}")
    if has_router and not has_data:
        # 检查 loaderData 里有没有作品字段
        m = re.search(r"window\._ROUTER_DATA\s*=\s*(\{.*?\})\s*</script>", body, re.S)
        if m:
            import json
            root = json.loads(m.group(1))
            ld = root.get("loaderData", {})
            for k, v in ld.items():
                if isinstance(v, dict) and v and k not in ("note_layout", "video_layout"):
                    print("    有数据的 loaderData key:", k, "->", list(v.keys())[:12])
