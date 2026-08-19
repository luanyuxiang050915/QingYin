# -*- coding: utf-8 -*-
"""调试：在 note_(id)/page JS chunk 里找出作品数据接口的真实路径"""
import re
import urllib.request

UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)
JS_URL = (
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
    js = fetch(JS_URL, referer="https://www.iesdouyin.com/")
    # 找含 /aweme/ 或 /web/ 或 detail 的字符串字面量（含转义拼接的）
    pats = set()
    for m in re.finditer(r'["\'`]([^"\'`]{4,120})["\'`]', js):
        s = m.group(1)
        if any(k in s for k in ("aweme", "/web/", "detail", "note_id", "aweme_id", "item_ids", "api")):
            if "monitor" in s or "w3.org" in s or "svg" in s:
                continue
            pats.add(s)
    print("--- 疑似接口字符串 ---")
    for p in sorted(pats):
        print("  ", p[:160])

    # 找 axios 调用：.get( / .post( 附近的变量
    print("\n--- axios 调用上下文 ---")
    for m in re.finditer(r'\.(?:get|post)\(\s*([^,)]{3,90})', js):
        print("  ", m.group(1)[:120])

    # 找 baseURL 配置
    print("\n--- baseURL ---")
    for m in re.finditer(r'.{60}baseURL.{80}', js):
        print("  ", m.group(0)[:200].replace("\n", " "))

    # 拼接式路径：如 "/aweme" + "/v1" ...
    print("\n--- 拼接式路径（含 + 的 URL 片段）---")
    for m in re.finditer(r'"[^"]{3,40}"\s*\+\s*"[^"]{3,60}"', js):
        print("  ", m.group(0)[:160])


if __name__ == "__main__":
    main()
