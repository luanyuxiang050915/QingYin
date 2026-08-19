# -*- coding: utf-8 -*-
"""调试：抓 note 分享页 JS chunk，找出作品数据异步接口"""
import re
import urllib.request

UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)


def fetch(url, referer=None):
    h = {"User-Agent": UA, "Accept": "*/*"}
    if referer:
        h["Referer"] = referer
    req = urllib.request.Request(url, headers=h)
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read().decode("utf-8", "ignore")


def main():
    html = fetch(
        "https://www.iesdouyin.com/share/note/7631053543941117618/",
        referer="https://www.douyin.com/",
    )
    srcs = re.findall(r'<script[^>]*src="([^"]*)"[^>]*>', html)
    print("页面 JS 列表:")
    for s in srcs:
        print("  ", s)
    # 找 note 相关 chunk
    note_js = [s for s in srcs if "note." in s]
    for src in note_js:
        if src.startswith("//"):
            src = "https:" + src
        print("\n=== 分析 JS:", src)
        js = fetch(src, referer="https://www.iesdouyin.com/")
        print("长度:", len(js))
        # 找出所有字符串里像接口路径的
        hits = set()
        for m in re.finditer(r'"((?:/[a-zA-Z0-9_]+){2,})"', js):
            p = m.group(1)
            if any(k in p for k in ("aweme", "note", "detail", "item", "mix", "feed")):
                hits.add(p)
        for p in sorted(hits):
            print("  API:", p[:150])
        # 也找 /aweme/ 开头的
        for m in re.finditer(r'"(/aweme/[^"]+)"', js):
            print("  AWEME:", m.group(1)[:200])


if __name__ == "__main__":
    main()
