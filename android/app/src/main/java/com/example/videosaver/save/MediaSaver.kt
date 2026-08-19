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
     * Android 8/9 写入公共目录后触发媒体扫描。
     * 按 mime 类型分流：视频存 Movies，图片存 Pictures，统一放在「视频去水印」目录。
     */
    suspend fun saveToGallery(file: File, mime: String = "video/mp4"): Uri =
        withContext(Dispatchers.IO) {
            val isImage = mime.startsWith("image/")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        (if (isImage) Environment.DIRECTORY_PICTURES
                        else Environment.DIRECTORY_MOVIES) + "/视频去水印"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = if (isImage) {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(collection, values)
                    ?: throw IOException("无法创建媒体条目")
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: throw IOException("无法写入媒体库")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(
                        if (isImage) Environment.DIRECTORY_PICTURES
                        else Environment.DIRECTORY_MOVIES
                    ),
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
