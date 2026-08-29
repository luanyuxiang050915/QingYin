package com.example.videosaver

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.videosaver.ui.formatDuration
import com.example.videosaver.ui.formatEta
import com.example.videosaver.ui.formatSpeed
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.datatransfer.DataFlavor

/**
 * 主界面：左（输入 + 结果）/ 右（下载历史）双栏布局
 */
@Composable
fun MainContent(
    vm: DesktopViewModel,
    trayIcon: TrayIcon?,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    val link by vm.link.collectAsState()
    var historyCollapsed by remember { mutableStateOf(false) }

    // 下载完成 → 系统托盘通知
    var lastCompleted by remember { mutableStateOf(0) }
    LaunchedEffect(state.tasks) {
        val completed = state.tasks.count { it.status == DownloadStatus.COMPLETED }
        if (completed > lastCompleted) {
            lastCompleted = completed
            trayIcon?.displayMessage(
                "下载完成",
                "已保存到「下载\\视频去水印」文件夹",
                TrayIcon.MessageType.INFO,
            )
        }
    }

    Box(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxSize()) {
            // ============ 左栏：输入 + 结果 ============
            Column(
                Modifier.weight(1.06f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                InputHero(
                    link = link,
                    onLinkChange = { vm.setLink(it) },
                    parsing = state.parsing,
                    message = state.message,
                    onPaste = {
                        if (link.isBlank()) {
                            readClipboard()?.takeIf { it.isNotBlank() }?.let { vm.setLink(it) }
                        } else {
                            vm.setLink("")
                        }
                    },
                    onParse = { vm.parse(link) },
                )

                AnimatedVisibility(
                    visible = state.parsing,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(150)),
                ) {
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        SkeletonCard()
                    }
                }

                state.video?.let { video ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 },
                        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 4 },
                    ) {
                        val task = state.tasks.firstOrNull {
                            it.video.videoUrl == video.videoUrl && it.video.imageUrls == video.imageUrls
                        }
                        ResultCard(
                            video = video,
                            task = task,
                            onDownload = { vm.startDownload() },
                            onPause = { task?.let { vm.pauseDownload(it.id) } },
                            onResume = { task?.let { vm.resumeDownload(it.id) } },
                            onDelete = { task?.let { vm.deleteDownload(it.id) } },
                            onOpenFolder = { task?.let { vm.openFolder(it.id) } },
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            // ============ 右栏：下载历史 ============
            HistoryPanel(
                tasks = state.tasks,
                collapsed = historyCollapsed,
                onToggle = { historyCollapsed = !historyCollapsed },
                onDelete = { vm.deleteDownload(it) },
                onOpenFolder = { vm.openFolder(it) },
                modifier = Modifier.width(if (historyCollapsed) 44.dp else 300.dp),
            )
        }
    }
}

// ------------------------------------------------------------------
// 输入区
// ------------------------------------------------------------------

@Composable
private fun InputHero(
    link: String,
    onLinkChange: (String) -> Unit,
    parsing: Boolean,
    message: String?,
    onPaste: () -> Unit,
    onParse: () -> Unit,
) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column {
            OutlinedTextField(
                value = link,
                onValueChange = onLinkChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("粘贴分享链接") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                placeholder = {
                    Text("支持：B站 / 快手 / X / 小红书 / 微博\n以及任意网站视频链接（通用解析 1000+ 站点）\n也可直接把链接拖进窗口")
                },
            )

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onPaste,
                    modifier = Modifier.height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (link.isBlank()) "粘贴" else "清空")
                }
                GradientButton(
                    text = if (parsing) "解析中..." else "解析",
                    onClick = onParse,
                    enabled = !parsing,
                    icon = if (parsing) null else Icons.Outlined.Download,
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

            message?.let {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (it.contains("失败") || it.contains("异常")) Icons.Outlined.Warning
                        else Icons.Outlined.CheckCircle,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = if (it.contains("失败") || it.contains("异常")) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        it,
                        color = if (it.contains("失败") || it.contains("异常")) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                    )
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
    onOpenFolder: () -> Unit,
) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column {
            Row {
                CoverImage(url = video.coverUrl, modifier = Modifier.size(132.dp, 84.dp), corner = 10.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    video.imageUrls.take(6).forEach { url ->
                        CoverImage(url, Modifier.size(52.dp, 52.dp), corner = 8.dp)
                    }
                    if (video.imageUrls.size > 6) {
                        Box(
                            Modifier.size(52.dp, 52.dp).clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+${video.imageUrls.size - 6}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---- 操作区 ----
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
                        OutlinedButton(onClick = onDelete) {
                            Icon(Icons.Outlined.Delete, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("删除")
                        }
                    }
                }
                task.status == DownloadStatus.PAUSED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RingProgress(progress = task.progress)
                        Spacer(Modifier.width(14.dp))
                        Text("已暂停 ${(task.progress * 100).toInt()}%", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        GradientButton(
                            text = "继续下载",
                            onClick = onResume,
                            icon = Icons.Outlined.PlayArrow,
                            modifier = Modifier.width(140.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onDelete) {
                            Icon(Icons.Outlined.Delete, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("删除")
                        }
                    }
                }
                task.status == DownloadStatus.COMPLETED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SuccessCheck()
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("下载完成", color = Color(0xFF2ECC71), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                if (task.video.imageUrls.isNotEmpty()) "已保存 ${task.video.imageUrls.size} 张图片" else "已保存到「下载\\视频去水印」",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        OutlinedButton(onClick = onOpenFolder) {
                            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("打开文件夹")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onDelete) {
                            Icon(Icons.Outlined.Delete, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("删除")
                        }
                    }
                }
                task.status == DownloadStatus.FAILED -> {
                    Column {
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
}

// ------------------------------------------------------------------
// 下载历史面板
// ------------------------------------------------------------------

@Composable
private fun HistoryPanel(
    tasks: List<DownloadTask>,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onDelete: (Long) -> Unit,
    onOpenFolder: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier) {
        if (collapsed) {
            Box(Modifier.fillMaxSize().clickable(onClick = onToggle), contentAlignment = Alignment.TopCenter) {
                Icon(
                    Icons.Outlined.History,
                    null,
                    modifier = Modifier.padding(top = 10.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            HistoryContent(
                tasks = tasks,
                onToggle = onToggle,
                onDelete = onDelete,
                onOpenFolder = onOpenFolder,
            )
        }
    }
}

@Composable
private fun HistoryContent(
    tasks: List<DownloadTask>,
    onToggle: () -> Unit,
    onDelete: (Long) -> Unit,
    onOpenFolder: (Long) -> Unit,
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
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Text("«", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(10.dp))

        if (tasks.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Download,
                title = "还没有下载记录",
                subtitle = "解析后点「下载」即可开始",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks, key = { it.id }) { task ->
                    HistoryItem(task, onDelete = { onDelete(task.id) }, onOpenFolder = { onOpenFolder(task.id) })
                }
                if (tasks.none { it.status == DownloadStatus.COMPLETED }) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Download,
                            title = "还没有下载记录",
                            subtitle = "进行中的任务会显示在这里",
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(task: DownloadTask, onDelete: () -> Unit, onOpenFolder: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val statusColor = when (task.status) {
        DownloadStatus.COMPLETED -> Color(0xFF2ECC71)
        DownloadStatus.DOWNLOADING -> Color(0xFF4A7CFF)
        DownloadStatus.PAUSED -> Color(0xFFF5A623)
        DownloadStatus.FAILED -> Color(0xFFE5484D)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null) {
                if (task.status == DownloadStatus.COMPLETED) onOpenFolder()
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(task.video.coverUrl, Modifier.size(44.dp, 44.dp), corner = 8.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = task.video.title,
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
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))
                .background(if (hovered) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else Color.Transparent)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Delete,
                null,
                modifier = Modifier.size(14.dp),
                tint = if (hovered) Color(0xFFE5484D) else MaterialTheme.colorScheme.outline,
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
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        content()
    }
}

// ------------------------------------------------------------------
// 平台识别
// ------------------------------------------------------------------

private fun detectPlatform(text: String): String? = when {
    Regex("(bilibili\\.com|b23\\.tv)").containsMatchIn(text) -> "B站"
    Regex("(kuaishou\\.com|gifshow\\.com)").containsMatchIn(text) -> "快手"
    Regex("(x\\.com|twitter\\.com|t\\.co)").containsMatchIn(text) -> "X(推特)"
    Regex("(xiaohongshu\\.com|xhslink\\.cn)").containsMatchIn(text) -> "小红书"
    Regex("(weibo\\.com|weibo\\.cn)").containsMatchIn(text) -> "微博"
    Regex("https?://").containsMatchIn(text) -> "通用"
    else -> null
}

/** 读取系统剪贴板文本 */
private fun readClipboard(): String? = try {
    val clip = Toolkit.getDefaultToolkit().systemClipboard
    clip.getData(DataFlavor.stringFlavor) as? String
} catch (e: Exception) {
    null
}
