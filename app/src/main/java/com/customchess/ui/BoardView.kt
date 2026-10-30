package com.customchess.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.customchess.core.Move
import com.customchess.core.fileOf
import com.customchess.core.rankOf
import com.customchess.ui.PieceArt.drawPiece

private const val FRAME = 0.03f

/**
 * Bàn cờ vẽ bằng Canvas: khung vàng, ô sơn mài, quân vector, kéo thả hoặc chạm hai lần.
 * [isMine] cho biết quân ở ô nào được phép kéo (đến lượt mình và đúng màu).
 */
@Composable
fun ChessBoard(
    board: IntArray,
    flipped: Boolean,
    lastMove: Move?,
    checkSquare: Int,
    selected: Int,
    targets: Set<Int>,
    plyCount: Int,
    theme: BoardTheme,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    showHints: Boolean = true,
    isMine: (Int) -> Boolean = { false },
    onSquare: (Int) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val anim = remember { Animatable(1f) }
    var prevPly by remember { mutableIntStateOf(plyCount) }
    LaunchedEffect(plyCount) {
        if (plyCount > prevPly) {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        } else anim.snapTo(1f)
        prevPly = plyCount
    }

    var dragSq by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    val onSquareState by rememberUpdatedState(onSquare)
    val isMineState by rememberUpdatedState(isMine)
    val selectedState by rememberUpdatedState(selected)

    fun squareAt(p: Offset, width: Float): Int {
        val frame = width * FRAME
        val cell = (width - 2 * frame) / 8f
        val col = ((p.x - frame) / cell).toInt()
        val row = ((p.y - frame) / cell).toInt()
        if (p.x < frame || p.y < frame || col !in 0..7 || row !in 0..7) return -1
        val f = if (flipped) 7 - col else col
        val r = if (flipped) row else 7 - row
        return r * 8 + f
    }

    val coordStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)

    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(flipped, interactive) {
                if (!interactive) return@pointerInput
                detectTapGestures { off ->
                    val sq = squareAt(off, size.width.toFloat())
                    if (sq >= 0) onSquareState(sq)
                }
            }
            .pointerInput(flipped, interactive) {
                if (!interactive) return@pointerInput
                detectDragGestures(
                    onDragStart = { off ->
                        val sq = squareAt(off, size.width.toFloat())
                        if (sq >= 0 && isMineState(sq)) {
                            dragSq = sq
                            dragPos = off
                            if (selectedState != sq) onSquareState(sq)
                        }
                    },
                    onDragEnd = {
                        if (dragSq >= 0) {
                            val target = squareAt(dragPos, size.width.toFloat())
                            if (target >= 0 && target != dragSq) onSquareState(target)
                            dragSq = -1
                        }
                    },
                    onDragCancel = { dragSq = -1 },
                    onDrag = { change, delta ->
                        if (dragSq >= 0) {
                            change.consume()
                            dragPos = dragPos + delta
                        }
                    },
                )
            },
    ) {
        val w = size.width
        val frame = w * FRAME
        val cell = (w - 2 * frame) / 8f

        fun origin(sq: Int): Offset {
            val f = fileOf(sq)
            val r = rankOf(sq)
            val col = if (flipped) 7 - f else f
            val row = if (flipped) r else 7 - r
            return Offset(frame + col * cell, frame + row * cell)
        }

        // khung vàng dát
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(theme.frame, theme.frame.copy(alpha = 0.78f), theme.frame),
                start = Offset.Zero, end = Offset(w, w),
            ),
            cornerRadius = CornerRadius(frame * 1.7f),
        )
        drawRoundRect(
            color = theme.frameEdge, topLeft = Offset(frame * 0.62f, frame * 0.62f),
            size = Size(w - frame * 1.24f, w - frame * 1.24f),
            cornerRadius = CornerRadius(frame * 0.9f), style = Stroke(width = frame * 0.16f),
        )

        // ô cờ
        for (r in 0..7) for (f in 0..7) {
            val sq = r * 8 + f
            val light = (r + f) % 2 == 1
            drawRect(if (light) theme.light else theme.dark, origin(sq), Size(cell, cell))
        }

        // nước đi cuối, ô đang chọn
        lastMove?.let {
            drawRect(Pal.Gold.copy(alpha = 0.36f), origin(it.from), Size(cell, cell))
            drawRect(Pal.Gold.copy(alpha = 0.46f), origin(it.to), Size(cell, cell))
        }
        if (selected >= 0) drawRect(Pal.Gold.copy(alpha = 0.6f), origin(selected), Size(cell, cell))
        if (checkSquare >= 0) {
            val o = origin(checkSquare)
            drawRect(
                Brush.radialGradient(
                    listOf(Pal.Alert.copy(alpha = 0.95f), Pal.Alert.copy(alpha = 0f)),
                    center = o + Offset(cell / 2, cell / 2), radius = cell * 0.75f,
                ),
                o, Size(cell, cell),
            )
        }

        // tọa độ
        for (i in 0..7) {
            val fileSq = if (flipped) 7 - i else i
            val rankSq = if (flipped) i else 7 - i
            val bottomSq = (if (flipped) 7 else 0) * 8 + fileSq
            val leftSq = rankSq * 8 + (if (flipped) 7 else 0)
            val fileLight = ((if (flipped) 7 else 0) + fileSq) % 2 == 1
            val rankLight = (rankSq + (if (flipped) 7 else 0)) % 2 == 1
            val fo = origin(bottomSq)
            val ro = origin(leftSq)
            drawText(
                measurer, "${'a' + fileSq}",
                Offset(fo.x + cell * 0.80f, fo.y + cell * 0.74f),
                coordStyle.copy(color = if (fileLight) theme.dark else theme.light),
            )
            drawText(
                measurer, "${rankSq + 1}",
                Offset(ro.x + cell * 0.07f, ro.y + cell * 0.04f),
                coordStyle.copy(color = if (rankLight) theme.dark else theme.light),
            )
        }

        // gợi ý nước đi
        if (showHints) for (t in targets) {
            val o = origin(t)
            val c = o + Offset(cell / 2, cell / 2)
            if (board[t] != 0) {
                drawCircle(Pal.Gold.copy(alpha = 0.85f), cell * 0.46f, c, style = Stroke(width = cell * 0.075f))
            } else {
                drawCircle(Color(0xFF14100E).copy(alpha = 0.34f), cell * 0.17f, c)
            }
        }

        // quân cờ
        val t = anim.value
        val movingTo = if (lastMove != null && t < 1f) lastMove.to else -1
        val pad = cell * 0.04f
        val pieceSize = cell * 0.92f
        for (sq in 0 until 64) {
            val p = board[sq]
            if (p == 0 || sq == dragSq || sq == movingTo) continue
            drawPiece(p, origin(sq) + Offset(pad, pad), pieceSize)
        }
        if (movingTo >= 0 && lastMove != null && board[movingTo] != 0) {
            val a = origin(lastMove.from)
            val b = origin(lastMove.to)
            val pos = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            drawPiece(board[movingTo], pos + Offset(pad, pad), pieceSize)
        }
        if (dragSq >= 0 && board[dragSq] != 0) {
            val big = cell * 1.16f
            drawPiece(board[dragSq], dragPos - Offset(big / 2, big * 0.62f), big)
        }
    }
}
