package com.customchess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customchess.core.GameResult
import com.customchess.core.Pc
import com.customchess.core.Reason
import com.customchess.vm.AppViewModel
import com.customchess.vm.Mode
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

private val START_COUNTS = mapOf(Pc.PAWN to 8, Pc.KNIGHT to 2, Pc.BISHOP to 2, Pc.ROOK to 2, Pc.QUEEN to 1)
private val PIECE_VALUE = mapOf(Pc.PAWN to 1, Pc.KNIGHT to 3, Pc.BISHOP to 3, Pc.ROOK to 5, Pc.QUEEN to 9)

/** Quân của màu [white] đã bị bắt (suy từ số quân còn thiếu so với đầu ván). */
private fun missingPieces(board: IntArray, white: Boolean): List<Int> {
    val have = IntArray(7)
    for (p in board) if (p != 0 && (p > 0) == white) have[abs(p)]++
    val out = ArrayList<Int>()
    for (t in listOf(Pc.QUEEN, Pc.ROOK, Pc.BISHOP, Pc.KNIGHT, Pc.PAWN)) {
        repeat(max(0, (START_COUNTS[t] ?: 0) - have[t])) { out.add(if (white) t else -t) }
    }
    return out
}

private fun material(board: IntArray, white: Boolean): Int {
    var s = 0
    for (p in board) if (p != 0 && (p > 0) == white) s += PIECE_VALUE[abs(p)] ?: 0
    return s
}

fun formatClock(ms: Long): String {
    if (ms < 0) return "--:--"
    if (ms < 20_000L) {
        val tenths = (ms / 100) % 10
        return "${ms / 1000}.$tenths"
    }
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

private fun resultTitle(r: GameResult, myWhite: Boolean?): String = when {
    r.winnerWhite == null -> "Hòa"
    myWhite == null -> if (r.winnerWhite == true) "Trắng thắng" else "Đen thắng"
    r.winnerWhite == myWhite -> "Bạn thắng"
    else -> "Bạn thua"
}

private fun reasonText(r: GameResult): String = when (r.reason) {
    Reason.CHECKMATE -> "Chiếu hết"
    Reason.STALEMATE -> "Hết nước đi nhưng không bị chiếu"
    Reason.INSUFFICIENT_MATERIAL -> "Không còn đủ quân để chiếu hết"
    Reason.FIFTY_MOVE -> "50 nước không bắt quân hay đi tốt"
    Reason.REPETITION -> "Lặp lại thế cờ 3 lần"
    Reason.AGREEMENT -> "Hai bên đồng ý hòa"
    Reason.RESIGNATION -> "Một bên đầu hàng"
    Reason.TIMEOUT -> "Hết giờ"
    Reason.ABANDONED -> "Ván bị hủy"
}

private fun modeTitle(m: Mode) = when (m) {
    Mode.ENGINE -> "Chơi với máy"
    Mode.API -> "Chơi với AI"
    Mode.LOCAL -> "Cùng một máy"
    Mode.LINK -> "Chơi với bạn bè"
    Mode.LICHESS -> "Đấu online"
}

// ---------------------------------------------------------------------------------- thành phần

@Composable
private fun ClockChip(ms: () -> Long, active: Boolean) {
    val v = ms()
    val low = v in 0..19_999
    val bg = when {
        active && low -> Pal.Alert
        active -> Pal.Gold
        else -> Pal.PanelHi
    }
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text2(formatClock(v), Type.Clock.copy(color = if (active) Pal.Ink else Pal.Egg))
    }
}

@Composable
private fun PlayerBar(
    name: String,
    sub: String,
    whiteSide: Boolean,
    active: Boolean,
    timed: Boolean,
    clock: () -> Long,
    captured: List<Int>,
    lead: Int,
    thinking: Boolean,
) {
    Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(12.dp).clip(CircleShape)
                .background(if (whiteSide) Pal.Egg else Pal.Ink)
                .border(BorderStroke(1.5.dp, if (active) Pal.Gold else Pal.EggDim), CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text2(
                    name.ifEmpty { if (whiteSide) "Trắng" else "Đen" },
                    Type.Heading.copy(fontSize = 17.sp, color = if (active) Pal.Gold else Pal.Egg),
                    Modifier.weight(1f, fill = false),
                )
                if (thinking) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.size(14.dp), color = Pal.Gold, strokeWidth = 2.dp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (p in captured) PieceIcon(p, 15.dp)
                if (lead > 0) Text2("+$lead", Type.Small.copy(color = Pal.Egg), Modifier.padding(start = 4.dp))
                if (sub.isNotEmpty()) {
                    if (captured.isNotEmpty() || lead > 0) Spacer(Modifier.width(8.dp))
                    Text2(sub, Type.Small, Modifier.weight(1f, fill = false))
                }
            }
        }
        if (timed) ClockChip(clock, active)
    }
}

@Composable
private fun MoveStrip(sans: List<String>) {
    val state = rememberLazyListState()
    val rows = (sans.size + 1) / 2
    LaunchedEffect(sans.size) { if (rows > 0) state.animateScrollToItem(rows - 1) }
    Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.CenterStart) {
        if (sans.isEmpty()) {
            Text2("Chưa có nước đi", Type.Small)
        } else {
            LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                items(rows) { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text2("${i + 1}.", Type.Small)
                        Spacer(Modifier.width(4.dp))
                        val w = 2 * i
                        Text2(sans[w], Type.Body.copy(color = if (w == sans.lastIndex) Pal.Gold else Pal.Egg))
                        if (w + 1 < sans.size) {
                            Spacer(Modifier.width(6.dp))
                            Text2(sans[w + 1], Type.Body.copy(color = if (w + 1 == sans.lastIndex) Pal.Gold else Pal.Egg))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    Box(
        modifier
            .height(46.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, if (danger) Pal.Alert.copy(alpha = 0.6f) else Pal.Hair), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text2(text, Type.Button.copy(fontSize = 13.sp, color = if (danger) Pal.Alert else Pal.Egg))
    }
}

// ---------------------------------------------------------------------------------- màn hình ván cờ

@Composable
fun GameScreen(vm: AppViewModel, theme: BoardTheme) {
    val ui = vm.ui
    val haptic = LocalHapticFeedback.current
    var leaveAsk by remember { mutableStateOf(false) }
    var resultDismissed by remember(ui.result) { mutableStateOf(false) }
    var resignAsk by remember { mutableStateOf(false) }

    BackHandler {
        if (ui.result == null && ui.plyCount > 0 && !ui.disconnected) leaveAsk = true else vm.leaveGame()
    }
    LaunchedEffect(ui.moveTick) {
        if (ui.moveTick > 0 && vm.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(ui.notice) {
        if (ui.notice != null) {
            delay(3800)
            vm.clearNotice()
        }
    }

    val topWhite = ui.flipped
    val mineAt: (Int) -> Boolean = { sq ->
        val p = ui.board[sq]
        p != 0 && (p > 0) == ui.whiteToMove && (ui.myWhite == null || ui.myWhite == ui.whiteToMove) && !ui.thinking
    }
    val gameOver = ui.result != null

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
    ) {
        ScreenHeader(modeTitle(ui.mode), onBack = {
            if (ui.result == null && ui.plyCount > 0 && !ui.disconnected) leaveAsk = true else vm.leaveGame()
        })
        PlayerSide(vm, topWhite)
        Spacer(Modifier.height(4.dp))
        ChessBoard(
            board = ui.board, flipped = ui.flipped, lastMove = ui.lastMove, checkSquare = ui.checkSquare,
            selected = ui.selected, targets = ui.targets, plyCount = ui.plyCount, theme = theme,
            modifier = Modifier.fillMaxWidth(),
            interactive = !gameOver && !ui.disconnected && ui.promotion == null,
            showHints = vm.settings.showHints,
            isMine = mineAt,
            onSquare = vm::onSquareTap,
        )
        Spacer(Modifier.height(4.dp))
        PlayerSide(vm, !topWhite)

        // dòng trạng thái
        val statusText = when {
            ui.result != null -> "${resultTitle(ui.result, ui.myWhite)}. ${reasonText(ui.result)}"
            ui.disconnected -> "Mất kết nối. Bạn có thể thoát ván."
            ui.thinking -> "${if (ui.whiteToMove) ui.whiteName else ui.blackName} đang nghĩ..."
            ui.checkSquare >= 0 -> "Đang bị chiếu"
            ui.myWhite == null -> if (ui.whiteToMove) "Đến lượt Trắng" else "Đến lượt Đen"
            ui.myWhite == ui.whiteToMove -> "Đến lượt bạn"
            else -> "Chờ ${if (ui.whiteToMove) ui.whiteName else ui.blackName}..."
        }
        Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.CenterStart) {
            val notice = ui.notice
            if (notice != null) Text2(notice, Type.Body.copy(color = Pal.Gold), Modifier.fillMaxWidth())
            else Text2(
                statusText,
                Type.Body.copy(color = if (ui.checkSquare >= 0 && !gameOver) Pal.Alert else Pal.EggDim),
                Modifier.fillMaxWidth(),
            )
        }
        MoveStrip(ui.sans)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton("Lật bàn", vm::flipBoard, Modifier.weight(1f))
            ControlButton("Đi lại", vm::undo, Modifier.weight(1f), enabled = ui.canUndo)
            ControlButton("Xin hòa", vm::offerDraw, Modifier.weight(1f), enabled = !gameOver && !ui.disconnected && !ui.drawSent && ui.plyCount > 0)
            ControlButton("Đầu hàng", { resignAsk = true }, Modifier.weight(1f), enabled = !gameOver && !ui.disconnected, danger = true)
        }
        Spacer(Modifier.height(20.dp))
    }

    // ---- hộp thoại ----
    if (ui.promotion != null) {
        AlertDialog(
            onDismissRequest = vm::cancelPromotion,
            containerColor = Pal.Panel,
            title = { Text2("Phong cấp thành", Type.Title) },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (t in listOf(Pc.QUEEN, Pc.ROOK, Pc.BISHOP, Pc.KNIGHT)) {
                        Box(
                            Modifier.size(62.dp).clip(RoundedCornerShape(12.dp)).background(Pal.PanelHi)
                                .clickable { vm.choosePromotion(t) },
                            contentAlignment = Alignment.Center,
                        ) { PieceIcon(if (ui.whiteToMove) t else -t, 52.dp) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::cancelPromotion) { Text2("Hủy", Type.Button.copy(color = Pal.EggDim)) } },
        )
    }

    if (ui.drawOffered && !gameOver) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Pal.Panel,
            title = { Text2(if (ui.mode == Mode.LOCAL) "Hai bên đồng ý hòa?" else "Đối thủ xin hòa", Type.Title) },
            text = { Text2(if (ui.mode == Mode.LOCAL) "Người chơi còn lại có chấp nhận hòa không?" else "Bạn có đồng ý hòa ván này không?", Type.Body) },
            confirmButton = { TextButton(onClick = { vm.answerDraw(true) }) { Text2("Đồng ý", Type.Button.copy(color = Pal.Gold)) } },
            dismissButton = { TextButton(onClick = { vm.answerDraw(false) }) { Text2("Từ chối", Type.Button.copy(color = Pal.EggDim)) } },
        )
    }

    if (resignAsk) {
        AlertDialog(
            onDismissRequest = { resignAsk = false },
            containerColor = Pal.Panel,
            title = { Text2("Đầu hàng?", Type.Title) },
            text = { Text2("Ván này sẽ tính là thua.", Type.Body) },
            confirmButton = { TextButton(onClick = { resignAsk = false; vm.resign() }) { Text2("Đầu hàng", Type.Button.copy(color = Pal.Alert)) } },
            dismissButton = { TextButton(onClick = { resignAsk = false }) { Text2("Chơi tiếp", Type.Button.copy(color = Pal.EggDim)) } },
        )
    }

    if (leaveAsk) {
        AlertDialog(
            onDismissRequest = { leaveAsk = false },
            containerColor = Pal.Panel,
            title = { Text2("Thoát ván?", Type.Title) },
            text = {
                Text2(
                    if (ui.mode == Mode.LINK || ui.mode == Mode.LICHESS) "Thoát giữa chừng sẽ tính là bạn bỏ cuộc." else "Ván đang chơi sẽ không được lưu.",
                    Type.Body,
                )
            },
            confirmButton = { TextButton(onClick = { leaveAsk = false; vm.leaveGame() }) { Text2("Thoát", Type.Button.copy(color = Pal.Alert)) } },
            dismissButton = { TextButton(onClick = { leaveAsk = false }) { Text2("Ở lại", Type.Button.copy(color = Pal.EggDim)) } },
        )
    }

    val res = ui.result
    if (res != null && !resultDismissed) {
        val canRematch = ui.mode == Mode.ENGINE || ui.mode == Mode.API || ui.mode == Mode.LOCAL
        AlertDialog(
            onDismissRequest = { resultDismissed = true },
            containerColor = Pal.Panel,
            title = { Text2(resultTitle(res, ui.myWhite), Type.Display.copy(fontSize = 32.sp)) },
            text = { Text2(reasonText(res), Type.Body.copy(color = Pal.EggDim)) },
            confirmButton = {
                TextButton(onClick = { if (canRematch) vm.rematch() else vm.leaveGame() }) {
                    Text2(if (canRematch) "Ván mới" else "Về trang chủ", Type.Button.copy(color = Pal.Gold))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { resultDismissed = true }) { Text2("Xem bàn cờ", Type.Button.copy(color = Pal.EggDim)) }
                    if (canRematch) TextButton(onClick = { vm.leaveGame() }) { Text2("Thoát", Type.Button.copy(color = Pal.EggDim)) }
                }
            },
        )
    }

    val aiError = ui.aiError
    if (aiError != null) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Pal.Panel,
            title = { Text2("AI không đi được", Type.Title) },
            text = { Text2(aiError, Type.Body.copy(color = Pal.EggDim)) },
            confirmButton = { TextButton(onClick = vm::retryAi) { Text2("Thử lại", Type.Button.copy(color = Pal.Gold)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = vm::useEngineForThisMove) { Text2("Máy đi thay", Type.Button.copy(color = Pal.EggDim)) }
                    TextButton(onClick = { vm.leaveGame() }) { Text2("Thoát", Type.Button.copy(color = Pal.EggDim)) }
                }
            },
        )
    }
}

/** Thanh người chơi cho một bên (trắng hoặc đen). */
@Composable
private fun PlayerSide(vm: AppViewModel, white: Boolean) {
    val ui = vm.ui
    val captured = missingPieces(ui.board, !white)
    val lead = material(ui.board, white) - material(ui.board, !white)
    PlayerBar(
        name = if (white) ui.whiteName else ui.blackName,
        sub = if (white) ui.whiteSub else ui.blackSub,
        whiteSide = white,
        active = ui.result == null && ui.whiteToMove == white,
        timed = ui.timed,
        clock = { if (white) vm.whiteMs else vm.blackMs },
        captured = captured,
        lead = lead,
        thinking = ui.thinking && ui.whiteToMove == white,
    )
}

