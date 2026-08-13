package com.example.videosaver.save

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MediaSaver(private val context: Context) {

    /**
     * Android 10+ 用 MediaStore 保存（无需存储权限）；
     * Android 8/9 写入公共 Movies 目录后触发媒体扫描。
     */
    suspend fun saveToGallery(file: File, mime: String = "video/mp4"): Uri =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Video.Media.MIME_TYPE, mime)
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/视频去水印"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("无法创建媒体条目")
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: throw IOException("无法写入媒体库")
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "视频去水印"
                )
                if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建保存目录")
                val dest = File(dir, file.name)
                file.copyTo(dest, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime), null)
                Uri.fromFile(dest)
            }
        }

    /** 删除已保存到相册的视频（Android 10+ 走 MediaStore；Android 8/9 直接删文件） */
    fun deleteSaved(uri: Uri?): Boolean {
        if (uri == null) return false
        return try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: return false)
                f.delete()
            } else {
                context.contentResolver.delete(uri, null, null) > 0
            }
        } catch (e: Exception) {
            false
        }
    }
}
