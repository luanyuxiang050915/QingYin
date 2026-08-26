package com.example.videosaver.save

import java.io.File

/** 桌面版保存：写入「下载\视频去水印」文件夹 */
class DesktopSaver {

    /** 把文件复制到「下载\视频去水印」，返回保存后的文件 */
    fun saveToDownloads(file: File): File {
        val downloads = File(System.getProperty("user.home"), "Downloads")
        val dir = File(downloads, "视频去水印").apply { mkdirs() }
        val dest = File(dir, file.name)
        file.copyTo(dest, overwrite = true)
        return dest
    }

    /** 删除文件/目录（不存在返回 false） */
    fun deleteFile(file: File?): Boolean {
        if (file == null || !file.exists()) return false
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }
}
