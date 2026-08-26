package com.example.videosaver

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.videosaver.parser.VideoParserManager
import kotlinx.coroutines.runBlocking

/**
 * 清印 · 视频下载去水印（桌面版）入口
 *
 * 用法：
 *   - 正常启动：打开图形界面
 *   - `--cli <链接>`：无界面命令行解析验证（输出 JSON）
 */
fun main(args: Array<String>) {
    if (args.size >= 2 && args[0] == "--cli") {
        cliTest(args[1])
        return
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "清印 · 视频下载去水印（桌面版）",
            state = rememberWindowState(width = 760.dp, height = 860.dp),
        ) {
            MainScreen(vm = DesktopViewModel())
        }
    }
}

/** 无界面解析测试：用于验证解析链路 */
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
