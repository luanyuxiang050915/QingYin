# -*- coding: utf-8 -*-
"""实验5：slidesinfo 裸参数 + a_bogus（模拟 m.douyin 页面的真实调用）"""
import json
import random
import re
import string
import subprocess
import urllib.parse
import urllib.request

CLI = "poc/lib/abogus_cli.js"
NOTE_ID = "7631053543941117618"
UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)


def rand_webid():
    def e(t):
        if t is not None:
            return str(t ^ (int(16 * random.random()) >> (t // 4)))
        return "".join([str(int(1e7)), "-", str(int(1e3)), "-", str(int(4e3)), "-", str(int(8e3)), "-", str(int(1e11))])
    return "".join(e(int(x)) if x in "018" else x for x in e(None)).replace("-", "")[:19]


def register_ttwid():
    try:
        body = json.dumps({
            "region": "CN", "aid": 6383, "needFid": False, "service": "www.douyin.com",
            "migrate_info": {"ticket": "", "source": "node"}, "cbUrlProtocol": "https", "union": True,
        }).encode()
        req = urllib.request.Request(
            "https://ttwid.bytedance.com/ttwid/union/register/", data=body,
            headers={"User-Agent": UA, "Content-Type": "application/json",
                     "Origin": "https://www.douyin.com", "Referer": "https://www.douyin.com/"},
            method="POST")
        with urllib.request.urlopen(req, timeout=20) as resp:
            data = json.loads(resp.read().decode("utf-8", "ignore"))
        redirect_url = data.get("redirect_url", "")
        if not redirect_url:
            return ""
        req2 = urllib.request.Request(redirect_url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req2, timeout=20) as resp2:
            cookie = resp2.headers.get("Set-Cookie", "")
        m = re.search(r"ttwid=([^;]+)", cookie)
        return f"ttwid={m.group(1)}" if m else ""
    except Exception as e:
        print(f"  [warn] ttwid: {e}")
        return ""


def sign(query, ua):
    proc = subprocess.run(["node", CLI, query, ua], capture_output=True, text=True, timeout=30)
    return proc.stdout.strip() if proc.returncode == 0 else None


def get(url, cookie):
    headers = {
        "User-Agent": UA,
        "Accept": "application/json, text/plain, */*",
        "Agw-Js-Conv": "str",
        "server-token": "1",
        "X-Tlb-Cluster": "internal_lb_core_api",
        "referer": f"https://www.m.douyin.com/share/note/{NOTE_ID}",
        "Origin": "https://m.douyin.com",
        "host": "m.douyin.com",
    }
    if cookie:
        headers["Cookie"] = cookie
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            return resp.status, resp.read().decode("utf-8", "ignore")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "ignore")[:300]


def main():
    cookie = register_ttwid()
    print("ttwid:", "OK" if cookie else "无")
    webid = rand_webid()

    # 1. m.douyin.com slidesinfo 裸参数（mobile 页面风格）
    cases = [
        ("m slides bare", "https://m.douyin.com/web/api/v2/aweme/slidesinfo/", {"aweme_ids": NOTE_ID, "aid": "1128", "request_source": "share"}),
        ("m slides bare2", "https://m.douyin.com/web/api/v2/aweme/slidesinfo/", {"aweme_ids": NOTE_ID}),
        ("www slides bare", "https://www.douyin.com/web/api/v2/aweme/slidesinfo/", {"aweme_ids": NOTE_ID, "aid": "1128", "request_source": "share"}),
    ]
    for name, base_url, extra in cases:
        p = dict(extra)
        query = urllib.parse.urlencode(p)
        sig = sign(query, UA)
        url = f"{base_url}?{query}&a_bogus={urllib.parse.quote(sig)}"
        status, body = get(url, cookie)
        print(f"=== {name} sig={len(sig)}: HTTP {status} 长度 {len(body)}")
        print(f"    {body[:400]}")
        print()


if __name__ == "__main__":
    main()
