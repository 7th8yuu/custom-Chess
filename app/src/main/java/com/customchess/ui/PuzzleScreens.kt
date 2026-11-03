package com.customchess.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customchess.core.Pc
import com.customchess.data.PUZZLE_FILTERS
import com.customchess.data.PuzzleBank
import com.customchess.data.themeLabel
import com.customchess.vm.AppViewModel
import com.customchess.vm.PuzzleMode
import com.customchess.vm.PuzzleRun
import com.customchess.vm.PuzzleStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Hộp thoại chọn quân khi phong cấp. */
@Composable
fun PromotionDialog(white: Boolean, onChoose: (Int) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Pal.Panel,
        title = { Text("Phong cấp thành", style = Type.Title) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (t in listOf(Pc.QUEEN, Pc.ROOK, Pc.BISHOP, Pc.KNIGHT)) {
                    Box(
                        Modifier.size(62.dp).clip(RoundedCornerShape(16.dp)).background(Pal.PanelHi).clickable { onChoose(t) },
                        contentAlignment = Alignment.Center,
                    ) { PieceIcon(if (white) t else -t, 52.dp) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Hủy", style = Type.Button.copy(color = Pal.EggDim)) } },
    )
}

// ---------------------------------------------------------------------------------- tab Câu đố

@Composable
fun PuzzlesScreen(vm: AppViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prog = vm.progress
    var filter by remember { mutableStateOf("") }
    val total = remember { PuzzleBank.all(ctx).size }
    TabColumn {
        TabHeader("Câu đố", "Rèn chiến thuật mỗi ngày")
        GlassCard(padding = 18.dp, tint = Pal.Tertiary) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Điểm câu đố", style = Type.Small)
                    Text("${prog.puzzleRating}", style = Type.Display.copy(fontSize = 44.sp, lineHeight = 48.sp, color = Pal.Gold))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Đã giải ${prog.solved}", style = Type.Body)
                    Text("Sai ${prog.failed}", style = Type.Small)
                    Text("Kỷ lục Rush ${prog.rushBest}", style = Type.Small)
                }
            }
        }

        SectionTitle("Chơi")
        ActionRow(Icons.Rounded.Extension, "Giải câu đố", "Câu hợp với điểm của bạn" + if (filter.isEmpty()) "" else " · ${PUZZLE_FILTERS.first { it.key == filter }.label}", { vm.startPuzzles(PuzzleMode.NORMAL, filter) })
        Spacer(Modifier.height(10.dp))
        ActionRow(Icons.Rounded.Bolt, "Puzzle Rush", "3 phút, tối đa 3 lần sai. Giải được càng nhiều càng tốt", { vm.startPuzzles(PuzzleMode.RUSH) }, tint = Pal.Tertiary)
        Spacer(Modifier.height(10.dp))
        ActionRow(
            Icons.Rounded.LocalFireDepartment, "Câu đố hằng ngày", if (vm.dailyDone) "Hôm nay bạn đã giải xong" else "Một câu mới mỗi ngày",
            { vm.startPuzzles(PuzzleMode.DAILY) }, tint = Pal.Secondary, trailing = if (vm.dailyDone) "Xong" else null,
        )
        Spacer(Modifier.height(10.dp))
        ActionRow(Icons.Rounded.Public, "Câu đố từ Lichess", "Câu mới mỗi lần, cần Internet", { vm.startPuzzles(PuzzleMode.ONLINE) })

        SectionTitle("Chủ đề")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in PUZZLE_FILTERS) Pill(f.label, filter == f.key, { filter = f.key })
        }
        Text("Chủ đề chỉ áp dụng cho nút \"Giải câu đố\". Bộ có sẵn gồm $total câu, tự tạo và kiểm tra trong ứng dụng.", style = Type.Small, modifier = Modifier.padding(top = 8.dp))
    }
}

// ---------------------------------------------------------------------------------- giải câu đố

private fun clockText(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

@Composable
fun PuzzleScreen(vm: AppViewModel, theme: BoardTheme) {
    val run = vm.puzzleRun
    if (run == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val s = vm.settings
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var solvedTick by remember { mutableIntStateOf(0) }
    var exitAsk by remember { mutableStateOf(false) }

    LaunchedEffect(run) { run.loadNext() }
    LaunchedEffect(run.replyToken) {
        if (run.replyToken > 0) {
            delay(if (Fx.anim) 520 else 150)
            run.playReply()
        }
    }
    if (run.mode == PuzzleMode.RUSH) {
        LaunchedEffect(run) {
            var last = SystemClock.elapsedRealtime()
            while (true) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                run.tickRush(now - last)
                last = now
            }
        }
    }
    LaunchedEffect(run.status) {
        when (run.status) {
            PuzzleStatus.SOLVED -> {
                solvedTick++
                if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (run.mode == PuzzleMode.RUSH) { delay(350); run.loadNext() }
                else if (s.puzzleAutoNext && (run.mode == PuzzleMode.NORMAL || run.mode == PuzzleMode.ONLINE)) { delay(1500); run.loadNext() }
            }
            PuzzleStatus.WRONG -> if (run.mode == PuzzleMode.RUSH) { delay(700); if (run.status == PuzzleStatus.WRONG) run.loadNext() }
            PuzzleStatus.REVEALED -> if (run.mode == PuzzleMode.RUSH) { delay(1400); if (run.status == PuzzleStatus.REVEALED) run.loadNext() }
            else -> Unit
        }
    }

    val title = when (run.mode) {
        PuzzleMode.NORMAL -> "Giải câu đố"
        PuzzleMode.RUSH -> "Puzzle Rush"
        PuzzleMode.DAILY -> "Câu đố hằng ngày"
        PuzzleMode.ONLINE -> "Câu đố Lichess"
    }
    val p = run.puzzle
    val solving = run.status == PuzzleStatus.SOLVING

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
            ScreenHeader(title, onBack = {
                if (run.mode == PuzzleMode.RUSH && run.status != PuzzleStatus.OVER && run.score > 0) exitAsk = true else vm.back()
            }, trailing = { Pill2("Điểm ${vm.progress.puzzleRating}") })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (run.mode == PuzzleMode.RUSH) RushBar(run)

                if (p != null) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PieceIcon(if (run.solverWhite) Pc.KING else -Pc.KING, 26.dp)
                        Text(
                            if (run.solverWhite) "Trắng đi" else "Đen đi",
                            style = Type.Heading.copy(fontSize = 16.sp), modifier = Modifier.padding(start = 8.dp).weight(1f),
                        )
                        if (run.mode != PuzzleMode.RUSH) Text("Độ khó ${p.rating}", style = Type.Small)
                    }
                    ChessBoard(
                        board = run.board, flipped = run.flipped, lastMove = run.lastMove, checkSquare = run.checkSquare,
                        selected = run.selected, targets = run.targets, plyCount = run.ply, theme = theme,
                        modifier = Modifier.fillMaxWidth(),
                        interactive = solving && run.promotion == null,
                        showHints = s.showHints,
                        isMine = { sq -> val pc = run.board[sq]; pc != 0 && (pc > 0) == run.solverWhite && solving },
                        onSquare = run::tap,
                        capture = run.lastCapture, highlightLast = s.highlightLast,
                        arrow = run.hintMove, hintSquare = run.hintSquare,
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        if (run.status == PuzzleStatus.LOADING) CircularProgressIndicator(color = Pal.Gold)
                    }
                }

                Spacer(Modifier.height(10.dp))
                val ok = run.status == PuzzleStatus.SOLVED
                val bad = run.status == PuzzleStatus.WRONG || run.status == PuzzleStatus.REVEALED
                GlassCard(padding = 14.dp, tint = if (ok) Pal.Jade else if (bad) Pal.Alert else Color.Unspecified) {
                    val msg = when (run.status) {
                        PuzzleStatus.LOADING -> "Đang tìm câu đố..."
                        PuzzleStatus.ERROR -> run.error ?: "Không tải được câu đố"
                        else -> run.message
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            msg, style = Type.Heading.copy(fontSize = 16.sp, color = if (ok) Pal.Jade else if (bad) Pal.Alert else Pal.Egg),
                            modifier = Modifier.weight(1f),
                        )
                        run.ratingDelta?.let { d ->
                            Pill2(if (d >= 0) "+$d" else "$d", if (d >= 0) Pal.Jade else Pal.Alert)
                        }
                    }
                    if ((ok || run.status == PuzzleStatus.REVEALED) && p != null && run.mode != PuzzleMode.RUSH) {
                        val themes = p.themes.filter { it != "short" && it != "long" && it != "veryLong" && it != "oneMove" && it != "crushing" && it != "advantage" }.take(4).map { themeLabel(it) }
                        if (themes.isNotEmpty()) Text("Chủ đề: " + themes.joinToString(" · "), style = Type.Small, modifier = Modifier.padding(top = 6.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (run.status != PuzzleStatus.OVER) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (run.status) {
                            PuzzleStatus.SOLVING -> {
                                if (s.puzzleHints && run.mode != PuzzleMode.RUSH) GhostButton("Gợi ý", run::hint, Modifier.weight(1f))
                                GhostButton("Xem lời giải", run::reveal, Modifier.weight(1f), danger = true)
                            }
                            PuzzleStatus.WRONG -> {
                                if (run.mode != PuzzleMode.RUSH) {
                                    PrimaryButton("Thử lại", run::retry, Modifier.weight(1f))
                                    GhostButton("Xem lời giải", run::reveal, Modifier.weight(1f), danger = true)
                                }
                            }
                            PuzzleStatus.SOLVED, PuzzleStatus.REVEALED -> if (run.mode != PuzzleMode.RUSH) {
                                if (run.mode == PuzzleMode.DAILY) PrimaryButton("Xong", vm::back, Modifier.weight(1f))
                                else PrimaryButton("Câu tiếp theo", { scope.launch { run.loadNext() } }, Modifier.weight(1f))
                            }
                            PuzzleStatus.ERROR -> {
                                PrimaryButton("Thử lại", { scope.launch { run.loadNext() } }, Modifier.weight(1f))
                            }
                            else -> Unit
                        }
                    }
                } else {
                    RushResult(run, onAgain = { run.startRush(); scope.launch { run.loadNext() } }, onExit = vm::back)
                }
                Spacer(Modifier.height(30.dp))
            }
        }
        ConfettiBurst(solvedTick, Modifier.fillMaxSize())
    }

    run.promotion?.let { pr ->
        PromotionDialog(white = run.solverWhite, onChoose = run::choosePromotion, onCancel = run::cancelPromotion)
    }
    if (exitAsk) {
        AlertDialog(
            onDismissRequest = { exitAsk = false },
            containerColor = Pal.Panel,
            title = { Text("Thoát Puzzle Rush?", style = Type.Title) },
            text = { Text("Lượt hiện tại sẽ kết thúc ngay và tính điểm ${run.score} câu đã giải.", style = Type.Body) },
            confirmButton = { TextButton(onClick = { exitAsk = false; vm.back() }) { Text("Thoát", style = Type.Button.copy(color = Pal.Alert)) } },
            dismissButton = { TextButton(onClick = { exitAsk = false }) { Text("Chơi tiếp", style = Type.Button.copy(color = Pal.EggDim)) } },
        )
    }
}

@Composable
private fun RushBar(run: PuzzleRun) {
    GlassCard(padding = 12.dp, tint = Pal.Tertiary) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Thời gian", style = Type.Small)
                Text(clockText(run.timeLeftMs), style = Type.Clock.copy(fontSize = 26.sp, color = if (run.timeLeftMs < 20_000) Pal.Alert else Pal.Egg))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Đã giải", style = Type.Small)
                Text("${run.score}", style = Type.Title.copy(color = Pal.Gold))
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                for (i in 0 until PuzzleRun.MAX_STRIKES) {
                    Icon(
                        Icons.Rounded.Close, null,
                        tint = if (i < run.strikes) Pal.Alert else Pal.Egg.copy(alpha = 0.25f),
                        modifier = Modifier.padding(start = 4.dp).size(26.dp),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun RushResult(run: PuzzleRun, onAgain: () -> Unit, onExit: () -> Unit) {
    GlassCard(padding = 18.dp, tint = Pal.Gold) {
        Text(if (run.newRecord) "Kỷ lục mới!" else "Hết lượt", style = Type.Title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("${run.score}", style = Type.Display.copy(fontSize = 60.sp, lineHeight = 64.sp, color = Pal.Gold), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("câu đã giải · kỷ lục ${run.rushBest}", style = Type.Small, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Chơi lại", onAgain)
        Spacer(Modifier.height(8.dp))
        GhostButton("Thoát", onExit, Modifier.fillMaxWidth())
    }
}
