# -*- coding: utf-8 -*-
"""调试：确认 note 页图片直链藏在哪里（HTML / JS chunk），并提取全部 URL"""
import re
import urllib.request

UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
)
NOTE_ID = "7631053543941117618"


def fetch(url, referer=None):
    h = {"User-Agent": UA, "Accept": "*/*"}
    if referer:
        h["Referer"] = referer
    req = urllib.request.Request(url, headers=h)
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read().decode("utf-8", "ignore")


def extract_img_urls(text):
    return re.findall(r"https://p3-pc-sign\.douyinpic\.com/obj/[^\"'\s\\]+", text)


def main():
    html = fetch(
        f"https://www.iesdouyin.com/share/note/{NOTE_ID}/",
        referer="https://www.douyin.com/",
    )
    html_imgs = extract_img_urls(html)
    print("HTML 中 douyinpic 图片 URL 数量:", len(html_imgs))
    if html_imgs:
        print("  示例:", html_imgs[0][:120])

    # 拉取 note_(id)/page JS chunk
    srcs = re.findall(r'<script[^>]*src="([^"]*)"[^>]*>', html)
    page_js = [s for s in srcs if "note_(id)" in s or "async/" in s]
    print("\nasync JS chunks:", page_js)
    js_imgs = []
    for src in page_js:
        full = src if src.startswith("http") else "https:" + src
        js = fetch(full, referer="https://www.iesdouyin.com/")
        imgs = extract_img_urls(js)
        js_imgs += imgs
        print(f"  {src} -> douyinpic URL {len(imgs)} 个")

    uniq = list(dict.fromkeys(js_imgs))
    print("\nJS 中唯一图片 URL 数量:", len(uniq))
    for u in uniq[:5]:
        print("  ", u[:140])
    print("  ...")
    for u in uniq[-3:]:
        print("  ", u[:140])

    # 找 JS 中图片数组的上下文，确认是轮播图数据
    if js_imgs:
        first = js_imgs[0]
        idx = js.find(first)
        ctx = js[max(0, idx - 300): idx + 100]
        print("\n--- 第一个图片 URL 附近的 JS 上下文 ---")
        print(ctx[:400])
        # 找附近的字段名（如 images / urlList / note）
        for kw in ("images", "urlList", "imageList", "carousel", "cover"):
            print(f"  含 {kw}:", kw in js)


if __name__ == "__main__":
    main()
