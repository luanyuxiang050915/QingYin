# -*- coding: utf-8 -*-
"""调试：逐步打印抖音解析每一步的中间结果，定位失败原因"""
import json
import re
import sys
import urllib.request

MOBILE_UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)
URL_RE = re.compile(
    r"https?://[a-zA-Z0-9.-]*(?:douyin|iesdouyin)\.com/[A-Za-z0-9?&=/%._~:#+@-]*"
)
ID_RE = re.compile(r"/(?:video|note)/(\d+)")
MODAL_ID_RE = re.compile(r"[?&]modal_id=(\d+)")


def fetch(url, referer=None):
    headers = {"User-Agent": MOBILE_UA, "Accept": "*/*"}
    if referer:
        headers["Referer"] = referer
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.read().decode("utf-8", "ignore"), resp.geturl(), resp.status


def main(text):
    raw_url = URL_RE.search(text)
    print("[1] 提取到的链接:", raw_url and raw_url.group(0))
    if not raw_url:
        print("    失败：未找到链接")
        return
    url = raw_url.group(0)

    final_url = url
    if "v.douyin.com" in url:
        print("[2] 短链跳转中...")
        html, final_url, status = fetch(url)
        print("    最终地址:", final_url, "HTTP", status)
    else:
        print("[2] 非短链，无需跳转")

    m = ID_RE.search(final_url) or MODAL_ID_RE.search(final_url)
    if not m:
        print("[3] 失败：无法提取作品 ID")
        return
    video_id = m.group(1)
    print("[3] 作品 ID:", video_id, "| 类型:", "/note/" if "/note/" in final_url else "/video/")

    is_note = "/note/" in final_url
    kinds = ["note", "video"] if is_note else ["video", "note"]
    html = None
    used = None
    for kind in kinds:
        try:
            html, _, status = fetch(
                f"https://www.iesdouyin.com/share/{kind}/{video_id}/",
                referer="https://www.douyin.com/",
            )
            used = kind
            print(f"[4] 分享页请求成功 ({kind} 页, HTTP {status}), HTML 长度 {len(html)}")
            break
        except Exception as e:
            print(f"[4] 分享页请求失败 ({kind} 页): {e}")
    if html is None:
        print("[4] 失败：两个分享页都请求失败")
        return

    m = re.search(
        r"<script[^>]*>\s*window\._ROUTER_DATA\s*=\s*(.*?)</script>",
        html,
        re.S,
    )
    if not m:
        # 打印页面开头，看看返回的是什么（可能是风控验证页）
        print("[5] 失败：页面里没有 _ROUTER_DATA！页面开头 300 字符：")
        print("    ", html[:300].replace("\n", " "))
        print("    是否含关键词：验证码=", "验证码" in html, " 安全验证=", "安全验证" in html,
              " _ROUTER_DATA=", "_ROUTER_DATA" in html)
        return
    root = json.loads(m.group(1).strip().rstrip(";"))
    print("[5] _ROUTER_DATA 解析成功，顶层 keys:", list(root.keys()))

    # 找出所有包含 images / play_addr / desc / nickname 的路径
    found = {"images": [], "play_addr": [], "desc": [], "nickname": []}

    def walk(node, path):
        if isinstance(node, dict):
            for k, v in node.items():
                if k in found:
                    found[k].append(f"{path}.{k}")
                walk(v, f"{path}.{k}")
        elif isinstance(node, list):
            for i, v in enumerate(node):
                walk(v, f"{path}[{i}]")

    walk(root, "root")
    for k, paths in found.items():
        print(f"[6] 字段 {k}: 出现 {len(paths)} 次")
        for p in paths[:5]:
            print("      ", p)
    print("[6] root 实际结构（前 800 字符）:")
    print(json.dumps(root, ensure_ascii=False)[:800])


if __name__ == "__main__":
    arg = sys.argv[1] if len(sys.argv) > 1 else ""
    if not arg:
        arg = input("粘贴抖音分享文本：").strip()
    main(arg)
