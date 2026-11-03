package com.customchess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customchess.core.Move
import com.customchess.core.Pc
import com.customchess.core.Position
import com.customchess.core.rankOf
import com.customchess.data.LESSONS
import com.customchess.data.Lesson
import com.customchess.data.lessonById
import com.customchess.vm.AppViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun ThinBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Pal.Gold) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Pal.Egg.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(CircleShape).background(color))
    }
}

// ---------------------------------------------------------------------------------- tab Học

@Composable
fun LearnScreen(vm: AppViewModel) {
    val prog = vm.progress
    val finished = LESSONS.count { (prog.lessons[it.id] ?: 0) >= it.steps.size }
    TabColumn {
        TabHeader("Học cờ", "Từ cách đi quân tới chiến thuật")
        GlassCard(padding = 18.dp, tint = Pal.Secondary) {
            Text("Tiến độ của bạn", style = Type.Small)
            Text("$finished / ${LESSONS.size} bài", style = Type.Title.copy(fontSize = 26.sp, color = Pal.Gold))
            Spacer(Modifier.height(10.dp))
            ThinBar(finished / LESSONS.size.toFloat())
            Text(
                if (finished == LESSONS.size) "Bạn đã học hết! Sang tab Câu đố để luyện thêm." else "Mỗi bài có vài bước ngắn, bạn tự đi quân trên bàn cờ để thực hành.",
                style = Type.Small, modifier = Modifier.padding(top = 10.dp),
            )
        }
        for (level in listOf("Cơ bản", "Trung bình")) {
            SectionTitle(level)
            for (l in LESSONS.filter { it.level == level }) {
                LessonRow(l, prog.lessons[l.id] ?: 0) { vm.openLesson(l.id) }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun LessonRow(l: Lesson, done: Int, onClick: () -> Unit) {
    val complete = done >= l.steps.size
    GlassBox(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), tint = if (complete) Pal.Jade else Color.Unspecified, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background((if (complete) Pal.Jade else Pal.Gold).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                if (complete) Icon(Icons.Rounded.Check, null, tint = Pal.Jade, modifier = Modifier.size(26.dp))
                else PieceIcon(lessonPiece(l.id), 34.dp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(l.title, style = Type.Heading)
                Text(l.desc, style = Type.Small, maxLines = 2)
                if (done > 0 && !complete) ThinBar(done / l.steps.size.toFloat(), Modifier.padding(top = 8.dp))
            }
            Pill2("${minOf(done, l.steps.size)}/${l.steps.size}", if (complete) Pal.Jade else Pal.Gold)
        }
    }
}

private fun lessonPiece(id: String): Int = when (id) {
    "pawn" -> Pc.PAWN
    "knight", "fork" -> Pc.KNIGHT
    "bishop", "pin" -> Pc.BISHOP
    "rook" -> Pc.ROOK
    "queen" -> Pc.QUEEN
    else -> Pc.KING
}

// ---------------------------------------------------------------------------------- một bài học

private class LessonBoard(fen: String) {
    var pos by mutableStateOf(Position.fromFen(fen) ?: Position.start())
    var selected by mutableIntStateOf(-1)
    var targets by mutableStateOf<Set<Int>>(emptySet())
    var last by mutableStateOf<Move?>(null)
    var ply by mutableIntStateOf(0)
    var capture by mutableStateOf(false)
    var promotion by mutableStateOf<Pair<Int, Int>?>(null)
    /** 0 = đang làm, 1 = đúng, 2 = sai. */
    var state by mutableIntStateOf(0)
}

@Composable
fun LessonScreen(vm: AppViewModel, theme: BoardTheme) {
    val lesson = lessonById(vm.lessonId)
    if (lesson == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    var step by remember(lesson.id) { mutableIntStateOf(0) }
    var finished by remember(lesson.id) { mutableStateOf(false) }
    var confetti by remember { mutableIntStateOf(0) }
    var showHint by remember(lesson.id, step) { mutableStateOf(false) }
    val st = lesson.steps[step]
    val lb = remember(lesson.id, step) { LessonBoard(st.fen) }
    val task = st.expect.isNotEmpty()
    val s = vm.settings

    LaunchedEffect(lb.state) {
        if (lb.state == 2) { delay(1100); if (lb.state == 2) lb.state = 0 }
    }

    fun attempt(from: Int, to: Int, promo: Int) {
        val m = Move(from, to, promo)
        lb.selected = -1
        lb.targets = emptySet()
        if (m.uci() in st.expect) {
            lb.capture = lb.pos.isCapture(m)
            Sfx.play(if (lb.capture) Snd.CAPTURE else Snd.MOVE)
            lb.pos = lb.pos.apply(m)
            lb.last = m
            lb.ply++
            lb.state = 1
            Sfx.play(Snd.OK)
        } else {
            lb.state = 2
            Sfx.play(Snd.BAD)
        }
    }

    fun tap(sq: Int) {
        if (!task || lb.state != 0 || lb.promotion != null) return
        val p = lb.pos
        val piece = p.board[sq]
        val mine = piece != 0 && (piece > 0) == p.whiteToMove
        fun select(q: Int) {
            lb.selected = q
            lb.targets = p.legalMoves().filter { it.from == q }.map { it.to }.toSet()
        }
        if (lb.selected >= 0) {
            if (sq in lb.targets) {
                val from = lb.selected
                val pc = p.board[from]
                if (abs(pc) == Pc.PAWN && rankOf(sq) == (if (p.whiteToMove) 7 else 0)) {
                    lb.promotion = from to sq
                    lb.targets = emptySet()
                } else attempt(from, sq, 0)
                return
            }
            if (mine && sq != lb.selected) { select(sq); return }
            lb.selected = -1
            lb.targets = emptySet()
            return
        }
        if (mine) select(sq)
    }

    val lastStep = step == lesson.steps.size - 1
    val canAdvance = !task || lb.state == 1

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
            ScreenHeader(lesson.title, vm::back, trailing = { Pill2("${minOf(step + 1, lesson.steps.size)}/${lesson.steps.size}") })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                ThinBar(if (finished) 1f else step / lesson.steps.size.toFloat())
                Spacer(Modifier.height(12.dp))
                if (!finished) {
                    GlassCard(padding = 14.dp) { Text(st.text, style = Type.Body) }
                    Spacer(Modifier.height(12.dp))
                    ChessBoard(
                        board = lb.pos.board, flipped = !(Position.fromFen(st.fen)?.whiteToMove ?: true), lastMove = lb.last,
                        checkSquare = if (lb.pos.inCheck()) lb.pos.kingSquare(lb.pos.whiteToMove) else -1,
                        selected = lb.selected, targets = lb.targets, plyCount = lb.ply, theme = theme, modifier = Modifier.fillMaxWidth(),
                        interactive = task && lb.state == 0 && lb.promotion == null, showHints = s.showHints,
                        isMine = { sq -> val pc = lb.pos.board[sq]; pc != 0 && (pc > 0) == lb.pos.whiteToMove && lb.state == 0 },
                        onSquare = ::tap, capture = lb.capture, highlightLast = s.highlightLast,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (task) {
                        when (lb.state) {
                            1 -> GlassCard(padding = 14.dp, tint = Pal.Jade) { Text(st.done, style = Type.Heading.copy(color = Pal.Jade)) }
                            2 -> GlassCard(padding = 14.dp, tint = Pal.Alert) { Text("Chưa đúng, thử lại nhé.", style = Type.Heading.copy(color = Pal.Alert)) }
                            else -> if (showHint && st.hint.isNotEmpty()) GlassCard(padding = 14.dp) { Text("Gợi ý: ${st.hint}", style = Type.Body) }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (canAdvance) {
                        PrimaryButton(if (lastStep) "Hoàn thành" else "Tiếp tục", {
                            vm.saveLessonStep(lesson.id, step + 1)
                            if (lastStep) { finished = true; confetti++; Sfx.play(Snd.WIN) } else step++
                        })
                    } else if (st.hint.isNotEmpty() && !showHint) {
                        GhostButton("Gợi ý", { showHint = true }, Modifier.fillMaxWidth())
                    }
                } else {
                    GlassCard(padding = 20.dp, tint = Pal.Gold) {
                        Text("Hoàn thành bài học!", style = Type.Title)
                        Text("Bạn đã học xong \"${lesson.title}\".", style = Type.Body, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    val next = LESSONS.getOrNull(LESSONS.indexOfFirst { it.id == lesson.id } + 1)
                    if (next != null) {
                        PrimaryButton("Bài tiếp theo: ${next.title}", { vm.back(); vm.openLesson(next.id) })
                        Spacer(Modifier.height(8.dp))
                    }
                    GhostButton("Về danh sách bài học", vm::back, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(30.dp))
            }
        }
        ConfettiBurst(confetti, Modifier.fillMaxSize())
    }

    lb.promotion?.let { pr ->
        PromotionDialog(
            white = lb.pos.whiteToMove,
            onChoose = { t -> lb.promotion = null; attempt(pr.first, pr.second, t) },
            onCancel = { lb.promotion = null; lb.selected = -1; lb.targets = emptySet() },
        )
    }
}
