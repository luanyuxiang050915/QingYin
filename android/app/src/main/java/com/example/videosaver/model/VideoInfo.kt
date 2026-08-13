package com.example.videosaver.model

data class VideoInfo(
    val platform: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val videoUrl: String,
    val durationSec: Long = 0,
)
