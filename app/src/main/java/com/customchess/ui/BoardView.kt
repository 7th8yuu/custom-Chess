package com.customchess.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.customchess.core.Move
import com.customchess.core.fileOf
import com.customchess.core.rankOf
import com.customchess.ui.PieceArt.drawPiece
import kotlin.math.roundToInt

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
    /** Nước vừa rồi có ăn quân không: nổ tia sáng ở ô đến. */
    capture: Boolean = false,
    highlightLast: Boolean = true,
    /** Mũi tên chỉ một nước đi (gợi ý, nước tốt nhất khi xem lại). */
    arrow: Move? = null,
    /** Ô cần nhấp nháy (gợi ý câu đố). */
    hintSquare: Int = -1,
    /** Nét vẽ của người dùng (mũi tên, ô tô màu); null = không cho vẽ. */
    marks: BoardMarks? = null,
    /** Nước đi trước (premove) đã đặt: tô xanh hai ô và vẽ mũi tên. */
    premove: Move? = null,
) {
    val gestureMarks = marks?.takeIf { !it.readOnly }
    val measurer = rememberTextMeasurer()
    val photo = rememberBoardPhoto(theme)
    val anim = remember { Animatable(1f) }
    val pop = remember { Animatable(1f) }
    var prevPly by remember { mutableIntStateOf(plyCount) }
    LaunchedEffect(plyCount) {
        gestureMarks?.clear() // đi nước mới (hoặc tua ván) thì xóa nét vẽ, như chess.com
        if (plyCount > prevPly && Fx.slide) {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(Fx.slideMs, easing = FastOutSlowInEasing))
        } else anim.snapTo(1f)
        if (plyCount > prevPly && capture && Fx.burst) {
            pop.snapTo(0f)
            pop.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        } else pop.snapTo(1f)
        prevPly = plyCount
    }
    val pulse = rememberFxTime(hintSquare >= 0 && Fx.anim, maxFps = 30)

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

    val drawing = gestureMarks?.drawMode == true
    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(flipped, interactive, gestureMarks, drawing) {
                if (!interactive && gestureMarks == null) return@pointerInput // bàn cờ chỉ để xem: không chặn cử chỉ của thẻ chứa nó
                if (drawing) {
                    // chế độ vẽ: chạm một ô để tô màu
                    detectTapGestures { off ->
                        val sq = squareAt(off, size.width.toFloat())
                        if (sq >= 0) gestureMarks?.square(sq)
                    }
                    return@pointerInput
                }
                detectTapGestures { off ->
                    gestureMarks?.clear() // chạm thường thì xóa nét vẽ
                    val sq = squareAt(off, size.width.toFloat())
                    if (interactive && sq >= 0) onSquareState(sq)
                }
            }
            .pointerInput(flipped, interactive, drawing) {
                if (!interactive || drawing) return@pointerInput
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
            }
            // vẽ mũi tên: ở chế độ vẽ thì kéo là vẽ, còn không thì giữ ngón tay rồi kéo (đặt cuối để nhận cử chỉ trước)
            .pointerInput(flipped, gestureMarks, drawing) {
                val mk = gestureMarks ?: return@pointerInput
                var pos = Offset.Zero
                val onStart: (Offset) -> Unit = { off ->
                    pos = off
                    val sq = squareAt(off, size.width.toFloat())
                    mk.dragFrom = sq
                    mk.dragTo = sq
                }
                val onMove: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit = { change, delta ->
                    change.consume()
                    pos += delta
                    if (mk.dragFrom >= 0) mk.dragTo = squareAt(pos, size.width.toFloat())
                }
                if (drawing) {
                    detectDragGestures(onDragStart = onStart, onDrag = onMove, onDragEnd = { mk.endDrag() }, onDragCancel = { mk.cancelDrag() })
                } else {
                    detectDragGesturesAfterLongPress(onDragStart = onStart, onDrag = onMove, onDragEnd = { mk.endDrag() }, onDragCancel = { mk.cancelDrag() })
                }
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

        // ô cờ: bàn cờ ảnh vẽ ảnh rồi phủ màu mờ lên từng ô, bàn cờ thường tô màu đặc
        if (theme.isPhoto) {
            val boardPx = (cell * 8f).roundToInt()
            val edge = frame.roundToInt()
            if (photo != null) {
                drawImage(
                    photo, srcOffset = IntOffset.Zero, srcSize = IntSize(photo.width, photo.height),
                    dstOffset = IntOffset(edge, edge), dstSize = IntSize(boardPx, boardPx),
                    filterQuality = FilterQuality.Medium,
                )
            } else {
                // ảnh đang giải mã: tạm một màu nền cùng tông
                drawRect(theme.frameEdge, Offset(frame, frame), Size(cell * 8f, cell * 8f))
            }
        }
        for (r in 0..7) for (f in 0..7) {
            val sq = r * 8 + f
            val light = (r + f) % 2 == 1
            drawRect(if (light) theme.light else theme.dark, origin(sq), Size(cell, cell))
        }

        // nước đi cuối, ô đang chọn
        if (highlightLast) lastMove?.let {
            drawRect(theme.highlight.copy(alpha = 0.36f), origin(it.from), Size(cell, cell))
            drawRect(theme.highlight.copy(alpha = 0.46f), origin(it.to), Size(cell, cell))
        }
        if (selected >= 0) drawRect(theme.highlight.copy(alpha = 0.6f), origin(selected), Size(cell, cell))
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

        // nước đi trước (premove): tô hai ô dưới quân cờ
        if (premove != null) {
            val c = Color(0xFF3B82F6).copy(alpha = 0.38f)
            drawRect(c, origin(premove.from), Size(cell, cell))
            drawRect(c, origin(premove.to), Size(cell, cell))
        }

        // ô người dùng tô màu (nằm dưới quân cờ)
        if (marks != null) {
            val st = marks.style
            fun markSquare(sq: Int, col: Color) {
                if (st.squareRing) {
                    val ring = cell * 0.07f
                    drawCircle(col, radius = cell / 2 - ring / 2, center = origin(sq) + Offset(cell / 2, cell / 2), style = Stroke(ring))
                } else {
                    drawRect(col, origin(sq), Size(cell, cell))
                }
            }
            for (s in marks.squares) markSquare(s.sq, marks.colorOf(s.color).copy(alpha = st.squareOpacity))
            if (marks.dragFrom >= 0 && marks.dragFrom == marks.dragTo) markSquare(marks.dragFrom, marks.colorOf(marks.color).copy(alpha = st.squareOpacity * 0.7f))
        }

        // tọa độ (màu chữ lấy bản đặc của màu ô đối diện để luôn đọc được, kể cả khi màu ô là lớp phủ mờ)
        if (Skin.showCoords) for (i in 0..7) {
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
                coordStyle.copy(color = (if (fileLight) theme.dark else theme.light).copy(alpha = 1f)),
            )
            drawText(
                measurer, "${rankSq + 1}",
                Offset(ro.x + cell * 0.07f, ro.y + cell * 0.04f),
                coordStyle.copy(color = (if (rankLight) theme.dark else theme.light).copy(alpha = 1f)),
            )
        }

        // gợi ý nước đi
        if (showHints) for (t in targets) {
            val o = origin(t)
            val c = o + Offset(cell / 2, cell / 2)
            if (board[t] != 0) {
                drawCircle(theme.highlight.copy(alpha = 0.9f), cell * 0.46f, c, style = Stroke(width = cell * 0.075f))
            } else {
                // chấm tối trên ô sáng, chấm sáng trên ô tối
                val lightSquare = (rankOf(t) + fileOf(t)) % 2 == 1
                drawCircle(if (lightSquare) Color(0xFF14100E).copy(alpha = 0.34f) else Color.White.copy(alpha = 0.42f), cell * 0.17f, c)
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
        // tia sáng khi ăn quân
        if (capture && lastMove != null && pop.value < 1f) {
            val c = origin(lastMove.to) + Offset(cell / 2, cell / 2)
            val pr = pop.value
            for (k in 0 until 12) {
                val ang = k * (2.0 * Math.PI / 12.0)
                val r = cell * (0.18f + 0.62f * pr)
                val dot = cell * 0.075f * (1f - pr)
                drawCircle(
                    (if (k % 2 == 0) theme.highlight else Pal.Tertiary).copy(alpha = (1f - pr) * 0.95f), dot,
                    c + Offset((kotlin.math.cos(ang) * r).toFloat(), (kotlin.math.sin(ang) * r).toFloat()),
                )
            }
            drawCircle(theme.highlight.copy(alpha = (1f - pr) * 0.55f), cell * (0.25f + 0.55f * pr), c, style = Stroke(width = cell * 0.05f * (1f - pr)))
        }

        // ô gợi ý nhấp nháy
        if (hintSquare >= 0) {
            val c = origin(hintSquare) + Offset(cell / 2, cell / 2)
            val a = 0.55f + 0.4f * kotlin.math.sin(pulse.value * 5f)
            drawCircle(theme.highlight.copy(alpha = a.coerceIn(0.2f, 1f)), cell * 0.5f, c, style = Stroke(width = cell * 0.09f))
        }

        // mũi tên: nước gợi ý của engine rồi các mũi tên người dùng vẽ (và nét đang kéo dở)
        fun drawArrow(fromSq: Int, toSq: Int, col: Color, widthK: Float = 0.17f) {
            val a = origin(fromSq) + Offset(cell / 2, cell / 2)
            val b = origin(toSq) + Offset(cell / 2, cell / 2)
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            if (len > 1f) {
                val ux = dx / len
                val uy = dy / len
                val head = cell * 0.42f
                val tail = a + Offset(ux * cell * 0.2f, uy * cell * 0.2f)
                val neck = b - Offset(ux * head * 0.8f, uy * head * 0.8f)
                drawLine(col, tail, neck, strokeWidth = cell * widthK, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                val px = -uy
                val py = ux
                val tip = b - Offset(ux * cell * 0.08f, uy * cell * 0.08f)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(neck.x + px * head * 0.55f, neck.y + py * head * 0.55f)
                    lineTo(neck.x - px * head * 0.55f, neck.y - py * head * 0.55f)
                    close()
                }
                drawPath(path, col)
            }
        }
        if (arrow != null) drawArrow(arrow.from, arrow.to, Pal.Tertiary.copy(alpha = 0.82f))
        if (premove != null) drawArrow(premove.from, premove.to, Color(0xFF3B82F6).copy(alpha = 0.8f))
        if (marks != null) {
            val st = marks.style
            for (a in marks.arrows) drawArrow(a.from, a.to, marks.colorOf(a.color).copy(alpha = st.arrowOpacity), st.arrowWidth)
            if (marks.dragFrom >= 0 && marks.dragTo >= 0 && marks.dragFrom != marks.dragTo) {
                drawArrow(marks.dragFrom, marks.dragTo, marks.colorOf(marks.color).copy(alpha = st.arrowOpacity * 0.65f), st.arrowWidth)
            }
        }

        if (dragSq >= 0 && board[dragSq] != 0) {
            val big = cell * 1.16f
            drawPiece(board[dragSq], dragPos - Offset(big / 2, big * 0.62f), big)
        }
    }
}
