package com.customchess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

/**
 * Một bộ quân: chọn họ hình dạng ([family], xem [PieceShapes]) và cách tô màu.
 * Bộ "dynamic" lấy màu từ màu chủ đạo hiện tại của ứng dụng ([Skin.tint]).
 */
class PieceStyle(
    val id: String,
    val name: String,
    val family: Int,
    val whiteTop: Color,
    val whiteBottom: Color,
    val blackTop: Color,
    val blackBottom: Color,
    val whiteLine: Color,
    val blackLine: Color,
    val lineWidth: Float,
    val detailAlpha: Float,
    /** Độ đặc của phần tô (nhỏ hơn 1 cho kiểu trong suốt, nét vẽ). */
    val fillAlpha: Float,
    /** Vầng sáng quanh nét viền (Neon). */
    val glow: Boolean,
    /** Độ sáng của vệt bóng loáng ở góc trên bên trái. */
    val gloss: Float,
    /** Độ đậm của bóng đổ dưới chân quân. */
    val shadow: Float,
    /** Viền ngoài tương phản (kiểu nét vẽ) để quân không biến mất trên ô cùng màu. */
    val haloWhite: Color? = null,
    val haloBlack: Color? = null,
    val dynamic: Boolean = false,
) {
    private val fixedWhite: Brush by lazy { gradient(whiteTop, whiteBottom) }
    private val fixedBlack: Brush by lazy { gradient(blackTop, blackBottom) }

    private fun gradient(top: Color, bottom: Color) =
        Brush.verticalGradient(listOf(top, bottom), startY = 0f, endY = 100f)

    fun fill(white: Boolean): Brush = when {
        !dynamic -> if (white) fixedWhite else fixedBlack
        white -> gradient(Color(Skin.tint.tone(97)), Color(Skin.tint.tone(86)))
        else -> gradient(Color(Skin.tint.tone(30)), Color(Skin.tint.tone(10)))
    }

    fun line(white: Boolean): Color = when {
        !dynamic -> if (white) whiteLine else blackLine
        white -> Color(Skin.tint.tone(22))
        else -> Color(Skin.tint.tone(88))
    }

    /** Viền ngoài tương phản của bên trắng/đen, hoặc null nếu bộ quân không có. */
    fun halo(white: Boolean): Color? = if (white) haloWhite else haloBlack
}

/** Vẽ quân cờ bằng đường vector (hệ tọa độ 100x100). */
object PieceArt {
    private class Geometry(val body: Map<Int, Path>, val detail: Map<Int, Path>)

    private val geometries: List<Geometry> by lazy {
        PieceShapes.FAMILIES.map { f ->
            Geometry(
                body = f.body.mapValues { (_, d) -> PathParser().parsePathString(d).toPath() },
                detail = f.detail.mapValues { (_, d) -> PathParser().parsePathString(d).toPath() },
            )
        }
    }

    /** Vẽ một quân (piece > 0 là trắng, < 0 là đen) vào ô vuông cạnh [size] tại [topLeft]. */
    fun DrawScope.drawPiece(
        piece: Int,
        topLeft: Offset,
        size: Float,
        alpha: Float = 1f,
        style: PieceStyle = Skin.pieceStyle,
    ) {
        val type = abs(piece)
        val geo = geometries.getOrNull(style.family) ?: return
        val body = geo.body[type] ?: return
        val white = piece > 0
        val line = style.line(white)
        val lw = style.lineWidth
        val k = size / 100f
        withTransform({
            translate(topLeft.x, topLeft.y)
            scale(k, k, pivot = Offset.Zero)
        }) {
            if (style.shadow > 0f) {
                drawOval(
                    Color.Black.copy(alpha = style.shadow * alpha),
                    topLeft = Offset(20f, 85.5f), size = Size(60f, 9f),
                )
            }
            if (style.glow) {
                drawPath(body, line.copy(alpha = 0.14f * alpha), style = Stroke(width = lw * 3.6f, join = StrokeJoin.Round))
                drawPath(body, line.copy(alpha = 0.26f * alpha), style = Stroke(width = lw * 2.2f, join = StrokeJoin.Round))
            }
            style.halo(white)?.let {
                drawPath(body, it.copy(alpha = 0.6f * alpha), style = Stroke(width = lw + 3.4f, join = StrokeJoin.Round))
            }
            drawPath(body, line, alpha = alpha, style = Stroke(width = lw, join = StrokeJoin.Round))
            drawPath(body, brush = style.fill(white), alpha = alpha * style.fillAlpha)
            if (style.gloss > 0f) {
                clipPath(body) {
                    drawRect(
                        brush = Brush.linearGradient(
                            0f to Color.White.copy(alpha = style.gloss * alpha),
                            0.55f to Color.Transparent,
                            start = Offset.Zero, end = Offset(70f, 100f),
                        ),
                        size = Size(100f, 100f),
                    )
                }
            }
            geo.detail[type]?.let {
                drawPath(
                    it, line, alpha = alpha * style.detailAlpha,
                    style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

/** Một quân cờ nhỏ dùng trong danh sách, hộp thoại... */
@Composable
fun PieceIcon(piece: Int, size: Dp, modifier: Modifier = Modifier, style: PieceStyle = Skin.pieceStyle) {
    Canvas(modifier.size(size)) {
        val s = this.size
        with(PieceArt) { drawPiece(piece, Offset(s.width * 0.04f, s.height * 0.04f), s.width * 0.92f, style = style) }
    }
}
