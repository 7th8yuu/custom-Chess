package com.customchess.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Bit(val angle: Float, val speed: Float, val size: Float, val color: Int, val spin: Float)

/**
 * Pháo giấy bắn ra khi thắng ván hoặc giải đúng câu đố. Mỗi lần [trigger] tăng là bắn một đợt;
 * trigger = 0 nghĩa là chưa bắn. Tự tắt khi người dùng tắt hiệu ứng.
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    val prog = remember { Animatable(1f) }
    val bits = remember(trigger) {
        val r = Random(trigger * 7919 + 13)
        List(54) {
            Bit(
                angle = (-Math.PI.toFloat()) * (0.05f + 0.9f * r.nextFloat()),
                speed = 0.35f + 0.65f * r.nextFloat(),
                size = 0.012f + 0.014f * r.nextFloat(),
                color = r.nextInt(5),
                spin = (r.nextFloat() - 0.5f) * 900f,
            )
        }
    }
    LaunchedEffect(trigger) {
        if (trigger > 0 && Fx.burst) {
            prog.snapTo(0f)
            prog.animateTo(1f, tween(1700, easing = LinearOutSlowInEasing))
        }
    }
    if (!Fx.burst || prog.value >= 1f) return
    val colors = listOf(Pal.Gold, Pal.Tertiary, Pal.Secondary, Color.White, Pal.Jade)
    Canvas(modifier.fillMaxSize()) {
        val t = prog.value
        val w = size.width
        val h = size.height
        val ox = w / 2
        val oy = h * 0.55f
        for (b in bits) {
            val dist = b.speed * t
            val x = ox + cos(b.angle) * dist * w * 0.62f
            val y = oy + sin(b.angle) * dist * h * 0.55f + t * t * h * 0.5f
            val s = b.size * w
            rotate(b.spin * t, pivot = Offset(x, y)) {
                drawRect(colors[b.color].copy(alpha = (1f - t).coerceIn(0f, 1f)), Offset(x - s / 2, y - s / 2), Size(s, s * 0.55f))
            }
        }
    }
}
