package com.example.videosaver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.videosaver.model.DownloadStatus
import com.example.videosaver.model.DownloadTask
import com.example.videosaver.model.VideoInfo
import com.example.videosaver.ui.theme.VideoSaverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var link by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startDownload()
        else Toast.makeText(context, "需要存储权限才能保存视频", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { CenterAlignedTopAppBar(title = { Text("清印") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onToggleTheme,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(
                    text = if (darkTheme) "☀" else "🌙",
                    fontSize = 20.sp,
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 顶部块：粘贴链接 + 两个按钮
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
                    label = { Text("粘贴抖音 / B站 分享链接") },
                    minLines = 2,
                    maxLines = 4,
                    placeholder = {
                        Text("例如：\n0.53 复制打开抖音... https://v.douyin.com/xxxxx/ ...")
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
                                clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let {
                                    link = it
                                }
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
                    val task = state.tasks.firstOrNull { it.video.videoUrl == video.videoUrl }
                    VideoCard(
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
                AsyncImage(
                    model = video.coverUrl,
                    contentDescription = "封面",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
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
                        text = "${video.platform} · ${video.author.ifBlank { "未知作者" }}",
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
                        Text("下载无水印视频")
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
                        text = "已保存到相册「视频去水印」",
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
