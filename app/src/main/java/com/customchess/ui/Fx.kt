package com.customchess.ui

import android.content.Context
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.customchess.data.Settings
import kotlin.math.min

/**
 * Trạng thái hiệu ứng đang áp dụng (đã gộp từ Cài đặt, chế độ tiết kiệm pin và tùy chọn "xóa hiệu ứng" của hệ thống).
 * Là state của Compose nên mọi chỗ đọc tự cập nhật khi người dùng đổi tùy chọn.
 */
object Fx {
    /** Công tắc tổng của mọi hiệu ứng chuyển động. */
    var anim by mutableStateOf(true)
    var glass by mutableStateOf(true)
    var glassAlpha by mutableFloatStateOf(0.6f)
    var aurora by mutableStateOf(true)
    var transitions by mutableStateOf(true)
    var slide by mutableStateOf(true)
    var slideMs by mutableIntStateOf(180)
    var burst by mutableStateOf(true)
    /** 0 = tự động. */
    var fps by mutableIntStateOf(0)
    var bgMode by mutableIntStateOf(0)
    var saver by mutableStateOf(false)

    fun apply(s: Settings, systemAnimations: Boolean) {
        val on = s.animations && systemAnimations
        val saving = s.batterySaver
        saver = saving
        anim = on
        glass = s.glass
        glassAlpha = s.glassStrength / 100f
        aurora = on && s.aurora && !saving
        transitions = on && s.transitions
        slide = on && s.slideAnim
        slideMs = if (saving) min(s.moveAnimMs, 120) else s.moveAnimMs
        burst = on && s.burst && !saving
        fps = if (saving) 30 else s.fps
        bgMode = s.bgMode
    }

    /** Người dùng có bật hoạt ảnh trong cài đặt hệ thống (Tùy chọn nhà phát triển / Hỗ trợ tiếp cận) không. */
    fun systemAnimationsOn(context: Context): Boolean = try {
        AndroidSettings.Global.getFloat(context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    } catch (_: Exception) {
        true
    }
}

/**
 * Đồng hồ cho hiệu ứng liên tục (nền Aurora...), chạy không quá [maxFps] khung hình/giây và theo giới hạn FPS trong Cài đặt.
 * Trả về số giây trôi qua; chỉ cập nhật khi [enabled].
 */
@Composable
fun rememberFxTime(enabled: Boolean, maxFps: Int = 30): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    val cap = Fx.fps
    LaunchedEffect(enabled, cap, maxFps) {
        if (!enabled) return@LaunchedEffect
        val fps = if (cap in 1..maxFps) cap else maxFps
        val gap = 1_000_000_000L / fps
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = now - last
                if (dt >= gap) {
                    t.floatValue += dt / 1_000_000_000f
                    last = now
                }
            }
        }
    }
    return t
}
