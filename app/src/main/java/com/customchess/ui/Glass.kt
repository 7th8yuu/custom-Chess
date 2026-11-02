package com.customchess.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Kính lỏng: nền trong mờ có dải sáng chéo, vệt phản chiếu ở góc trên và viền sáng mảnh.
 * Khi tắt "Kính mờ" trong Cài đặt thì rơi về một mặt phẳng đặc cùng bảng màu.
 * [tint] pha thêm màu vào kính, [selected] vẽ viền nhấn.
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    level: Float = 1f,
    tint: Color = Color.Unspecified,
    selected: Boolean = false,
): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val dark = Skin.dark
    val on = Fx.glass
    val k = 0.45f + 0.75f * Fx.glassAlpha
    val primary = Pal.Gold
    val base = if (tint.isSpecified) tint else primary
    val panel = Pal.Panel
    val hair = Pal.Hair
    val w = size.width
    val h = size.height
    val hairPx = 1.dp.toPx()
    val rimPx = 1.2.dp.toPx()
    val selPx = 2.dp.toPx()
    onDrawBehind {
        if (!on) {
            drawOutline(outline, panel)
            if (tint.isSpecified) drawOutline(outline, base.copy(alpha = 0.10f))
            drawOutline(outline, hair, style = Stroke(hairPx))
            if (selected) drawOutline(outline, primary, style = Stroke(selPx))
        } else {
            val topA = ((if (dark) 0.15f else 0.62f) * k * level).coerceAtMost(0.95f)
            val botA = ((if (dark) 0.05f else 0.34f) * k * level).coerceAtMost(0.9f)
            drawOutline(outline, Brush.verticalGradient(listOf(Color.White.copy(alpha = topA), Color.White.copy(alpha = botA)), startY = 0f, endY = h))
            drawOutline(outline, base.copy(alpha = if (selected) 0.30f else 0.08f))
            drawOutline(
                outline,
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = (if (dark) 0.16f else 0.35f) * level), Color.Transparent),
                    center = Offset(w * 0.18f, 0f), radius = max(w, h) * 0.75f,
                ),
            )
            val rim = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = if (dark) 0.50f else 0.95f), Color.White.copy(alpha = if (dark) 0.06f else 0.20f),
                    Color.Transparent, Color.White.copy(alpha = if (dark) 0.22f else 0.55f),
                ),
                start = Offset.Zero, end = Offset(w, h),
            )
            drawOutline(outline, rim, style = Stroke(rimPx))
            if (selected) drawOutline(outline, primary, style = Stroke(selPx))
        }
    }
}

/** Nhấn xuống thì co nhẹ lại như nút kính thật (tắt khi tắt hiệu ứng). */
fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean = true, scale: Float = 0.965f): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed && Fx.anim) scale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Một khối kính có nội dung, có thể bấm. */
@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    level: Float = 1f,
    tint: Color = Color.Unspecified,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val m = if (onClick != null) modifier.pressable(onClick, enabled) else modifier
    Box(m.glass(shape, level, tint, selected).clip(shape), content = content)
}

// ------------------------------------------------------------------------------------------ nền

/** Làm mờ ảnh bằng cách thu nhỏ hai lần (nhanh, chạy được mọi đời Android). */
private fun blurBitmap(img: ImageBitmap): ImageBitmap {
    val src = img.asAndroidBitmap()
    val small = Bitmap.createScaledBitmap(src, 36, 36, true)
    val mid = Bitmap.createScaledBitmap(small, 108, 108, true)
    return mid.asImageBitmap()
}

/**
 * Nền toàn ứng dụng: ảnh bàn cờ làm mờ (hoặc dải màu/phẳng), phủ màu nền để chữ dễ đọc,
 * thêm ba quầng màu Material 3 trôi chậm (Aurora) khi bật.
 */
@Composable
fun AppBackdrop(theme: BoardTheme, modifier: Modifier = Modifier) {
    val photo = rememberBoardPhoto(theme)
    val blurred by produceState<ImageBitmap?>(null, photo) {
        value = photo?.let { p -> withContext(Dispatchers.Default) { runCatching { blurBitmap(p) }.getOrNull() } }
    }
    val time = rememberFxTime(Fx.aurora && Fx.bgMode != 2)
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val dark = Skin.dark
        drawRect(Pal.Ink)
        val img = blurred
        if (Fx.bgMode == 0 && img != null) {
            // phủ kín màn hình, cắt giữa ảnh
            val ratio = w / h
            val srcW: Int
            val srcH: Int
            if (img.width.toFloat() / img.height > ratio) { srcH = img.height; srcW = (img.height * ratio).roundToInt() }
            else { srcW = img.width; srcH = (img.width / ratio).roundToInt() }
            drawImage(
                img, srcOffset = IntOffset((img.width - srcW) / 2, (img.height - srcH) / 2), srcSize = IntSize(srcW, srcH),
                dstSize = IntSize(w.roundToInt(), h.roundToInt()), filterQuality = FilterQuality.Medium,
            )
            drawRect(Pal.Ink.copy(alpha = if (dark) 0.58f else 0.40f))
        } else if (Fx.bgMode != 2) {
            drawRect(
                Brush.verticalGradient(
                    listOf(Pal.PrimaryContainer.copy(alpha = if (dark) 0.55f else 0.65f), Pal.Ink, Pal.TertiaryContainer.copy(alpha = if (dark) 0.35f else 0.45f)),
                ),
            )
        }
        if (Fx.bgMode != 2) {
            val t = time.value
            val a = if (dark) 0.34f else 0.30f
            val r = w * 0.95f
            fun blob(c: Color, cx: Float, cy: Float) =
                drawRect(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center = Offset(cx, cy), radius = r))
            blob(Pal.Gold, w * (0.20f + 0.25f * sin(t * 0.13f)), h * (0.16f + 0.12f * cos(t * 0.11f)))
            blob(Pal.Tertiary, w * (0.85f + 0.12f * cos(t * 0.09f + 1f)), h * (0.48f + 0.18f * sin(t * 0.07f)))
            blob(Pal.Secondary, w * (0.30f + 0.20f * sin(t * 0.10f + 2f)), h * (0.88f + 0.08f * cos(t * 0.12f)))
        }
        // làm tối nhẹ mép dưới để thanh điều hướng nổi lên
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Pal.Ink.copy(alpha = if (dark) 0.55f else 0.35f)), startY = h * 0.7f, endY = h),
        )
    }
}
