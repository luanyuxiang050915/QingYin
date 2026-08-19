# -*- coding: utf-8 -*-
"""调试：分析 note_(id)/page JS chunk 里的数据接口"""
import re
import urllib.request

UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)
URL = (
    "https://lf-douyin-mobile.bytecdn.com/obj/growth-douyin-share/growth/douyin_ug/"
    "static/js/async/note_(id)/page.2014af9c.js"
)


def fetch(url, referer=None):
    h = {"User-Agent": UA, "Accept": "*/*"}
    if referer:
        h["Referer"] = referer
    req = urllib.request.Request(url, headers=h)
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read().decode("utf-8", "ignore")


def main():
    js = fetch(URL, referer="https://www.iesdouyin.com/")
    print("JS 长度:", len(js))
    # 1. 找接口路径字符串
    hits = set()
    for m in re.finditer(r'"((?:/[a-zA-Z0-9_]+){2,})"', js):
        p = m.group(1)
        if any(k in p for k in ("aweme", "note", "detail", "item", "mix", "feed", "api", "web")):
            hits.add(p)
    print("--- 疑似接口路径 ---")
    for p in sorted(hits):
        print("  ", p[:200])
    # 2. 找完整 URL
    print("--- 完整 URL ---")
    for m in re.finditer(r'https?://[^"\']{5,120}', js):
        print("  ", m.group(0)[:160])
    # 3. 找 axios 请求相关的上下文（url 附近的代码）
    print("--- url: 附近的上下文 ---")
    for m in re.finditer(r'.{80}url:.{120}', js):
        s = m.group(0)
        print("  ", s[:220].replace("\n", " "))
    # 4. 找 a_bogus / X-Bogus / 签名相关
    print("--- 签名相关关键词 ---")
    for kw in ("a_bogus", "X-Bogus", "X-Bogus", "msToken", "ttwid", "sign"):
        print("  ", kw, "->", js.count(kw))


if __name__ == "__main__":
    main()
