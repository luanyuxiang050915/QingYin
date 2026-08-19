# -*- coding: utf-8 -*-
"""调试：从 m.douyin.com share/note 页面的 JS 里挖出真实 API 调用（host/path/params/headers）"""
import re
import urllib.request

UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)
NOTE_ID = "7631053543941117618"


def fetch(url, referer=None, ua=UA):
    h = {"User-Agent": ua, "Accept": "*/*"}
    if referer:
        h["Referer"] = referer
    req = urllib.request.Request(url, headers=h)
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read().decode("utf-8", "ignore")


def main():
    html = fetch(f"https://m.douyin.com/share/note/{NOTE_ID}", referer="https://www.douyin.com/")
    print("HTML 长度:", len(html))
    # 收集所有 JS chunk
    srcs = re.findall(r'<script[^>]*src="([^"]*)"[^>]*>', html)
    print("JS chunks:", len(srcs))
    keywords = ("aweme/v1", "aweme/v2", "web/api", "detail", "note/detail", "aweme/detail", "api/douyin")
    for src in srcs:
        if src.startswith("//"):
            src = "https:" + src
        if "bytecdn" not in src and "bytedos" not in src and "bytegoofy" not in src:
            continue
        try:
            js = fetch(src, referer=f"https://m.douyin.com/share/note/{NOTE_ID}")
        except Exception:
            continue
        hits = []
        for kw in keywords:
            for m in re.finditer(re.escape(kw), js):
                s = max(0, m.start() - 60)
                ctx = js[s:m.start() + 90].replace("\n", " ")
                hits.append((kw, ctx))
        if hits:
            print(f"\n=== {src.split('/')[-1][:60]} ({len(js)}B)")
            for kw, ctx in hits[:6]:
                print(f"  [{kw}] ...{ctx[:180]}...")


if __name__ == "__main__":
    main()
