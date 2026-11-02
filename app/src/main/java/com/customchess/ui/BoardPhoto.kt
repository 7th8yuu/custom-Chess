package com.customchess.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val photoCache = HashMap<String, ImageBitmap>()

/**
 * Ảnh nền của bàn cờ ảnh (có sẵn hoặc do người dùng thêm), giải mã ngoài luồng giao diện và giữ lại để dùng chung.
 * Trả về null khi bàn cờ không dùng ảnh hoặc ảnh chưa giải mã xong (lúc đó bàn cờ vẽ tạm một màu nền).
 */
@Composable
fun rememberBoardPhoto(theme: BoardTheme): ImageBitmap? {
    if (!theme.isPhoto) return null
    val resources = LocalContext.current.applicationContext.resources
    val key = theme.key
    val state = produceState<ImageBitmap?>(initialValue = photoCache[key], key) {
        value = photoCache[key] ?: withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bmp = if (theme.photo != 0) BitmapFactory.decodeResource(resources, theme.photo, opts)
            else theme.photoFile?.let { BitmapFactory.decodeFile(it, opts) }
            bmp?.asImageBitmap()
        }?.also { photoCache[key] = it }
    }
    return state.value
}

/** Quên ảnh đã nạp (khi xóa hoặc thay ảnh). */
fun forgetBoardPhoto(keyPrefix: String) {
    photoCache.keys.filter { it.startsWith(keyPrefix) }.forEach { photoCache.remove(it) }
}
