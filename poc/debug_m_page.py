# -*- coding: utf-8 -*-
"""调试：检查 m.douyin.com/share/note 页面里的 desc 与内嵌数据"""
import json
import re
import urllib.request

MOBILE_UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)
NOTE_ID = "7631053543941117618"


def fetch(url, ua, referer):
    h = {"User-Agent": ua, "Accept": "*/*", "Referer": referer}
    req = urllib.request.Request(url, headers=h)
    with urllib.request.urlopen(req, timeout=20) as r:
        return r.read().decode("utf-8", "ignore")


def main():
    body = fetch(
        f"https://m.douyin.com/share/note/{NOTE_ID}",
        MOBILE_UA,
        "https://www.douyin.com/",
    )
    # desc 出现的位置
    for m in re.finditer(r"desc", body):
        s = max(0, m.start() - 120)
        print("desc 上下文:", body[s:m.start() + 80].replace("\n", " ")[:240])
        print("---")
    # 所有内嵌 script JSON 数据块
    print("===== script 里的 JSON 数据块 =====")
    for m in re.finditer(r"<script[^>]*>(.*?)</script>", body, re.S):
        s = m.group(1)
        if len(s) > 200 and s.strip().startswith(("{", "window.")):
            print(s[:600])
            print("=====")


if __name__ == "__main__":
    main()
