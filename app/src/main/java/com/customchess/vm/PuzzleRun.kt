package com.customchess.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.customchess.core.Move
import com.customchess.core.Pc
import com.customchess.core.Position
import com.customchess.core.rankOf
import com.customchess.data.Progress
import com.customchess.data.Puzzle
import com.customchess.data.PuzzleBank
import com.customchess.net.PublicApi
import com.customchess.ui.Sfx
import com.customchess.ui.Snd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.random.Random

enum class PuzzleMode { NORMAL, RUSH, DAILY, ONLINE }

enum class PuzzleStatus { LOADING, SOLVING, WRONG, SOLVED, REVEALED, ERROR, OVER }

/**
 * Một phiên giải câu đố. Giữ toàn bộ trạng thái bằng state của Compose để màn hình tự vẽ lại;
 * điểm số, chuỗi ngày... được ghi vào [Progress] qua [getProgress] / [setProgress].
 */
class PuzzleRun(
    private val app: Application,
    val mode: PuzzleMode,
    private val filterKey: String,
    private val getProgress: () -> Progress,
    private val setProgress: (Progress) -> Unit,
    private val autoQueen: () -> Boolean,
) {
    var puzzle by mutableStateOf<Puzzle?>(null)
        private set
    var board by mutableStateOf(IntArray(64))
        private set
    var flipped by mutableStateOf(false)
        private set
    var lastMove by mutableStateOf<Move?>(null)
        private set
    var checkSquare by mutableIntStateOf(-1)
        private set
    var selected by mutableIntStateOf(-1)
        private set
    var targets by mutableStateOf<Set<Int>>(emptySet())
        private set
    var status by mutableStateOf(PuzzleStatus.LOADING)
        private set
    var message by mutableStateOf("")
        private set
    var hintSquare by mutableIntStateOf(-1)
        private set
    var hintMove by mutableStateOf<Move?>(null)
        private set
    var ratingDelta by mutableStateOf<Int?>(null)
        private set
    var promotion by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    /** Tăng mỗi khi bàn cờ có nước đi mới (cho hoạt ảnh trượt quân và âm thanh). */
    var ply by mutableIntStateOf(0)
        private set
    var lastCapture by mutableStateOf(false)
        private set
    /** Tăng khi cần máy đi nước đáp trả; màn hình chờ một chút rồi gọi [playReply]. */
    var replyToken by mutableIntStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var solution by mutableStateOf<List<String>>(emptyList())
        private set

    // ---- Puzzle Rush
    var score by mutableIntStateOf(0)
        private set
    var strikes by mutableIntStateOf(0)
        private set
    var timeLeftMs by mutableLongStateOf(RUSH_MS)
        private set
    var rushBest by mutableIntStateOf(0)
        private set
    var newRecord by mutableStateOf(false)
        private set

    private var pos: Position = Position.start()
    private var idx = 1
    private var failedOnce = false
    private var usedHint = false
    private var needReply = false
    private var served = HashSet<String>()

    val solverWhite: Boolean get() = !flipped

    /** Số nước người giải còn phải đi. */
    val movesLeft: Int get() = puzzle?.let { (it.moves.size - idx + 1) / 2 } ?: 0

    // ------------------------------------------------------------------ nạp câu đố

    /** Chọn và nạp câu đố kế tiếp. Chạy trong coroutine của giao diện (có chuyển luồng khi cần mạng). */
    suspend fun loadNext() {
        status = PuzzleStatus.LOADING
        error = null
        message = ""
        try {
            val p: Puzzle? = when (mode) {
                PuzzleMode.ONLINE -> withContext(Dispatchers.IO) { PublicApi.puzzleNext() }
                PuzzleMode.DAILY -> {
                    val fromNet = withContext(Dispatchers.IO) { runCatching { PublicApi.puzzleDaily() }.getOrNull() }
                    fromNet ?: PuzzleBank.dailyFallback(app, Progress.today())
                }
                else -> pickFromBank()
            }
            if (p == null || p.start() == null) {
                error = "Không có câu đố phù hợp"
                status = PuzzleStatus.ERROR
                return
            }
            setPuzzle(p)
        } catch (e: Exception) {
            error = e.message ?: "Không tải được câu đố"
            status = PuzzleStatus.ERROR
        }
    }

    private fun pickFromBank(): Puzzle? {
        val all = PuzzleBank.all(app)
        if (all.isEmpty()) return null
        val prog = getProgress()
        var pool = if (filterKey.isEmpty()) all else all.filter { filterKey in it.themes }
        if (pool.isEmpty()) pool = all
        val target = if (mode == PuzzleMode.RUSH) 600 + 120 * score else prog.puzzleRating
        val recent = prog.recent.toHashSet()
        val fresh = pool.filter { it.id !in recent && it.id !in served }.ifEmpty { pool.filter { it.id !in served } }.ifEmpty { pool }
        val near = fresh.sortedBy { abs(it.rating - target) }.take(12)
        return near.random(Random.Default)
    }

    private fun setPuzzle(p: Puzzle) {
        val start = p.start() ?: return
        served.add(p.id)
        puzzle = p
        pos = start
        idx = 1
        failedOnce = false
        usedHint = false
        needReply = false
        ratingDelta = null
        hintSquare = -1
        hintMove = null
        promotion = null
        solution = emptyList()
        flipped = !start.whiteToMove
        lastMove = p.setupMove()
        lastCapture = false
        syncBoard()
        selected = -1
        targets = emptySet()
        status = PuzzleStatus.SOLVING
        message = if (start.whiteToMove) "Trắng đi. Tìm nước tốt nhất." else "Đen đi. Tìm nước tốt nhất."
        ply++
    }

    private fun syncBoard() {
        board = pos.board.copyOf()
        checkSquare = if (pos.inCheck()) pos.kingSquare(pos.whiteToMove) else -1
    }

    // ------------------------------------------------------------------ thao tác

    fun tap(sq: Int) {
        if (status != PuzzleStatus.SOLVING || needReply || promotion != null) return
        val piece = pos.board[sq]
        val mine = piece != 0 && (piece > 0) == pos.whiteToMove
        if (selected >= 0) {
            if (sq in targets) { attempt(selected, sq, 0); return }
            if (mine && sq != selected) { select(sq); return }
            selected = -1
            targets = emptySet()
            return
        }
        if (mine) select(sq)
    }

    private fun select(sq: Int) {
        selected = sq
        targets = pos.legalMoves().filter { it.from == sq }.map { it.to }.toSet()
    }

    fun choosePromotion(type: Int) {
        val pr = promotion ?: return
        promotion = null
        attempt(pr.first, pr.second, type)
    }

    fun cancelPromotion() {
        promotion = null
        selected = -1
        targets = emptySet()
    }

    private fun attempt(from: Int, to: Int, promoIn: Int) {
        val p = puzzle ?: return
        val piece = pos.board[from]
        var promo = promoIn
        val promoting = abs(piece) == Pc.PAWN && rankOf(to) == (if (pos.whiteToMove) 7 else 0)
        if (promoting && promo == 0) {
            if (!autoQueen()) {
                promotion = from to to
                targets = emptySet()
                return
            }
            promo = Pc.QUEEN
        }
        val m = Move(from, to, promo)
        val expected = p.moves.getOrNull(idx) ?: return
        val next = pos.apply(m)
        val mates = next.inCheck() && next.legalMoves().isEmpty()
        val ok = m.uci() == expected || (mates && p.themes.any { it.startsWith("mate") })
        selected = -1
        targets = emptySet()
        hintSquare = -1
        if (!ok) {
            onWrong()
            return
        }
        lastCapture = pos.isCapture(m)
        Sfx.play(if (lastCapture) Snd.CAPTURE else Snd.MOVE)
        pos = next
        lastMove = m
        idx++
        syncBoard()
        ply++
        hintMove = null
        if (mates || idx >= p.moves.size) {
            onSolved()
        } else {
            needReply = true
            message = "Đúng rồi! Đối thủ đáp trả..."
            replyToken++
        }
    }

    /** Máy đi nước đáp trả của đối thủ. */
    fun playReply() {
        val p = puzzle ?: return
        if (!needReply) return
        val m = pos.parseMove(p.moves.getOrNull(idx) ?: return)
        if (m == null) { needReply = false; return }
        lastCapture = pos.isCapture(m)
        Sfx.play(if (lastCapture) Snd.CAPTURE else Snd.MOVE)
        pos = pos.apply(m)
        lastMove = m
        idx++
        needReply = false
        syncBoard()
        ply++
        message = "Tiếp tục, tìm nước kế tiếp."
        if (idx >= p.moves.size) onSolved()
    }

    private fun onWrong() {
        val p = puzzle ?: return
        status = PuzzleStatus.WRONG
        Sfx.play(Snd.BAD)
        message = "Chưa đúng. Thử nước khác."
        if (!failedOnce) {
            failedOnce = true
            if (mode != PuzzleMode.RUSH) recordResult(p, ok = false)
        }
        if (mode == PuzzleMode.RUSH) {
            strikes++
            if (strikes >= MAX_STRIKES) endRush()
        }
    }

    /** Tiếp tục giải sau khi đi sai. */
    fun retry() {
        if (status == PuzzleStatus.WRONG) {
            status = PuzzleStatus.SOLVING
            message = "Thử lại nào."
        }
    }

    private fun onSolved() {
        val p = puzzle ?: return
        status = PuzzleStatus.SOLVED
        Sfx.play(Snd.OK)
        message = if (failedOnce) "Đã giải xong (sau khi đi sai)." else "Chính xác!"
        if (!failedOnce) recordResult(p, ok = !usedHint)
        if (mode == PuzzleMode.RUSH) score++
        if (mode == PuzzleMode.DAILY) setProgress(getProgress().copy(dailyDone = Progress.today()).touch())
    }

    private fun recordResult(p: Puzzle, ok: Boolean) {
        val before = getProgress()
        val rated = mode != PuzzleMode.RUSH
        val after = before.afterPuzzle(p.id, p.rating, ok, rated)
        ratingDelta = if (rated) after.puzzleRating - before.puzzleRating else null
        setProgress(after)
    }

    /** Gợi ý: lần một chỉ quân cần đi, lần hai chỉ cả nước đi. Tính là đã dùng gợi ý. */
    fun hint() {
        val p = puzzle ?: return
        if (status != PuzzleStatus.SOLVING || needReply) return
        val m = pos.parseMove(p.moves.getOrNull(idx) ?: return) ?: return
        usedHint = true
        if (hintSquare < 0) {
            hintSquare = m.from
            message = "Hãy nhìn quân đang sáng."
        } else {
            hintMove = m
            message = "Mũi tên chỉ nước đi cần tìm."
        }
    }

    /** Hiện lời giải còn lại (tính là chưa giải được). */
    fun reveal() {
        val p = puzzle ?: return
        if (status != PuzzleStatus.SOLVING && status != PuzzleStatus.WRONG) return
        if (!failedOnce) {
            failedOnce = true
            recordResult(p, ok = false)
        }
        val sans = ArrayList<String>()
        var q = pos
        for (i in idx until p.moves.size) {
            val m = q.parseMove(p.moves[i]) ?: break
            sans.add(q.san(m))
            q = q.apply(m)
        }
        solution = sans
        hintMove = pos.parseMove(p.moves.getOrNull(idx) ?: "")
        status = PuzzleStatus.REVEALED
        message = "Lời giải: " + sans.joinToString(" ")
        if (mode == PuzzleMode.RUSH) {
            strikes++
            if (strikes >= MAX_STRIKES) endRush()
        }
    }

    // ------------------------------------------------------------------ Puzzle Rush

    fun startRush() {
        score = 0
        strikes = 0
        timeLeftMs = RUSH_MS
        newRecord = false
        rushBest = getProgress().rushBest
        served = HashSet()
    }

    /** Trừ thời gian của Rush; gọi đều đặn từ giao diện. */
    fun tickRush(dtMs: Long) {
        if (mode != PuzzleMode.RUSH || status == PuzzleStatus.OVER || status == PuzzleStatus.LOADING) return
        timeLeftMs = (timeLeftMs - dtMs).coerceAtLeast(0)
        if (timeLeftMs == 0L) endRush()
    }

    private fun endRush() {
        if (status == PuzzleStatus.OVER) return
        status = PuzzleStatus.OVER
        val prog = getProgress()
        newRecord = score > prog.rushBest
        rushBest = maxOf(score, prog.rushBest)
        setProgress((if (newRecord) prog.copy(rushBest = score) else prog).touch())
        message = "Hết lượt! Bạn giải được $score câu."
    }

    companion object {
        const val RUSH_MS = 180_000L
        const val MAX_STRIKES = 3
    }
}
