package com.example.videosaver.model

data class VideoInfo(
    val platform: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val videoUrl: String,
    val durationSec: Long = 0,
    /** 图集图片直链（无水印原图）；非空表示这是图文作品而非视频 */
    val imageUrls: List<String> = emptyList(),
    /** 下载时携带的 Referer（防盗链 CDN 要求页面域名，如 Pornhub 的 ev.phncdn.com 需要 pornhub 页面做来源） */
    val referer: String = "",
)
