package com.example.videosaver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Minimize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.videosaver.parser.VideoParserManager
import com.example.videosaver.ui.QingYinTheme
import kotlinx.coroutines.runBlocking
import java.awt.Component
import java.awt.Dimension
import java.awt.MenuItem
import java.awt.Point
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.image.BufferedImage

/**
 * 清印 · 视频下载去水印（桌面版）入口
 * 无边框圆角窗口 + 系统托盘 + 拖拽链接 + 快捷键
 */
fun main(args: Array<String>) {
    if (args.size >= 2 && args[0] == "--cli") {
        cliTest(args[1])
        return
    }

    application {
        val vm = remember { DesktopViewModel() }
        val windowState = rememberWindowState(width = 1120.dp, height = 720.dp)
        val exitApp = { exitApplication() }

        Window(
            onCloseRequest = { /* 由自定义关闭按钮处理 → 最小化到托盘 */ },
            title = "清印 · 视频下载去水印",
            state = windowState,
            undecorated = true,
            transparent = true,
        ) {
            val window = this.window
            val trayIcon = remember { createTrayIcon() }
            var darkTheme by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(900, 640)
                setupDropTarget(window) { text ->
                    if (!text.isNullOrBlank()) vm.parse(text.trim())
                }
                // 无边框窗口拖拽（标题栏区域 y < 42）
                var dragOffset: Point? = null
                window.addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        if (e.point.y < 42) dragOffset = e.point
                    }
                    override fun mouseReleased(e: MouseEvent) {
                        dragOffset = null
                    }
                })
                window.addMouseMotionListener(object : MouseMotionAdapter() {
                    override fun mouseDragged(e: MouseEvent) {
                        val off = dragOffset ?: return
                        window.location = Point(e.xOnScreen - off.x, e.yOnScreen - off.y)
                    }
                })
                trayIcon?.let { t ->
                    val show = {
                        window.isVisible = true
                        window.toFront()
                        window.requestFocus()
                    }
                    t.addActionListener { show() }
                    t.popupMenu?.getItem(0)?.addActionListener { show() }
                    t.popupMenu?.getItem(1)?.addActionListener { exitApp() }
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    trayIcon?.let { runCatching { SystemTray.getSystemTray().remove(it) } }
                    vm.dispose()
                }
            }

            QingYinTheme(darkTheme = darkTheme) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    WindowChrome(
                        darkTheme = darkTheme,
                        onToggleTheme = { darkTheme = !darkTheme },
                        onMinimize = { window.setExtendedState(java.awt.Frame.ICONIFIED) },
                        onCloseToTray = { window.isVisible = false },
                    )
                    MainContent(
                        vm = vm,
                        trayIcon = trayIcon,
                        modifier = Modifier
                            .padding(top = 42.dp)
                            .onPreviewKeyEvent { event ->
                                // Ctrl+Enter 触发解析
                                if (event.key == Key.Enter && event.isCtrlPressed) {
                                    vm.parse(vm.currentLink())
                                    true
                                } else {
                                    false
                                }
                            },
                    )
                }
            }
        }
    }
}

/** 自定义标题栏：拖拽区 + 主题/最小化/关闭 */
@Composable
private fun FrameWindowScope.WindowChrome(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onMinimize: () -> Unit,
    onCloseToTray: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).height(42.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "清印",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
            )
            Text(
                " · 视频下载去水印",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        ChromeButton(icon = Icons.Outlined.LightMode, onClick = onToggleTheme, shown = darkTheme)
        ChromeButton(icon = Icons.Outlined.DarkMode, onClick = onToggleTheme, shown = !darkTheme)
        Spacer(Modifier.width(4.dp))
        ChromeButton(icon = Icons.Outlined.Minimize, onClick = onMinimize)
        ChromeButton(icon = Icons.Outlined.Close, onClick = onCloseToTray, danger = true)
    }
}

@Composable
private fun ChromeButton(
    icon: ImageVector,
    onClick: () -> Unit,
    shown: Boolean = true,
    danger: Boolean = false,
) {
    if (!shown) return
    Box(
        modifier = Modifier
            .padding(2.dp)
            .size(28.dp)
            .clip(CircleShape)
            .background(if (danger) Color(0x1A_E5484D) else Color(0x14_8A90A5))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (danger) Color(0xFFE5484D) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ------------------------------------------------------------------
// 系统托盘
// ------------------------------------------------------------------

private fun createTrayIcon(): TrayIcon? {
    if (!SystemTray.isSupported()) return null
    return try {
        val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = java.awt.Color(0xFF4A7CFF.toInt())
        g.fillOval(2, 2, 28, 28)
        g.color = java.awt.Color.WHITE
        g.fillPolygon(intArrayOf(12, 12, 23), intArrayOf(10, 22, 16), 3)
        g.dispose()
        val icon = TrayIcon(image, "清印 · 视频下载去水印")
        icon.isImageAutoSize = true
        val menu = PopupMenu()
        menu.add(MenuItem("打开主界面"))
        menu.add(MenuItem("退出"))
        icon.popupMenu = menu
        SystemTray.getSystemTray().add(icon)
        icon
    } catch (e: Exception) {
        null
    }
}

// ------------------------------------------------------------------
// 拖拽链接进窗口
// ------------------------------------------------------------------

private fun setupDropTarget(window: Component, onText: (String?) -> Unit) {
    DropTarget(
        window,
        object : DropTargetAdapter() {
            override fun drop(e: DropTargetDropEvent) {
                try {
                    e.acceptDrop(DnDConstants.ACTION_COPY)
                    val text = e.transferable.getTransferData(DataFlavor.stringFlavor) as? String
                    e.dropComplete(true)
                    onText(text)
                } catch (ex: Exception) {
                    runCatching { e.rejectDrop() }
                }
            }
        },
    )
}

// ------------------------------------------------------------------
// 无界面解析测试
// ------------------------------------------------------------------

private fun cliTest(text: String) {
    runBlocking {
        try {
            val video = VideoParserManager.parse(text)
            println(
                "解析成功：\n" +
                    "  平台: ${video.platform}\n" +
                    "  标题: ${video.title}\n" +
                    "  作者: ${video.author}\n" +
                    "  封面: ${video.coverUrl.take(120)}\n" +
                    "  类型: " + if (video.imageUrls.isNotEmpty()) "图集 ${video.imageUrls.size} 张" else "视频\n  直链: ${video.videoUrl.take(160)}"
            )
            if (video.imageUrls.isNotEmpty()) {
                video.imageUrls.take(5).forEach { println("  图: ${it.take(140)}") }
            }
        } catch (e: Exception) {
            println("解析失败: ${e.message}")
        }
    }
}
