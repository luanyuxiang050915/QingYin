# -*- coding: utf-8 -*-
"""实验2：参数变体对比（test.html 风格参数 + uifid + 不同 UA + 页面内 msToken）"""
import json
import random
import re
import string
import subprocess
import urllib.parse
import urllib.request

CLI = "poc/lib/abogus_cli.js"
AWEME_ID = "7631053543941117618"

UA135 = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
)


def rand_webid():
    def e(t):
        if t is not None:
            return str(t ^ (int(16 * random.random()) >> (t // 4)))
        return "".join([str(int(1e7)), "-", str(int(1e3)), "-", str(int(4e3)), "-", str(int(8e3)), "-", str(int(1e11))])
    return "".join(e(int(x)) if x in "018" else x for x in e(None)).replace("-", "")[:19]


def rand_ms_token(length=120):
    base = string.ascii_letters + string.digits + "="
    return "".join(random.choice(base) for _ in range(length))


def register_ttwid():
    try:
        body = json.dumps({
            "region": "CN", "aid": 6383, "needFid": False, "service": "www.douyin.com",
            "migrate_info": {"ticket": "", "source": "node"}, "cbUrlProtocol": "https", "union": True,
        }).encode()
        req = urllib.request.Request(
            "https://ttwid.bytedance.com/ttwid/union/register/", data=body,
            headers={"User-Agent": UA135, "Content-Type": "application/json",
                     "Origin": "https://www.douyin.com", "Referer": "https://www.douyin.com/"},
            method="POST")
        with urllib.request.urlopen(req, timeout=20) as resp:
            data = json.loads(resp.read().decode("utf-8", "ignore"))
        redirect_url = data.get("redirect_url", "")
        if not redirect_url:
            return ""
        req2 = urllib.request.Request(redirect_url, headers={"User-Agent": UA135})
        with urllib.request.urlopen(req2, timeout=20) as resp2:
            cookie = resp2.headers.get("Set-Cookie", "")
        m = re.search(r"ttwid=([^;]+)", cookie)
        return f"ttwid={m.group(1)}" if m else ""
    except Exception as e:
        print(f"  [warn] ttwid: {e}")
        return ""


def sign(query, ua):
    proc = subprocess.run(["node", CLI, query, ua], capture_output=True, text=True, timeout=30)
    if proc.returncode != 0:
        return None
    return proc.stdout.strip()


def fetch(url, cookie, ua):
    headers = {
        "User-Agent": ua,
        "Accept": "application/json, text/plain, */*",
        "Referer": "https://www.douyin.com/",
        "sec-fetch-site": "same-origin", "sec-fetch-mode": "cors", "sec-fetch-dest": "empty",
        "accept-language": "zh-CN,zh;q=0.9,en;q=0.8",
    }
    if cookie:
        headers["Cookie"] = cookie
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            body = resp.read().decode("utf-8", "ignore")
            return resp.status, body
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "ignore")[:150]


def grab_ms_token_from_share_page():
    """从 m.douyin 分享页 HTML 找 msToken"""
    try:
        req = urllib.request.Request(
            f"https://m.douyin.com/share/note/{AWEME_ID}",
            headers={"User-Agent": UA135, "Accept": "*/*", "Referer": "https://www.douyin.com/"})
        with urllib.request.urlopen(req, timeout=20) as resp:
            html = resp.read().decode("utf-8", "ignore")
        m = re.search(r"msToken=([A-Za-z0-9_\-=]{20,})", html)
        if m:
            return m.group(1)
        # 也找 localStorage 或 xmst
        for pat in (r'"xmst"\s*:\s*"([^"]+)"', r'xmst=([A-Za-z0-9_\-=]{20,})'):
            m = re.search(pat, html)
            if m:
                return m.group(1)
    except Exception as e:
        print("  [warn] 页面取 msToken:", e)
    return ""


def main():
    cookie = register_ttwid()
    print("ttwid:", "OK" if cookie else "无")
    page_token = grab_ms_token_from_share_page()
    print("页面 msToken:", "找到" if page_token else "无", (page_token or "")[:20])

    webid = rand_webid()

    # 参数组：test.html 风格（V17.4.0 / Chrome135 / 含 pc_libra_divert 等）
    base_test = {
        "device_platform": "webapp", "aid": "6383", "channel": "channel_pc_web",
        "update_version_code": "170400", "pc_client_type": "1", "pc_libra_divert": "Windows",
        "support_h265": "1", "support_dash": "1", "version_code": "170400", "version_name": "17.4.0",
        "cookie_enabled": "true", "screen_width": "2560", "screen_height": "1440",
        "browser_language": "zh-CN", "browser_platform": "Win32", "browser_name": "Chrome",
        "browser_version": "135.0.0.0", "browser_online": "true", "engine_name": "Blink",
        "engine_version": "135.0.0.0", "os_name": "Windows", "os_version": "10",
        "cpu_core_num": "20", "device_memory": "8", "platform": "PC", "downlink": "0.55",
        "effective_type": "3g", "round_trip_time": "500",
    }
    # 参数组：我原来的（MacIntel 风格）
    base_mine = {
        "device_platform": "webapp", "aid": "6383", "channel": "channel_pc_web",
        "version_code": "190600", "version_name": "19.6.0", "update_version_code": "170400",
        "pc_client_type": "1", "cookie_enabled": "true", "browser_language": "zh-CN",
        "browser_platform": "MacIntel", "browser_name": "Chrome", "browser_version": "125.0.0.0",
        "browser_online": "true", "engine_name": "Blink", "os_name": "Mac OS", "os_version": "10.15.7",
        "cpu_core_num": "8", "device_memory": "8", "engine_version": "109.0", "platform": "PC",
        "screen_width": "2560", "screen_height": "1440", "effective_type": "4g",
        "round_trip_time": "50",
    }

    dummy_uifid = "a" * 128
    cases = []
    for name, base, ua in [("test135", base_test, UA135), ("mine", base_mine, UA135)]:
        for use_page_token in (True, False):
            for with_uifid in (False, True):
                p = dict(base)
                p["webid"] = webid
                p["msToken"] = page_token if use_page_token else rand_ms_token()
                if with_uifid:
                    p["uifid"] = dummy_uifid
                p["aweme_id"] = AWEME_ID
                cases.append((f"{name} pageToken={use_page_token} uifid={with_uifid}", p, ua))

    for name, params, ua in cases:
        query = urllib.parse.urlencode(params)
        sig = sign(query, ua)
        if not sig:
            print(f"{name} -> 签名失败")
            continue
        url = f"https://www.douyin.com/aweme/v1/web/aweme/detail/?{query}&a_bogus={urllib.parse.quote(sig)}"
        status, body = fetch(url, cookie, ua)
        print(f"{name} sig_len={len(sig)} -> HTTP {status} body {len(body)}: {body[:100]}")


if __name__ == "__main__":
    main()
