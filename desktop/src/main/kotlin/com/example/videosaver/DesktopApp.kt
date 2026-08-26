package com.example.videosaver

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videosaver.model.DownloadStatus
import com.example.videosaver.model.DownloadTask
import com.example.videosaver.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: DesktopViewModel) {
    var darkTheme by rememberSaveable { mutableStateOf(false) }
    MaterialTheme {
        val state by vm.state.collectAsState()
        var link by remember { mutableStateOf("") }

        DisposableEffect(Unit) {
            onDispose { vm.dispose() }
        }

        Scaffold(
            topBar = { CenterAlignedTopAppBar(title = { Text("清印 · 视频下载去水印") }) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { darkTheme = !darkTheme },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(if (darkTheme) "☀" else "🌙", fontSize = 20.sp)
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("粘贴分享链接") },
                        minLines = 2,
                        maxLines = 4,
                        placeholder = {
                            Text("支持：B站 / 快手 / X(推特) / 小红书 / 微博\n（抖音暂未支持桌面版）\n例如：https://b23.tv/xxxx 或 https://v.kuaishou.com/xxx")
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (link.isBlank()) {
                                    readClipboard()?.takeIf { it.isNotBlank() }?.let { link = it }
                                } else {
                                    link = ""
                                }
                            }
                        ) { Text(if (link.isBlank()) "粘贴" else "清空") }
                        Button(
                            onClick = { vm.parse(link) },
                            enabled = !state.parsing,
                            modifier = Modifier.weight(1f)
                        ) { Text(if (state.parsing) "解析中..." else "解析") }
                    }

                    if (state.parsing) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    state.message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }

                    state.video?.let { video ->
                        Spacer(Modifier.height(12.dp))
                        // 图集作品 videoUrl 为空，需同时比对图片列表才能匹配到对应任务
                        val task = state.tasks.firstOrNull {
                            it.video.videoUrl == video.videoUrl && it.video.imageUrls == video.imageUrls
                        }
                        VideoCard(
                            video = video,
                            task = task,
                            onDownload = { vm.startDownload() },
                            onPause = { task?.let { vm.pauseDownload(it.id) } },
                            onResume = { task?.let { vm.resumeDownload(it.id) } },
                            onDelete = { task?.let { vm.deleteDownload(it.id) } }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 底部块：下载历史
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp)
                ) {
                    Text(
                        "下载历史",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    val completed = state.tasks.filter { it.status == DownloadStatus.COMPLETED }
                    if (completed.isEmpty()) {
                        Text(
                            "还没有下载记录",
                            color = MaterialTheme.colorScheme.outline,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(completed) { task ->
                            HistoryItem(task, onDelete = { vm.deleteDownload(task.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCard(
    video: VideoInfo,
    task: DownloadTask?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row {
                CoverImage(url = video.coverUrl)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${video.platform} · ${video.author.ifBlank { "未知作者" }}" +
                            if (video.imageUrls.isNotEmpty()) " · 图集 ${video.imageUrls.size} 张" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (video.durationSec > 0) {
                        Text(
                            text = "时长 ${formatDuration(video.durationSec)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            when {
                task == null -> {
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (video.imageUrls.isNotEmpty()) {
                                "下载无水印图片（${video.imageUrls.size} 张）"
                            } else {
                                "下载无水印视频"
                            }
                        )
                    }
                }
                task.status == DownloadStatus.DOWNLOADING ||
                    task.status == DownloadStatus.PAUSED -> {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (task.status == DownloadStatus.DOWNLOADING) {
                                "下载中 ${(task.progress * 100).toInt()}%"
                            } else {
                                "已暂停 ${(task.progress * 100).toInt()}%"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (task.status == DownloadStatus.DOWNLOADING) {
                                OutlinedButton(onClick = onPause) { Text("暂停") }
                            } else {
                                Button(onClick = onResume) { Text("继续") }
                            }
                            OutlinedButton(onClick = onDelete) { Text("删除") }
                        }
                    }
                }
                task.status == DownloadStatus.COMPLETED -> {
                    Text(
                        text = if (task.video.imageUrls.isNotEmpty()) {
                            "已保存 ${task.video.imageUrls.size} 张图片到「下载\\视频去水印」"
                        } else {
                            "已保存到「下载\\视频去水印」文件夹"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text("删除这条下载记录")
                    }
                }
                task.status == DownloadStatus.FAILED -> {
                    Text(
                        text = task.error ?: "下载失败",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text("重试") }
                        OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("删除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(task: DownloadTask, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "[${task.video.platform}] ${task.video.title}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = task.video.author.ifBlank { "" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        TextButton(onClick = onDelete) {
            Text("删除", color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}

/** 读取系统剪贴板文本 */
private fun readClipboard(): String? = try {
    val clip = Toolkit.getDefaultToolkit().systemClipboard
    clip.getData(DataFlavor.stringFlavor) as? String
} catch (e: Exception) {
    null
}

/** 封面图：IO 线程加载网络图片，失败显示灰色占位块 */
@Composable
private fun CoverImage(url: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection()
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val img: BufferedImage = ImageIO.read(conn.getInputStream())
                    ?: return@withContext null
                img.toComposeImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap!!,
            contentDescription = "封面",
            modifier = modifier
                .size(96.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .size(96.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Gray.copy(alpha = 0.3f))
        )
    }
}
