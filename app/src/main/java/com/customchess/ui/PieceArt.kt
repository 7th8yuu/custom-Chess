package com.customchess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import com.customchess.core.Pc
import kotlin.math.abs

/**
 * Bộ quân vẽ bằng đường vector (hệ tọa độ 100x100), thiết kế riêng cho ứng dụng.
 * Quân trắng màu vỏ trứng viền nâu, quân đen màu sơn mài viền vàng.
 */
object PieceArt {
    private const val BASE = "M24 78H76V86Q76 89 73 89H27Q24 89 24 86Z"

    private val BODY = mapOf(
        Pc.PAWN to "M38.5 31a11.5 11.5 0 1 0 23 0a11.5 11.5 0 1 0 -23 0ZM36 43H64V50H36ZM41 49C41 62 34 66 29 79H71C66 66 59 62 59 49Z",
        Pc.ROOK to "M28 17H39V25H45V17H55V25H61V17H72V39H28ZM33 38H67L64 72H36ZM27 71H73V78H27Z",
        Pc.KNIGHT to "M27 89C27 74 31 62 41 52C35 53 30 57 25 62L19 55C25 42 36 29 48 24L49 12L57 20C75 22 82 38 79 58C77 70 75 80 75 89Z",
        Pc.BISHOP to "M50 11C64 22 68 33 63 43C61 47 58 49 57 52H43C42 49 39 47 37 43C32 33 36 22 50 11ZM45.5 9a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0ZM37 52H63V58H37ZM42 57C42 67 36 71 31 79H69C64 71 58 67 58 57Z",
        Pc.QUEEN to "M22 26L33 54L37 30L50 52L63 30L67 54L78 26L73 68H27ZM17.5 22a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0ZM31.5 14a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0ZM45.5 10a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0ZM59.5 14a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0ZM73.5 22a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0ZM30 66H70V77H30Z",
        Pc.KING to "M46.5 6H53.5V13H60V19.5H53.5V26H46.5V19.5H40V13H46.5ZM34 44C34 33 42 27 50 27C58 27 66 33 66 44C66 50 62 53 60 56H40C38 53 34 50 34 44ZM36 55H64V61H36ZM40 60C40 68 33 72 29 79H71C67 72 60 68 60 60Z",
    )

    private val DETAIL = mapOf(
        Pc.ROOK to "M33 38H67",
        Pc.KNIGHT to "M46 36.5a2.3 2.3 0 1 0 4.6 0a2.3 2.3 0 1 0 -4.6 0ZM60 30C68 36 70 48 66 58",
        Pc.BISHOP to "M45 34L55 25",
        Pc.QUEEN to "M30 66H70",
        Pc.KING to "M38 44H62",
    )

    private val bodyPaths: Map<Int, Path> by lazy {
        BODY.mapValues { (_, d) -> PathParser().parsePathString(d + BASE).toPath() }
    }
    private val detailPaths: Map<Int, Path> by lazy {
        DETAIL.mapValues { (_, d) -> PathParser().parsePathString(d).toPath() }
    }

    private val whiteFill = Brush.verticalGradient(listOf(Color(0xFFFFF9EA), Color(0xFFD9C7A0)), startY = 0f, endY = 100f)
    private val blackFill = Brush.verticalGradient(listOf(Color(0xFF4B3B36), Color(0xFF150E0C)), startY = 0f, endY = 100f)
    private val whiteLine = Color(0xFF2B1D17)
    private val blackLine = Color(0xFFE3C98A)

    /** Vẽ một quân (piece > 0 là trắng, < 0 là đen) vào ô vuông cạnh [size] tại [topLeft]. */
    fun DrawScope.drawPiece(piece: Int, topLeft: Offset, size: Float, alpha: Float = 1f) {
        val type = abs(piece)
        val body = bodyPaths[type] ?: return
        val white = piece > 0
        val line = if (white) whiteLine else blackLine
        val k = size / 100f
        withTransform({
            translate(topLeft.x, topLeft.y)
            scale(k, k, pivot = Offset.Zero)
        }) {
            drawPath(body, line, alpha = alpha, style = Stroke(width = 4.8f, join = StrokeJoin.Round))
            drawPath(body, brush = if (white) whiteFill else blackFill, alpha = alpha)
            detailPaths[type]?.let {
                drawPath(it, line, alpha = alpha * 0.75f, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Một quân cờ nhỏ dùng trong danh sách, hộp thoại... */
@Composable
fun PieceIcon(piece: Int, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        with(PieceArt) { drawPiece(piece, Offset(this@Canvas.size.width * 0.04f, this@Canvas.size.height * 0.04f), this@Canvas.size.width * 0.92f) }
    }
}
