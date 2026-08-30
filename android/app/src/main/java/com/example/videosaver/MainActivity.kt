package com.example.videosaver

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.videosaver.model.DownloadStatus
import com.example.videosaver.model.DownloadTask
import com.example.videosaver.model.VideoInfo
import com.example.videosaver.ui.Chip
import com.example.videosaver.ui.CoverImage
import com.example.videosaver.ui.EmptyState
import com.example.videosaver.ui.GradientButton
import com.example.videosaver.ui.PlatformBadge
import com.example.videosaver.ui.RingProgress
import com.example.videosaver.ui.SkeletonCard
import com.example.videosaver.ui.SuccessCheck
import com.example.videosaver.ui.formatEta
import com.example.videosaver.ui.formatSpeed
import com.example.videosaver.ui.theme.VideoSaverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var darkTheme by rememberSaveable { mutableStateOf(false) }
            VideoSaverTheme(darkTheme = darkTheme) {
                MainScreen(
                    darkTheme = darkTheme,
                    onToggleTheme = { darkTheme = !darkTheme },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    vm: MainViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var link by rememberSaveable { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startDownload()
        else Toast.makeText(context, "需要存储权限才能保存视频", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "清印",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            " · 视频下载去水印",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = "切换主题",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // ============ 输入区 ============
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("粘贴分享链接") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        placeholder = {
                            Text("支持：抖音（视频/图集）/ B站 / 快手 / X / 小红书 / 微博\n例如：0.53 复制打开抖音... https://v.douyin.com/xxxxx/ ...")
                        }
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (link.isBlank()) {
                                    clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let {
                                        link = it
                                    }
                                } else {
                                    link = ""
                                }
                            },
                            modifier = Modifier.height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (link.isBlank()) "粘贴" else "清空")
                        }
                        GradientButton(
                            text = if (state.parsing) "解析中..." else "解析",
                            onClick = { vm.parse(link) },
                            enabled = !state.parsing,
                            icon = if (state.parsing) null else Icons.Outlined.Download,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // 平台识别徽章
                    detectPlatform(link)?.let { platform ->
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("识别到", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                            Spacer(Modifier.width(8.dp))
                            PlatformBadge(platform)
                        }
                    }

                    state.message?.let {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isError = it.contains("失败") || it.contains("异常")
                            Icon(
                                if (isError) Icons.Outlined.Warning else Icons.Outlined.CheckCircle,
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                it,
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            // 解析中：骨架屏
            AnimatedVisibility(
                visible = state.parsing,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
            ) {
                SurfaceCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    SkeletonCard()
                }
            }

            // ============ 结果卡片 ============
            state.video?.let { video ->
                Spacer(Modifier.height(12.dp))
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 },
                    exit = fadeOut(tween(150)),
                ) {
                    val task = state.tasks.firstOrNull {
                        it.video.videoUrl == video.videoUrl && it.video.imageUrls == video.imageUrls
                    }
                    ResultCard(
                        video = video,
                        task = task,
                        onDownload = {
                            val needPermission =
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) != PackageManager.PERMISSION_GRANTED
                            if (needPermission) {
                                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                vm.startDownload()
                            }
                        },
                        onPause = { task?.let { vm.pauseDownload(it.id) } },
                        onResume = { task?.let { vm.resumeDownload(it.id) } },
                        onDelete = { task?.let { vm.deleteDownload(it.id) } },
                    )
                }
            }

            // ============ 下载历史 ============
            Spacer(Modifier.height(12.dp))
            SurfaceCard(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.History,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("下载历史", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (state.tasks.isNotEmpty()) {
                            Text(
                                "${state.tasks.size} 个任务",
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    if (state.tasks.isEmpty()) {
                        EmptyState(
                            icon = Icons.Outlined.Download,
                            title = "还没有下载记录",
                            subtitle = "解析后点「下载」即可开始",
                            modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(state.tasks, key = { it.id }) { task ->
                                HistoryItem(task, onDelete = { vm.deleteDownload(task.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// 结果卡片
// ------------------------------------------------------------------

@Composable
private fun ResultCard(
    video: VideoInfo,
    task: DownloadTask?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column {
            Row {
                CoverImage(url = video.coverUrl, modifier = Modifier.size(120.dp, 76.dp), corner = 10.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PlatformBadge(video.platform)
                        if (video.author.isNotBlank()) {
                            Text(video.author, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        if (video.durationSec > 0) {
                            Chip(formatDuration(video.durationSec))
                        }
                        if (video.imageUrls.isNotEmpty()) {
                            Chip("图集 ${video.imageUrls.size} 张", tint = Color(0xFF9C6CFF))
                        }
                    }
                }
            }

            // 图集缩略图
            if (video.imageUrls.isNotEmpty() && task?.status != DownloadStatus.COMPLETED) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    video.imageUrls.take(5).forEach { url ->
                        CoverImage(url, Modifier.size(48.dp, 48.dp), corner = 8.dp)
                    }
                    if (video.imageUrls.size > 5) {
                        Box(
                            Modifier.size(48.dp, 48.dp).clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+${video.imageUrls.size - 5}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            when {
                task == null -> {
                    GradientButton(
                        text = if (video.imageUrls.isNotEmpty()) "下载无水印图片（${video.imageUrls.size} 张）" else "下载无水印视频",
                        onClick = onDownload,
                        icon = Icons.Outlined.Download,
                    )
                }
                task.status == DownloadStatus.DOWNLOADING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RingProgress(progress = task.progress)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "下载中 ${(task.progress * 100).toInt()}%",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                listOfNotNull(
                                    formatSpeed(task.speedBps),
                                    formatEta(task.etaSec),
                                    "${task.bytesDownloaded / 1024 / 1024}MB / ${task.totalBytes / 1024 / 1024}MB",
                                ).joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(onClick = onPause) {
                            Icon(Icons.Outlined.Pause, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("暂停")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("删除下载")
                    }
                }
                task.status == DownloadStatus.PAUSED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RingProgress(progress = task.progress)
                        Spacer(Modifier.width(14.dp))
                        Text("已暂停 ${(task.progress * 100).toInt()}%", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        GradientButton(
                            text = "继续",
                            onClick = onResume,
                            icon = Icons.Outlined.PlayArrow,
                            modifier = Modifier.width(130.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("删除下载")
                    }
                }
                task.status == DownloadStatus.COMPLETED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SuccessCheck()
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("下载完成", color = Color(0xFF2ECC71), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                if (task.video.imageUrls.isNotEmpty()) "已保存 ${task.video.imageUrls.size} 张图片到相册「视频去水印」"
                                else "已保存到相册「视频去水印」",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("删除这条下载记录")
                    }
                }
                task.status == DownloadStatus.FAILED -> {
                    Text(
                        task.error ?: "下载失败",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradientButton(
                            text = "重试",
                            onClick = onResume,
                            icon = Icons.Outlined.Refresh,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = onDelete, modifier = Modifier.weight(0.6f)) {
                            Icon(Icons.Outlined.Delete, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// 历史条目
// ------------------------------------------------------------------

@Composable
private fun HistoryItem(task: DownloadTask, onDelete: () -> Unit) {
    val statusColor = when (task.status) {
        DownloadStatus.COMPLETED -> Color(0xFF2ECC71)
        DownloadStatus.DOWNLOADING -> Color(0xFF4A7CFF)
        DownloadStatus.PAUSED -> Color(0xFFF5A623)
        DownloadStatus.FAILED -> Color(0xFFE5484D)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(task.video.coverUrl, Modifier.size(42.dp, 42.dp), corner = 8.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "[${task.video.platform}] ${task.video.title}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(statusColor))
                Spacer(Modifier.width(5.dp))
                Text(
                    when (task.status) {
                        DownloadStatus.COMPLETED -> "已完成"
                        DownloadStatus.DOWNLOADING -> "下载中 ${(task.progress * 100).toInt()}%"
                        DownloadStatus.PAUSED -> "已暂停 ${(task.progress * 100).toInt()}%"
                        DownloadStatus.FAILED -> "失败"
                    },
                    color = statusColor,
                    fontSize = 10.sp,
                )
            }
        }
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Delete,
                null,
                modifier = Modifier.size(15.dp),
                tint = Color(0xFFE5484D),
            )
        }
    }
}

// ------------------------------------------------------------------
// 通用卡片
// ------------------------------------------------------------------

@Composable
private fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        content()
    }
}

// ------------------------------------------------------------------
// 工具
// ------------------------------------------------------------------

private fun detectPlatform(text: String): String? = when {
    Regex("(bilibili\\.com|b23\\.tv)").containsMatchIn(text) -> "B站"
    Regex("(kuaishou\\.com|gifshow\\.com)").containsMatchIn(text) -> "快手"
    Regex("(x\\.com|twitter\\.com|t\\.co)").containsMatchIn(text) -> "X(推特)"
    Regex("(xiaohongshu\\.com|xhslink\\.cn)").containsMatchIn(text) -> "小红书"
    Regex("(weibo\\.com|weibo\\.cn)").containsMatchIn(text) -> "微博"
    Regex("(douyin\\.com|iesdouyin\\.com)").containsMatchIn(text) -> "抖音"
    else -> null
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
