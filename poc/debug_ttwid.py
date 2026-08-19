# -*- coding: utf-8 -*-
"""调试：注册 ttwid 后带 cookie 请求抖音接口，验证能否绕过签名"""
import json
import urllib.request

DESKTOP_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
NOTE_ID = "7631053543941117618"


def post_json(url, data, headers):
    req = urllib.request.Request(url, data=json.dumps(data).encode(), headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=20) as r:
        return r.read().decode("utf-8", "ignore")


def get(url, headers):
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, r.read().decode("utf-8", "ignore")
    except Exception as e:
        return None, f"{type(e).__name__}: {e}"


def main():
    # 1. 注册 ttwid
    try:
        body = post_json(
            "https://ttwid.bytedance.com/ttwid/union/register/",
            {
                "region": "CN",
                "aid": 6383,
                "needFid": False,
                "service": "www.douyin.com",
                "migrate_info": {"ticket": "", "source": "node"},
                "cbUrlProtocol": "https",
                "union": True,
            },
            {
                "User-Agent": DESKTOP_UA,
                "Content-Type": "application/json",
                "Origin": "https://www.douyin.com",
                "Referer": "https://www.douyin.com/",
            },
        )
        print("ttwid 注册响应:", body[:300])
        data = json.loads(body)
        ttwid = data.get("data", {}).get("ttwid", "")
        print("ttwid:", ttwid[:80])
    except Exception as e:
        print("ttwid 注册失败:", e)
        return

    cookie = f"ttwid={ttwid}"
    h = {"User-Agent": DESKTOP_UA, "Referer": "https://www.douyin.com/", "Cookie": cookie}

    # 2. 带 cookie 试各接口
    for name, url in [
        ("iteminfo", f"https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids={NOTE_ID}"),
        ("aweme/detail 桌面", f"https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id={NOTE_ID}"),
        ("aweme/detail ies", f"https://www.iesdouyin.com/aweme/v1/web/aweme/detail/?aweme_id={NOTE_ID}"),
        ("share/video 页面", f"https://www.iesdouyin.com/share/video/{NOTE_ID}/"),
    ]:
        status, body = get(url, h)
        print(f"=== {name}: HTTP {status}, 长度 {len(body)}")
        print(f"    {body[:300]}")
        print()


if __name__ == "__main__":
    main()
