# -*- coding: utf-8 -*-
"""实验3：用 AGW 网关头调用 slidesinfo / iteminfo 接口"""
import json
import urllib.parse
import urllib.request

NOTE_ID = "7631053543941117618"
UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)

AGW_HEADERS = {
    "User-Agent": UA,
    "Accept": "application/json, text/plain, */*",
    "Agw-Js-Conv": "str",
    "server-token": "1",
    "X-Tlb-Cluster": "internal_lb_core_api",
    "referer": f"https://www.m.douyin.com/share/note/{NOTE_ID}",
    "host": "m.douyin.com",
    "Origin": "https://m.douyin.com",
}


def get(url, headers, timeout=20):
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8", "ignore")
            return resp.status, body
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "ignore")[:300]


def main():
    cases = [
        ("m slidesinfo", f"https://m.douyin.com/web/api/v2/aweme/slidesinfo/?aweme_ids={NOTE_ID}&aid=1128&request_source=share"),
        ("m slidesinfo no aid", f"https://m.douyin.com/web/api/v2/aweme/slidesinfo/?aweme_ids={NOTE_ID}"),
        ("m iteminfo", f"https://m.douyin.com/web/api/v2/aweme/iteminfo/?item_ids={NOTE_ID}&use_new_select_scope=0"),
        ("www slidesinfo", f"https://www.douyin.com/web/api/v2/aweme/slidesinfo/?aweme_ids={NOTE_ID}&aid=1128"),
        ("www iteminfo", f"https://www.douyin.com/web/api/v2/aweme/iteminfo/?item_ids={NOTE_ID}&use_new_select_scope=0"),
        ("ies slidesinfo", f"https://www.iesdouyin.com/web/api/v2/aweme/slidesinfo/?aweme_ids={NOTE_ID}&aid=1128"),
    ]
    for name, url in cases:
        h = dict(AGW_HEADERS)
        if "www.douyin.com" in url:
            h["host"] = "www.douyin.com"
            h["Origin"] = "https://www.douyin.com"
            h["referer"] = f"https://www.douyin.com/note/{NOTE_ID}"
        if "iesdouyin" in url:
            h["host"] = "www.iesdouyin.com"
            h["Origin"] = "https://www.iesdouyin.com"
            h["referer"] = f"https://www.iesdouyin.com/share/note/{NOTE_ID}/"
        status, body = get(url, h)
        print(f"=== {name}: HTTP {status} 长度 {len(body)}")
        print(f"    {body[:300]}")
        print()


if __name__ == "__main__":
    main()
