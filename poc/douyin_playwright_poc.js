// 抖音解析 POC v5：Playwright 真实浏览器方案（解析图集/视频完整信息）
// 原理：www.douyin.com/note/{id} 页面会异步调用 aweme/v1/web/aweme/post/ 等接口，
//       截获响应即可拿到作品的 images / video 数据。
// 用法: node poc/douyin_playwright_poc.js "<链接>"
const { chromium } = require('playwright');

const input = process.argv[2] || '';

function extractImages(aweme) {
  if (!aweme.images || !aweme.images.length) return [];
  return aweme.images
    .map((img) => (img.url_list || []).find((u) => u))
    .filter(Boolean);
}

async function main() {
  const browser = await chromium.launch({
    channel: 'chrome',
    headless: true,
    args: [
      '--disable-blink-features=AutomationControlled',
      '--no-sandbox',
      '--disable-dev-shm-usage',
      '--disable-crash-reporter',
    ],
  });
  const context = await browser.newContext({
    userAgent:
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36',
    viewport: { width: 1366, height: 768 },
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    extraHTTPHeaders: { 'Accept-Language': 'zh-CN,zh;q=0.9' },
  });
  await context.addInitScript(() => {
    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
    Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
    Object.defineProperty(navigator, 'languages', { get: () => ['zh-CN', 'zh'] });
    window.chrome = { runtime: {} };
  });

  const page = await context.newPage();
  const hits = [];
  page.on('response', async (resp) => {
    const url = resp.url();
    if (/(aweme\/post|aweme\/detail|slidesinfo|iteminfo)/.test(url) && !hits.find((h) => h.url === url)) {
      try {
        const ct = resp.headers()['content-type'] || '';
        if (ct.includes('json')) {
          const body = await resp.text();
          if (body && body.length > 50) hits.push({ url, body });
        }
      } catch (e) { /* ignore */ }
    }
  });

  console.log('[1] 打开页面:', input);
  await page.goto(input, { waitUntil: 'domcontentloaded', timeout: 45000 });
  console.log('[2] 等待渲染与接口返回...');
  await page.waitForTimeout(10000);
  try {
    for (let i = 0; i < 4; i++) {
      await page.mouse.wheel(0, 1200);
      await page.waitForTimeout(700);
    }
  } catch (e) { /* ignore */ }
  await page.waitForTimeout(1500);

  // 1) 优先从接口响应里解析
  let parsed = null;
  for (const h of hits) {
    try {
      const j = JSON.parse(h.body);
      const list = j.aweme_list || (j.aweme_detail ? [j.aweme_detail] : []) ||
        (j.aweme_details ? j.aweme_details : []);
      if (list.length) {
        for (const aweme of list) {
          const imgs = extractImages(aweme);
          const play = aweme.video?.play_addr;
          if (imgs.length || play) {
            parsed = {
              platform: '抖音',
              type: imgs.length ? '图集' : '视频',
              title: (aweme.desc || '').trim() || '抖音作品',
              author: aweme.author?.nickname || '',
              cover: aweme.video?.cover?.url_list?.[0] || '',
              image_urls: imgs,
              video_url: play ? (play.url_list || []).find((u) => u) : '',
              duration_sec: aweme.duration || 0,
              from: h.url.split('?')[0].split('/').pop(),
            };
            break;
          }
        }
      }
    } catch (e) { /* ignore */ }
    if (parsed) break;
  }

  if (!parsed) {
    console.log('[x] 接口响应里没有作品数据，改用 DOM 提取');
    const dom = await page.evaluate(() => {
      const imgs = Array.from(document.querySelectorAll('img'))
        .map((i) => i.src || i.getAttribute('data-src') || '')
        .filter((s) => s && /douyinpic/.test(s));
      return { imgs, title: document.title };
    });
    console.log('    DOM 标题:', dom.title.slice(0, 80));
    console.log('    DOM 图片数:', dom.imgs.length);
    dom.imgs.slice(0, 5).forEach((u) => console.log('     ', u.slice(0, 140)));
    await browser.close();
    return;
  }

  console.log('[3] 解析结果:');
  console.log(JSON.stringify(parsed, null, 2));

  // 2) 顺带验证图片直链可下载
  if (parsed.image_urls.length) {
    const url = parsed.image_urls[0];
    const resp = await page.request.get(url);
    console.log('[4] 第一张图直链验证: HTTP', resp.status(), '类型', resp.headers()['content-type'], '大小', resp.headers()['content-length']);
  }

  await browser.close();
}

main().catch((e) => {
  console.error('FAILED:', e.message);
  process.exit(1);
});
