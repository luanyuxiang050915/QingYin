# -*- coding: utf-8 -*-
"""读取保存的直链 → 不同 Referer 下载测试（模拟 App 的 VideoDownloader）"""
import sys
import urllib.request

with open(r"E:\002_代码\003_奇思乱想\视频下载去水印\poc\direct-url.txt", encoding="utf-8-sig") as f:
    direct = f.read().strip()

print("直链:", direct[:120], "...")
tests = [
    ("App 逻辑（Referer=直链同域名 ev.phncdn.com）", "https://ev.phncdn.com/"),
    ("Referer=cn.pornhub.com", "https://cn.pornhub.com/"),
    ("Referer=www.pornhub.com", "https://www.pornhub.com/"),
    ("无 Referer", None),
]
for name, ref in tests:
    h = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "Range": "bytes=0-102399"}
    if ref:
        h["Referer"] = ref
    req = urllib.request.Request(direct, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            data = r.read()
            print(f"{name} -> HTTP {r.status}, 收到 {len(data)} 字节, Content-Range: {r.headers.get('Content-Range')}")
    except Exception as e:
        print(f"{name} -> 失败: {e}")
