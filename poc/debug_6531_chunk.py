# -*- coding: utf-8 -*-
"""调试：深挖 6531 chunk 里的 note 详情接口与 U2 请求封装"""
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
    print("chunk 长度:", len(js))

    # 1. note 相关的接口路径
    print("\n--- 含 note 的接口路径 ---")
    seen = set()
    for m in re.finditer(r'path:\s*"([^"]*note[^"]*)"', js):
        if m.group(1) not in seen:
            seen.add(m.group(1))
            print("  ", m.group(1))

    # 2. aweme/detail 或 iteminfo 等详情接口
    print("\n--- 详情类接口路径 ---")
    seen = set()
    for m in re.finditer(r'path:\s*"([^"]*)"', js):
        p = m.group(1)
        if ("detail" in p or "iteminfo" in p or "aweme/" in p) and p not in seen and len(p) < 60:
            seen.add(p)
            print("  ", p)

    # 3. U2 请求封装：找函数定义与默认参数
    print("\n--- U2 封装附近的代码 ---")
    for m in re.finditer(r"U2\s*=\s*function|function\s+U2|U2:\s*function", js):
        s = max(0, m.start() - 100)
        print("  ...", js[s:m.start() + 400].replace("\n", " ")[:500])
        print("  ---")

    # 4. 域名/header 配置
    print("\n--- 域名与 headers ---")
    for m in re.finditer(r'"(https?://[^"]*douyin[^"]*)"', js):
        u = m.group(1)
        if "monitor" not in u and u not in seen:
            seen.add(u)
            print("  ", u[:120])


if __name__ == "__main__":
    main()
