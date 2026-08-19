# -*- coding: utf-8 -*-
"""实验：对比不同后缀（cus / dhzx）与 RC4 key 组合，找出当前可用的 a_bogus 配置"""
import json
import random
import re
import string
import subprocess
import sys
import urllib.parse
import urllib.request

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
)
HOST = "https://www.douyin.com"
AWEME_ID = "7631053543941117618"

COMMON_PARAMS = {
    "device_platform": "webapp",
    "aid": "6383",
    "channel": "channel_pc_web",
    "version_code": "190600",
    "version_name": "19.6.0",
    "update_version_code": "170400",
    "pc_client_type": "1",
    "cookie_enabled": "true",
    "browser_language": "zh-CN",
    "browser_platform": "MacIntel",
    "browser_name": "Chrome",
    "browser_version": "125.0.0.0",
    "browser_online": "true",
    "engine_name": "Blink",
    "os_name": "Mac OS",
    "os_version": "10.15.7",
    "cpu_core_num": "8",
    "device_memory": "8",
    "engine_version": "109.0",
    "platform": "PC",
    "screen_width": "2560",
    "screen_height": "1440",
    "effective_type": "4g",
    "round_trip_time": "50",
}


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


def sign_with_variant(query, ua, suffix, rc4_key, args):
    """用指定后缀/密钥的 JS 生成签名"""
    js = open("poc/lib/douyin.js", encoding="utf-8").read()
    # 动态改后缀与 rc4 key 与 Arguments
    js = js.replace('suffix = "cus"', f'suffix = "{suffix}"')
    js = js.replace('"cus",\n        arguments', f'"{suffix}",\n        arguments')
    js = js.replace('String.fromCharCode.apply(null, [121])', f'String.fromCharCode.apply(null, [{rc4_key}])')
    js = js.replace("return sign(params, userAgent, [0, 1, 14])", f"return sign(params, userAgent, [{args}])")
    js = js.replace("return sign(params, userAgent, [0, 1, 8])", f"return sign(params, userAgent, [{args}])")
    code = js + "\nprocess.stdout.write(sign_datail(process.argv[2], process.argv[3]));"
    proc = subprocess.run(
        ["node", "-e", code, "x", query, ua],
        capture_output=True, text=True, timeout=30,
    )
    if proc.returncode != 0:
        return None, proc.stderr[-200:]
    sig = proc.stdout.strip()
    return sig, None


def fetch(url, cookie=""):
    headers = {
        "User-Agent": UA,
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
        return e.code, e.read().decode("utf-8", "ignore")[:200]


def main():
    cookie = register_ttwid()
    print("ttwid:", "OK" if cookie else "无")

    params = dict(COMMON_PARAMS)
    params.update({"webid": rand_webid(), "msToken": rand_ms_token(), "aweme_id": AWEME_ID})
    query = urllib.parse.urlencode(params)
    print("query 长度:", len(query))

    variants = [
        ("cus", 121, "0, 1, 14"),   # MediaCrawler 现用
        ("dhzx", 121, "0, 1, 14"),  # abogus_new 提示的新后缀
        ("cus", 121, "0, 1, 8"),
        ("dhzx", 121, "0, 1, 8"),
        ("cus", 14, "0, 1, 14"),
        ("dhzx", 14, "0, 1, 14"),
    ]
    for suffix, key, args in variants:
        sig, err = sign_with_variant(query, UA, suffix, key, args)
        if err:
            print(f"suffix={suffix} key={key} args=[{args}] -> 签名错误: {err[:100]}")
            continue
        url = f"{HOST}/aweme/v1/web/aweme/detail/?{query}&a_bogus={urllib.parse.quote(sig)}"
        status, body = fetch(url, cookie)
        print(f"suffix={suffix} key={key} args=[{args}] sig_len={len(sig)} -> HTTP {status}, body {len(body)}: {body[:120]}")


if __name__ == "__main__":
    main()
