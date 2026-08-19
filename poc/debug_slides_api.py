# -*- coding: utf-8 -*-
"""调试：找 slidesinfo / iteminfo 接口的函数定义与参数"""
import re
import urllib.request

UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)
SRC = "https://lf-douyin-mobile.bytecdn.com/obj/growth-douyin-share/growth/douyin_ug/static/js/6531.98ffab12.js"


def fetch(url, referer=None):
    h = {"User-Agent": UA, "Accept": "*/*"}
    if referer:
        h["Referer"] = referer
    req = urllib.request.Request(url, headers=h)
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf-8", "ignore")


def main():
    js = fetch(SRC, referer="https://m.douyin.com/share/note/7631053543941117618")

    for kw in ("slidesinfo", "iteminfo", "slides"):
        print(f"\n===== {kw} =====")
        for m in re.finditer(re.escape(kw), js):
            s = max(0, m.start() - 200)
            print("  ...", js[s:m.start() + 250].replace("\n", " ")[:450])
            print("  ---")

    # 找请求封装 AH（U2 返回的请求函数）
    print("\n===== 请求封装（含 baseURL / host / headers 的代码）=====")
    for pat in ("baseURL", "baseUrl", "host:", "www.douyin.com", "iesdouyin.com", "m.douyin.com", "aweme.snssdk"):
        for m in re.finditer(re.escape(pat), js):
            s = max(0, m.start() - 120)
            ctx = js[s:m.start() + 150].replace("\n", " ")
            if "monitor" in ctx or "w3.org" in ctx:
                continue
            print(f"  [{pat}] ...{ctx[:280]}...")
            print("  ---")


if __name__ == "__main__":
    main()
