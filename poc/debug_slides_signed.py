# -*- coding: utf-8 -*-
"""实验4：slidesinfo + a_bogus 签名 + ttwid cookie"""
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
        "referer": f"https://www.douyin.com/note/{NOTE_ID}",
        "Origin": "https://www.douyin.com",
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

    base = {
        "device_platform": "webapp", "aid": "6383", "channel": "channel_pc_web",
        "update_version_code": "170400", "pc_client_type": "1",
        "version_code": "170400", "version_name": "17.4.0",
        "cookie_enabled": "true", "screen_width": "2560", "screen_height": "1440",
        "browser_language": "zh-CN", "browser_platform": "Win32", "browser_name": "Chrome",
        "browser_version": "135.0.0.0", "browser_online": "true", "engine_name": "Blink",
        "engine_version": "135.0.0.0", "os_name": "Windows", "os_version": "10",
        "cpu_core_num": "20", "device_memory": "8", "platform": "PC", "downlink": "0.55",
        "effective_type": "3g", "round_trip_time": "500",
        "webid": rand_webid(),
        "msToken": "".join(random.choice(string.ascii_letters + string.digits + "=") for _ in range(120)),
    }

    api_cases = [
        ("slidesinfo", {"aweme_ids": NOTE_ID, "aid": "1128", "request_source": "share"}),
        ("slidesinfo2", {"aweme_ids": NOTE_ID, "aweme_type": "68", "aid": "1128"}),
        ("iteminfo", {"item_ids": NOTE_ID, "use_new_select_scope": "0"}),
    ]
    for api_name, extra in api_cases:
        p = dict(base)
        p.update(extra)
        query = urllib.parse.urlencode(p)
        sig = sign(query, UA)
        url = f"https://www.douyin.com/web/api/v2/aweme/{api_name}?{query}&a_bogus={urllib.parse.quote(sig)}"
        status, body = get(url, cookie)
        print(f"=== {api_name}: HTTP {status} 长度 {len(body)}")
        print(f"    {body[:400]}")
        print()


if __name__ == "__main__":
    main()
