# 清印 QingYin · 视频下载去水印（Android）

粘贴分享链接，一键下载无水印视频。支持 **抖音 / B站 / 快手 / X(推特) / 小红书 / 微博** 六个平台。

## 功能特性

- 粘贴 / 清空输入框（有内容时按钮自动变成"清空"）
- 解析展示：封面、标题、作者、时长
- 下载无水印视频，带实时进度
- **暂停 / 继续（断点续传）**：抖音、B站、快手、X、小红书、微博直链均已验证支持 Range
- **删除**：删除未完成的下载，或删除已保存的视频与历史记录
- 下载历史列表
- 保存到相册「视频去水印」文件夹（Android 10+ 免存储权限）

## 目录结构

- [计划书.md](计划书.md) — 立项评估与方案
- `poc/` — Python 验证脚本（不需要安卓环境，先跑通解析链路）
- `android/` — Android Studio 工程（Kotlin + Jetpack Compose）

## 一、POC 快速验证（无需安卓环境）

```powershell
python poc/bilibili_poc.py "https://www.bilibili.com/video/BV1GJ411x7h7"
python poc/douyin_poc.py "0.53 复制打开抖音... https://v.douyin.com/xxxxx/ ..."
python poc/kuaishou_poc.py "https://www.kuaishou.com/short-video/3xtuvnw4dpeuq79"
python poc/x_poc.py "https://x.com/RafaelNadal/status/1844308861492318594"
python poc/xhs_poc.py "BladeSage Q5... http://xhslink.cn/o/soAvHtZJcw"
python poc/weibo_poc.py "https://m.weibo.cn/status/5280806310516853"
```

脚本输出 JSON：标题、作者、封面、无水印视频直链。六个平台的解析链路均已在 2026-08 实测通过。

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
| 抖音 | 分享短链跳转 → 分享页 `_ROUTER_DATA` 提取 `play_addr` → 播放地址 `playwm` 替换为 `play` | 原平台分辨率 |
| B 站 | 提取 BV 号 → `view` 接口拿 cid/标题 → `playurl` 接口拿直链 | 默认 720p（无需登录） |
| 快手 | 分享链接跳转到分享页 → 提取 `mainMvUrls` 直链（upic 无水印源） | 原平台分辨率 |
| X(推特) | 官方 syndication 接口（无需登录）挑最高清 mp4；vxtwitter 兜底 | 最高可用 mp4（可到 1080p） |
| 小红书 | 短链跳转 → 页面 `__SETUP_SERVER_STATE__` 提取 `masterUrl` 直链 | 原平台分辨率 |
| 微博 | 访客票据（genvisitor）+ 移动端状态接口 → 提取 `stream_url_hd` 直链 | 原平台分辨率 |

> 说明：以上平台的"去水印"原理是**直接获取平台提供的无水印源**（水印由客户端叠加，源文件本身无水印），并非 AI 画面修复。

## 四、技术栈

- Kotlin + Jetpack Compose（Material 3）
- OkHttp（网络）、Coil（封面加载）、org.json（解析）
- 下载：流式写入 + Range 断点续传
- 保存：MediaStore（Android 10+）/ 公共目录（Android 8/9）

## 五、已知限制与注意事项

- 平台随时可能改版 / 加风控，解析失败属正常现象，需要持续跟进维护；
- 抖音、小红书、微博短链有时效，过期后重新复制分享链接即可；
- B 站 720p 以上清晰度需要登录 Cookie（后续版本可加）；
- Android 9 及以下保存到相册需要手动授予存储权限；
- 部分 CDN 直链是 `http://`，应用已开启明文流量支持；
- **X(推特) 及其视频 CDN 在国内网络无法直连，需要科学上网才能解析和下载**；
- 微博 `weibo.com/tv/show/1034:xxx` 格式暂不支持，请从微博 App 复制分享链接；
- 小红书图集笔记（无视频）会提示解析失败，属正常；
- 本工具仅限个人学习 / 素材收集，请遵守版权与平台条款（详见计划书第八章）。

## 六、路线图

- [x] 抖音、B站、快手、X(推特)、小红书、微博解析
- [x] 下载暂停 / 续传 / 删除
- [ ] 下载队列、历史记录持久化
- [ ] 服务端解析引擎（yt-dlp），覆盖 YouTube 等更多平台
- [ ] TikTok、西瓜视频 / 头条视频
