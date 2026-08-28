# 清印 QingYin · 视频下载去水印（Android / Windows 桌面版）

粘贴分享链接，一键下载无水印视频与抖音图集。支持 **抖音 / B站 / 快手 / X(推特) / 小红书 / 微博** 六个平台（桌面版支持其中 5 个，详见[桌面版](#桌面版windows)）。

## 功能特性

- 粘贴 / 清空输入框（有内容时按钮自动变成"清空"）
- 解析展示：封面、标题、作者、时长
- 下载无水印视频，带实时进度
- **抖音图集（图文笔记）**：无水印原图逐张下载，全部完成后统一存入相册
- **抖音解析适配平台改版**：WebView 真实浏览器方案，平台加签名/风控也能解析（详见[维护记录](#六维护记录2026-08-抖音改版)）
- **暂停 / 继续（断点续传）**：抖音、B站、快手、X、小红书、微博直链均已验证支持 Range
- **删除**：删除未完成的下载，或删除已保存的视频与历史记录
- 下载历史列表
- 保存到相册「视频去水印」文件夹（Android 10+ 免存储权限）

## 下载 APK（正式版 v0.1.0）

[点击下载 QingYin-v0.1.0-release.apk](https://github.com/luanyuxiang050915/QingYin/raw/main/apk/QingYin-v0.1.0-release.apk)

> 正式签名版，可直接安装使用。安装时如提示"未知来源应用"，允许即可。
>
> ⚠️ 注意：v0.1.0 正式版**不含**抖音 WebView 解析修复（2026-08 抖音改版后抖音解析失效）。最新修复在调试版 `android/app/build/outputs/apk/debug/app-debug.apk`（含抖音 WebView 方案），需真机验证后随下一版本发布。

## 目录结构

- [计划书.md](计划书.md) — 立项评估与方案
- `poc/` — 各平台解析链路验证脚本（Python + Node.js，无需安卓环境）
  - `douyin_playwright_poc.js` — 抖音解析验证（WebView 方案原型，需 Node.js + Chrome）
  - `douyin_abogus_poc.py` — 抖音 a_bogus 签名实验脚本（当前被服务端风控拒绝，仅作算法参考）
  - `lib/` — a_bogus 签名算法 JS 参考实现（Apache-2.0）
  - `debug_*.py` — 历次排查用的诊断脚本（留存备查）
- `android/` — Android Studio 工程（Kotlin + Jetpack Compose）
- `desktop/` — Windows 桌面版工程（Compose Desktop + jpackage，复用 Android 版解析器源码）

## 桌面版（Windows）

清印提供 **Windows 桌面版**（`desktop/` 工程，Compose Desktop + jpackage），无需安装 Java 即可运行：

- **功能**：粘贴链接 → 解析 → 下载无水印视频 / 图集 → 保存到「下载\视频去水印」文件夹；支持 **B站 / 快手 / X(推特) / 小红书 / 微博** + **任意网站视频链接（通用解析 yt-dlp，支持 Pornhub 等 1000+ 站点）**（抖音桌面版暂不支持，详见[维护记录](#六维护记录2026-08-抖音改版)）
- **通用链接说明**：非自研平台链接自动走 yt-dlp（`yt-dlp.exe` 随安装包分发，位于 `app/` 目录）；仅支持直链 mp4（无需 ffmpeg），m3u8 分片流站点暂不支持
- **安装方式**：
  - 安装包（推荐）：`desktop/build/installer/QingYin-0.2.0.exe`（或 `.msi`），双击 → 下一步 → 完成，自动创建桌面快捷方式与开始菜单项（分发包 `desktop/build/QingYin-setup-0.2.0.zip`）
  - 免安装版：`desktop/build/jpackage-app/QingYin/QingYin.exe`（解压即用，`app` 与 `runtime` 目录需与 exe 同目录）
- **构建**：`cd desktop && .\gradlew.bat createDistributable`；安装包需额外安装 [WiX 3.x](https://wixtoolset.org)（jpackage 依赖 candle/light），生成方式见 `desktop/build.gradle.kts` 中的 `copyJpackageInput` 任务 + 手动 jpackage；通用链接需在 `desktop/build/tools/` 放置 `yt-dlp.exe`
- **代码复用**：解析器/下载器与 Android 版共用同一份纯 JVM 源码（`desktop/build.gradle.kts` 里通过共享源集引用），修改一处两端生效
- 抖音解析依赖 Android WebView 浏览器环境，桌面端暂无等价方案，后续可评估 JCEF（内置 Chromium）支持

## 一、POC 快速验证（无需安卓环境）

```powershell
python poc/bilibili_poc.py "https://www.bilibili.com/video/BV1GJ411x7h7"
python poc/kuaishou_poc.py "https://www.kuaishou.com/short-video/3xtuvnw4dpeuq79"
python poc/x_poc.py "https://x.com/RafaelNadal/status/1844308861492318594"
python poc/xhs_poc.py "BladeSage Q5... http://xhslink.cn/o/soAvHtZJcw"
python poc/weibo_poc.py "https://m.weibo.cn/status/5280806310516853"
```

抖音因平台改版（见[维护记录](#六维护记录2026-08-抖音改版)），需用浏览器方案验证：

```powershell
# 需要本机安装 Node.js 与 Chrome（任意版本）
node poc/douyin_playwright_poc.js "https://v.douyin.com/ZapNucLbiS0/"
```

脚本输出 JSON：标题、作者、封面、无水印视频直链（图集输出原图直链列表）。`poc/douyin_abogus_poc.py` 为实验性脚本，纯 HTTP + a_bogus 签名当前会被服务端拒绝，仅作签名算法参考。

## 二、构建 APK

1. 安装 [Android Studio](https://developer.android.com/studio)（Ladybug 或更新版本，自带 JDK 与 SDK）；
2. File → Open → 选择 `android` 目录；
3. 等待 Gradle 同步完成（国内网络建议保留工程内已配置的腾讯 / 阿里云镜像）；
4. 连接安卓真机（开启 USB 调试）或创建模拟器；
5. 点击 Run；也可以命令行构建：

```powershell
cd android
.\gradlew.bat assembleDebug
```

APK 输出在 `android/app/build/outputs/apk/debug/app-debug.apk`。

## 三、已支持平台与原理

| 平台 | 解析原理 | 清晰度 |
| --- | --- | --- |
| 抖音 | 短链跳转 → 提取作品 ID → **WebView 加载官方页面，注入 JS 截获接口响应**（`aweme/post` / `aweme/detail` 等），从 JSON 提取无水印直链（`playwm` 替换为 `play`）；图集提取 `images` 数组原图直链。优先走快路径（分享页 `_ROUTER_DATA`），失败自动回退 WebView | 原平台分辨率 / 原图 |
| B 站 | 提取 BV 号 → `view` 接口拿 cid/标题 → `playurl` 接口拿直链 | 默认 720p（无需登录） |
| 快手 | 分享链接跳转到分享页 → 提取 `mainMvUrls` 直链（upic 无水印源） | 原平台分辨率 |
| X(推特) | 官方 syndication 接口（无需登录）挑最高清 mp4；vxtwitter 兜底 | 最高可用 mp4（可到 1080p） |
| 小红书 | 短链跳转 → 页面 `__SETUP_SERVER_STATE__` 提取 `masterUrl` 直链 | 原平台分辨率 |
| 微博 | 访客票据（genvisitor）+ 移动端状态接口 → 提取 `stream_url_hd` 直链 | 原平台分辨率 |

> 说明：以上平台的"去水印"原理是**直接获取平台提供的无水印源**（水印由客户端叠加，源文件本身无水印），并非 AI 画面修复。
>
> **抖音改版说明（2026-08）**：抖音分享页不再内嵌作品数据，改为前端调用带 `a_bogus` 签名接口异步获取，纯 HTTP 无法伪造其浏览器状态（`s_v_web_id` / `msToken` / `uifid`）。App 改用 WebView 方案：让页面自身完成签名与风控校验，注入 JS 截获接口响应提取直链。代价是解析耗时约 3~15 秒。

## 四、技术栈

- Kotlin + Jetpack Compose（Material 3）
- OkHttp（网络）、Coil（封面加载）、org.json（解析）
- **WebView（抖音解析）**：加载官方页面 + 注入 JS 截获接口响应，适配平台签名/风控改版
- 下载：流式写入 + Range 断点续传
- 保存：MediaStore（Android 10+）/ 公共目录（Android 8/9）

## 五、已知限制与注意事项

- 平台随时可能改版 / 加风控，解析失败属正常现象，需要持续跟进维护；
- 抖音解析走 WebView 真实浏览器方案，首次解析耗时约 3~15 秒，需保持网络通畅；若抖音风控升级导致 WebView 也被拦截，会出现"页面数据获取失败"提示；
- 抖音、小红书、微博短链有时效，过期后重新复制分享链接即可；
- 抖音图集只保存图片本身（不含背景音乐），图片暂停/继续按"张"为粒度生效；
- B 站 720p 以上清晰度需要登录 Cookie（后续版本可加）；
- Android 9 及以下保存到相册需要手动授予存储权限；
- 部分 CDN 直链是 `http://`，应用已开启明文流量支持；
- **X(推特) 及其视频 CDN 在国内网络无法直连，需要科学上网才能解析和下载**；
- 微博 `weibo.com/tv/show/1034:xxx` 格式暂不支持，请从微博 App 复制分享链接；
- 小红书图集笔记（无视频）会提示解析失败，属正常；
- 本工具仅限个人学习 / 素材收集，请遵守版权与平台条款（详见计划书第八章）。

## 六、维护记录（2026-08 抖音改版）

**现象**：粘贴抖音图文/视频分享链接，解析报"未找到视频或图片地址"。

**根因**：抖音改版分享页，作品数据不再内嵌在页面 `_ROUTER_DATA` 里，改为前端调用带 `a_bogus` 签名接口异步获取；纯 HTTP 无法伪造服务端校验的真实浏览器状态（`s_v_web_id` cookie、真实 `msToken`、安全 SDK 指纹 `uifid` 等），旧解析链路整体失效。

**排查结论**（详细过程见 `poc/debug_*.py` 与 `poc/douyin_abogus_poc.py`）：
- 旧版 a_bogus（`cus` 后缀）与新版（`dhzx` 后缀、RC4 key 211，[brock7/douyin_sign](https://github.com/brock7/douyin_sign)）签名均可生成，但服务端仍返回 200 空 body / 403 / "Url doesn't match"；
- AGW 网关接口（`slidesinfo` / `iteminfo`）同样要求签名 + 浏览器状态；
- 唯一可稳定通过的入口是**真实浏览器**：页面自带完整环境与签名，我们只截获它的接口响应。

**解决方案**：Android 端 `DouyinWebViewParser` 用 WebView 加载 `www.douyin.com/note(或video)/{id}`，注入 JS 钩子截获 `aweme/post` / `aweme/detail` / `slidesinfo` 等接口响应，提取图集原图直链 / 视频无水印直链；并保留"快路径（分享页 `_ROUTER_DATA`）→ 失败自动回退 WebView"的降级策略。验证脚本：`poc/douyin_playwright_poc.js`（Playwright + 本机 Chrome）。

**维护提示**：抖音若再次改版，优先检查 `poc/douyin_playwright_poc.js` 能否跑通；若浏览器方案也失效，通常意味着接口路径变了，更新 `DouyinWebViewParser` 里的 JS 钩子匹配正则与响应解析逻辑即可。

## 七、路线图

- [x] 抖音、B站、快手、X(推特)、小红书、微博解析
- [x] 抖音图集（图文笔记）无水印原图下载
- [x] 抖音解析适配平台改版（WebView 方案，2026-08）
- [x] 下载暂停 / 续传 / 删除
- [x] **Windows 桌面版**（Compose Desktop + jpackage EXE，2026-08）
- [x] **通用链接下载（yt-dlp）**：桌面版任意网站视频直链解析（Pornhub 等 1000+ 站点，2026-08）
- [ ] 下载队列、历史记录持久化
- [ ] 通用链接支持 m3u8 分片流（集成 ffmpeg）
- [ ] 桌面版支持抖音（JCEF 内置 Chromium 等方案评估）
- [ ] 服务端解析引擎（yt-dlp），覆盖 YouTube 等更多平台
- [ ] TikTok、西瓜视频 / 头条视频
