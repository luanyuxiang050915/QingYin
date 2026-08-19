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
)
