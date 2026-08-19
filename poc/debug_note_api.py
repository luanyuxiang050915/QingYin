# -*- coding: utf-8 -*-
"""调试：直接测试抖音公开接口能否拿到 note 详情（含图片列表）"""
import json
import urllib.request

UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)
NOTE_ID = "7631053543941117618"


def get(url, referer="https://www.iesdouyin.com/", cookie=None):
    h = {
        "User-Agent": UA,
        "Accept": "*/*",
        "Referer": referer,
    }
    if cookie:
        h["Cookie"] = cookie
    req = urllib.request.Request(url, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            body = r.read().decode("utf-8", "ignore")
            return r.status, body[:800]
    except Exception as e:
        return None, f"{type(e).__name__}: {e}"


candidates = [
    ("iesdouyin web/api iteminfo", f"https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids={NOTE_ID}"),
    ("iesdouyin aweme detail (无签名)", f"https://www.iesdouyin.com/aweme/v1/web/aweme/detail/?aweme_id={NOTE_ID}"),
    ("m.douyin aweme detail (无签名)", f"https://m.douyin.com/aweme/v1/web/aweme/detail/?aweme_id={NOTE_ID}"),
    ("iesdouyin note detail (无签名)", f"https://www.iesdouyin.com/aweme/v1/web/note/detail/?note_id={NOTE_ID}"),
    ("iesdouyin share page 完整参数链接", "https://www.iesdouyin.com/share/note/{0}/?region=CN&mid=7470023700765263888&u_code=2k5ad50ahalh&did=MS4wLjABAAAATF-2TzdX4U-5pf0Froq5T-2XFss3q5gSpBD2pIkSlZ6A3iYwhc7E2EyPSKaHWmfR&iid=MS4wLjABAAAArH5_Ca2l98ftSvtFu_Lp-uJxxYlAxgXs6WFNWXd6T88lVwcKGDfiuGL3GUGu8I4w&with_sec_did=1".format(NOTE_ID)),
]
for name, url in candidates:
    status, body = get(url)
    print(f"=== {name}")
    print(f"    HTTP {status}")
    print(f"    {body[:400]}")
    print()
