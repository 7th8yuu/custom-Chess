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

private val photoCache = HashMap<Int, ImageBitmap>()

/**
 * Ảnh nền của bàn cờ ảnh, giải mã ngoài luồng giao diện và giữ lại để các bàn cờ khác dùng chung.
 * Trả về null khi [res] = 0 hoặc ảnh chưa giải mã xong (lúc đó bàn cờ vẽ tạm một màu nền).
 */
@Composable
fun rememberBoardPhoto(res: Int): ImageBitmap? {
    if (res == 0) return null
    val resources = LocalContext.current.applicationContext.resources
    val state = produceState<ImageBitmap?>(initialValue = photoCache[res], res) {
        value = photoCache[res] ?: withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            BitmapFactory.decodeResource(resources, res, opts)?.asImageBitmap()
        }?.also { photoCache[res] = it }
    }
    return state.value
}
