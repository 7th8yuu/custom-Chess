package com.customchess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customchess.core.Engine
import com.customchess.data.SavedGame
import com.customchess.data.MarkStyle
import com.customchess.vm.AppViewModel
import com.customchess.core.MoveClass
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun dateText(ms: Long): String = SimpleDateFormat("dd/MM HH:mm", Lang.locale).format(Date(ms))

private fun classColor(c: MoveClass): Color = when (c) {
    MoveClass.BRILLIANT -> Color(0xFF26C2A3)
    MoveClass.GREAT -> Color(0xFF5B9BD5)
    MoveClass.BEST -> Color(0xFF4CAF7D)
    MoveClass.GOOD -> Color(0xFF9AA5A0)
    MoveClass.INACCURACY -> Color(0xFFE5B84B)
    MoveClass.MISTAKE -> Color(0xFFE8803A)
    MoveClass.BLUNDER -> Color(0xFFE5484D)
}

/** Một dòng ván trong lịch sử: kết quả, đối thủ, chế độ, ngày. */
@Composable
fun GameRow(g: SavedGame, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    val outcome = g.outcomeForMe()
    val (label, color) = when (outcome) {
        1 -> "Thắng" to Pal.Jade
        0 -> "Hòa" to Pal.EggDim
        -1 -> "Thua" to Pal.Alert
        else -> (if (g.winnerWhite == null) "Hòa" else if (g.winnerWhite == true) "Trắng thắng" else "Đen thắng") to Pal.Gold
    }
    val opponent = when (g.myWhite) {
        true -> g.black
        false -> g.white
        null -> "${g.white} - ${g.black}"
    }
    GlassBox(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(label, style = Type.Small.copy(color = color, fontSize = 13.sp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(opponent, style = Type.Heading.copy(fontSize = 16.sp), maxLines = 1)
                Text("${g.mode} · ${dateText(g.time)} · ${(g.moves.size + 1) / 2} nước", style = Type.Small, maxLines = 1)
            }
            if (onDelete != null) GlassIconButton(Icons.Rounded.Delete, "Xóa ván", onDelete, size = 38.dp, tint = Pal.EggDim)
        }
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel) {
    var deleteId by remember { mutableStateOf<String?>(null) }
    var showPgn by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Lịch sử ván", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            GhostButton("Dán PGN để xem lại ván từ nơi khác", { showPgn = true }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            if (vm.history.isEmpty()) {
                Spacer(Modifier.height(18.dp))
                InfoCard("Chưa có ván nào. Chơi xong một ván (ít nhất vài nước) là ván được lưu ở đây để xem lại và phân tích. Ván từ chess.com hay Lichess cũng xem lại được bằng cách dán PGN.")
            } else {
                Text("Chạm vào một ván để xem lại, chạy phân tích và xem nước nào là sai lầm.", style = Type.Small)
                Spacer(Modifier.height(12.dp))
                for (g in vm.history) {
                    GameRow(g, { vm.openReview(g) }, { deleteId = g.id })
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showPgn) PgnImportDialog(vm) { showPgn = false }
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            containerColor = Pal.Panel,
            title = { Text("Xóa ván này?", style = Type.Title) },
            text = { Text("Ván sẽ bị xóa khỏi máy.", style = Type.Body) },
            confirmButton = { TextButton(onClick = { vm.deleteGame(id); deleteId = null }) { Text("Xóa", style = Type.Button.copy(color = Pal.Alert)) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Hủy", style = Type.Button.copy(color = Pal.EggDim)) } },
        )
    }
}

/** Hộp dán PGN: đọc ván, lưu vào lịch sử rồi mở màn xem lại. */
@Composable
private fun PgnImportDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var mySide by remember { mutableStateOf<Boolean?>(null) } // true = trắng, false = đen, null = không rõ
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Panel,
        title = { Text("Xem lại ván từ PGN", style = Type.Title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Trên chess.com: mở ván, bấm Chia sẻ, chọn tab PGN rồi sao chép. Trên Lichess: bấm nút PGN dưới ván. " +
                        "Ván được lưu vào lịch sử và phân tích ngay trên máy, miễn phí.",
                    style = Type.Small,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = null },
                    modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 8,
                    placeholder = { Text("1. e4 e5 2. Nf3 Nc6 ...", style = Type.Small) },
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                GhostButton("Dán từ bộ nhớ tạm", { clipboard.getText()?.text?.let { text = it; error = null } })
                Spacer(Modifier.height(10.dp))
                Text("Bạn cầm quân", style = Type.Small)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Trắng", mySide == true, { mySide = true })
                    Pill("Đen", mySide == false, { mySide = false })
                    Pill("Không rõ", mySide == null, { mySide = null })
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    ErrorText(error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = vm.importPgn(text, mySide)
                if (err == null) onDismiss() else error = err
            }, enabled = text.isNotBlank()) { Text("Xem lại", style = Type.Button.copy(color = Pal.Gold)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy", style = Type.Button.copy(color = Pal.EggDim)) } },
    )
}

// ---------------------------------------------------------------------------------- xem lại và phân tích

private fun evalText(cp: Int?): String {
    if (cp == null) return "--"
    if (abs(cp) > Engine.MATE - 200) return if (cp > 0) "Chiếu hết Trắng" else "Chiếu hết Đen"
    val v = cp / 100.0
    return (if (v > 0) "+" else "") + String.format(Locale.US, "%.1f", v)
}

@Composable
fun ReviewScreen(vm: AppViewModel, theme: BoardTheme) {
    val r = vm.review
    if (r == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val g = r.game
    // 0 = chưa chạy / đã dừng, 1 = phân tích nhanh, 2 = phân tích kỹ (sâu hơn, lâu hơn)
    var mode by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var showBest by remember { mutableStateOf(true) }
    val marks = remember { BoardMarks() }
    val markStyle = remember(vm.settings.markStyle) { MarkStyle.parseOrDefault(vm.settings.markStyle) }
    SideEffect { marks.style = markStyle }
    LaunchedEffect(mode) {
        when (mode) {
            1 -> r.analyze(depth = 4, msPerPosition = 400)
            2 -> r.analyze(depth = 6, msPerPosition = 1200)
        }
        if (mode != 0 && r.done) mode = 0
    }
    LaunchedEffect(playing) {
        while (playing) {
            delay(900)
            if (r.index >= r.last) { playing = false } else r.goto(r.index + 1)
        }
    }

    val index = r.index
    val pos = r.positions[index]
    val last = if (index > 0) r.moves[index - 1] else null
    val check = if (pos.inCheck()) pos.kingSquare(pos.whiteToMove) else -1
    val captured = last != null && index > 0 && r.positions[index - 1].isCapture(last)
    val cls = if (index > 0) r.classify(index - 1) else null
    val bestHere = if (showBest && r.evals.getOrNull(index) != null) r.bestMoveAt(index) else null

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
        ScreenHeader("Xem lại ván", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val flipped = g.myWhite == false
            ReviewPlayer(if (flipped) g.white else g.black, if (flipped) r.accuracy(true) else r.accuracy(false), white = flipped)
            ChessBoard(
                board = pos.board, flipped = flipped, lastMove = last, checkSquare = check, selected = -1, targets = emptySet(),
                plyCount = index, theme = theme, modifier = Modifier.fillMaxWidth(), interactive = false,
                capture = captured, highlightLast = vm.settings.highlightLast, arrow = bestHere,
                marks = if (vm.settings.drawing) marks else null,
            )
            ReviewPlayer(if (flipped) g.black else g.white, if (flipped) r.accuracy(false) else r.accuracy(true), white = !flipped)

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.SkipPrevious, "Về đầu ván", { playing = false; r.goto(0) }, enabled = index > 0)
                GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Lùi một nước", { playing = false; r.goto(index - 1) }, enabled = index > 0)
                GlassIconButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Dừng" else "Tự chạy", {
                    if (!playing && index >= r.last) r.goto(0)
                    playing = !playing
                }, size = 52.dp)
                GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Tiến một nước", { playing = false; r.goto(index + 1) }, enabled = index < r.last)
                GlassIconButton(Icons.Rounded.SkipNext, "Tới cuối ván", { playing = false; r.goto(r.last) }, enabled = index < r.last)
            }
            if (vm.settings.drawing) {
                Spacer(Modifier.height(10.dp))
                DrawBar(marks)
            }

            // trạng thái nước đang xem
            Spacer(Modifier.height(10.dp))
            GlassCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (index == 0) "Thế ban đầu" else "${(index + 1) / 2}${if (index % 2 == 1) "." else "..."} ${r.sans[index - 1]}",
                            style = Type.Heading,
                        )
                        if (cls != null) Text(
                            "${cls.label} ${cls.mark}".trim(),
                            style = Type.Small.copy(color = classColor(cls), fontSize = 14.sp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Điểm engine", style = Type.Small)
                        Text(evalText(r.evals.getOrNull(index)), style = Type.Title.copy(fontSize = 20.sp, color = Pal.Gold))
                    }
                }
            }

            // biểu đồ điểm
            if (r.evals.any { it != null }) {
                Spacer(Modifier.height(10.dp))
                EvalGraph(r)
            }

            // phân tích
            Spacer(Modifier.height(10.dp))
            GlassCard(padding = 14.dp) {
                if (r.done) {
                    Text("Kết quả phân tích", style = Type.Heading)
                    Spacer(Modifier.height(8.dp))
                    for (c in MoveClass.entries) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(classColor(c)))
                            Text(c.label, style = Type.Body.copy(fontSize = 14.sp), modifier = Modifier.weight(1f).padding(start = 10.dp))
                            Text("${r.count(true, c)}", style = Type.Body.copy(fontSize = 14.sp), modifier = Modifier.width(44.dp))
                            Text("${r.count(false, c)}", style = Type.Body.copy(fontSize = 14.sp), modifier = Modifier.width(44.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Spacer(Modifier.weight(1f))
                        Text("Trắng", style = Type.Small, modifier = Modifier.width(44.dp))
                        Text("Đen", style = Type.Small, modifier = Modifier.width(44.dp))
                    }
                    if (r.depthUsed < 6) {
                        Spacer(Modifier.height(10.dp))
                        GhostButton("Phân tích kỹ hơn", { r.reset(); mode = 2 })
                    }
                } else if (r.analyzing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Pal.Gold, strokeWidth = 2.dp)
                        Text(
                            if (r.phase == 2) "Đang tìm nước rất hay và nước xuất sắc..." else "Đang phân tích ${r.analyzed}/${r.positions.size} thế...",
                            style = Type.Body, modifier = Modifier.padding(start = 12.dp).weight(1f),
                        )
                        GhostButton("Dừng", { mode = 0 })
                    }
                } else {
                    Text(
                        "Engine nội bộ chấm từng thế để tìm nước xuất sắc (!!), nước rất hay (!), nước sai lầm và tính độ chính xác của mỗi bên. " +
                            "Bản nhanh mất khoảng nửa phút, bản kỹ sâu hơn nhưng lâu hơn nhiều. Engine chạy trên điện thoại nên chỉ mang tính tham khảo.",
                        style = Type.Small,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (r.analyzed > 0) {
                        PrimaryButton("Phân tích tiếp", { mode = if (r.depthUsed >= 6) 2 else 1 })
                    } else {
                        PrimaryButton("Phân tích nhanh", { mode = 1 })
                        Spacer(Modifier.height(8.dp))
                        GhostButton("Phân tích kỹ", { mode = 2 })
                    }
                }
            }
            SettingSwitch("Mũi tên nước tốt nhất", "Sau khi phân tích, hiện nước engine thấy tốt nhất ở thế đang xem", showBest) { showBest = it }

            // danh sách nước đi
            Text("Các nước đi", style = Type.Heading.copy(color = Pal.EggDim, fontSize = 14.sp), modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            val rows = (r.sans.size + 1) / 2
            for (i in 0 until rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", style = Type.Small, modifier = Modifier.width(34.dp))
                    for (k in 0..1) {
                        val m = 2 * i + k
                        if (m < r.sans.size) {
                            val c = r.classify(m)
                            val sel = index == m + 1
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) Pal.Gold.copy(alpha = 0.25f) else Color.Transparent)
                                    .pressable({ playing = false; r.goto(m + 1) }, scale = 0.97f)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                            ) {
                                Text(
                                    r.sans[m] + (c?.mark?.let { if (it.isEmpty() || it == "★") "" else " $it" } ?: ""),
                                    style = Type.Body.copy(fontSize = 15.sp, color = if (c != null && c != MoveClass.BEST && c != MoveClass.GOOD) classColor(c) else Pal.Egg),
                                )
                            }
                        } else Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ReviewPlayer(name: String, accuracy: Int?, white: Boolean) {
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(if (white) Color(0xFFF2E8D2) else Color(0xFF14100E)))
        Text(name.ifEmpty { if (white) "Trắng" else "Đen" }, style = Type.Heading.copy(fontSize = 16.sp), modifier = Modifier.padding(start = 10.dp).weight(1f), maxLines = 1)
        if (accuracy != null) Pill2("Chính xác $accuracy%")
    }
}

@Composable
private fun EvalGraph(r: com.customchess.vm.ReviewState) {
    val line = Pal.Gold
    val back = Pal.Egg.copy(alpha = 0.10f)
    val mark = Pal.Tertiary
    GlassBox(Modifier.fillMaxWidth().height(96.dp), RoundedCornerShape(22.dp)) {
        Canvas(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp).pointerInput(r) {
                detectTapGestures { off -> r.goto(((off.x / size.width) * r.last).roundToInt()) }
            },
        ) {
            val w = size.width
            val h = size.height
            val n = r.positions.size
            if (n < 2) return@Canvas
            drawLine(back, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 1.dp.toPx())
            val path = Path()
            var prev = 0
            var started = false
            for (i in 0 until n) {
                val e = r.evals[i]
                if (e != null) prev = e
                val v = (prev.coerceIn(-800, 800)) / 800f
                val x = w * i / (n - 1)
                val y = h / 2 - v * h / 2 * 0.92f
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            drawPath(path, line, style = Stroke(width = 2.2.dp.toPx()))
            val cx = w * r.index / (n - 1)
            drawLine(mark, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.6.dp.toPx())
        }
    }
}
