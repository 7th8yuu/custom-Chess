package com.customchess.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.customchess.core.Engine
import com.customchess.core.Move
import com.customchess.core.Position
import com.customchess.data.SavedGame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.max

enum class MoveClass(val label: String, val mark: String) {
    BEST("Nước tốt nhất", "★"),
    GOOD("Tốt", ""),
    INACCURACY("Thiếu chính xác", "?!"),
    MISTAKE("Sai lầm", "?"),
    BLUNDER("Sai lầm nghiêm trọng", "??"),
}

/** Xem lại một ván đã lưu: tua nước đi, chạy engine phân tích từng thế để chấm điểm từng nước. */
class ReviewState(val game: SavedGame) {
    val positions: List<Position>
    val moves: List<Move>
    val sans: List<String>

    init {
        var p = Position.fromFen(game.startFen) ?: Position.start()
        val ps = arrayListOf(p)
        val ms = ArrayList<Move>()
        val ss = ArrayList<String>()
        for (u in game.moves) {
            val m = p.parseMove(u) ?: break
            ss.add(p.san(m))
            p = p.apply(m)
            ms.add(m)
            ps.add(p)
        }
        positions = ps
        moves = ms
        sans = ss
    }

    /** Đang xem thế sau [index] nước (0 = thế ban đầu). */
    var index by mutableIntStateOf(0)

    /** Điểm theo quân trắng (centipawn) của từng thế; null = chưa phân tích. */
    val evals = mutableStateListOf<Int?>().also { l -> repeat(positions.size) { l.add(null) } }
    private val best = arrayOfNulls<Move>(positions.size)
    var analyzing by mutableStateOf(false)
        private set
    var analyzed by mutableIntStateOf(0)
        private set
    var done by mutableStateOf(false)
        private set

    val last: Int get() = moves.size

    fun goto(i: Int) {
        index = i.coerceIn(0, last)
    }

    fun bestMoveAt(i: Int): Move? = best.getOrNull(i)

    /** Phân tích từng thế bằng engine nội bộ (độ sâu thấp, vài trăm ms mỗi thế). Hủy bằng cách hủy coroutine. */
    suspend fun analyze(depth: Int = 4, msPerPosition: Long = 400) {
        if (done) return
        analyzing = true
        try {
            for (i in positions.indices) {
                if (evals[i] != null) { analyzed = i + 1; continue }
                val ctx = currentCoroutineContext()
                ctx.ensureActive()
                val pos = positions[i]
                val a = withContext(Dispatchers.Default) { Engine(3).analyze(pos, depth, msPerPosition) { !ctx.isActiveSafe() } }
                evals[i] = a.scoreWhite
                best[i] = a.best
                analyzed = i + 1
            }
            done = true
        } catch (e: CancellationException) {
            throw e
        } finally {
            analyzing = false
        }
    }

    private fun kotlin.coroutines.CoroutineContext.isActiveSafe(): Boolean = this[kotlinx.coroutines.Job]?.isActive ?: true

    private fun winPct(cp: Int): Double {
        val c = cp.coerceIn(-1000, 1000)
        return 50.0 + 50.0 * (2.0 / (1.0 + exp(-0.00368208 * c)) - 1.0)
    }

    /** Phần trăm thắng mà bên đi nước thứ [k] (0 = nước đầu tiên) đã đánh mất; null nếu chưa phân tích. */
    private fun lossAt(k: Int): Double? {
        val before = evals.getOrNull(k) ?: return null
        val after = evals.getOrNull(k + 1) ?: return null
        val white = positions[k].whiteToMove
        val wb = winPct(before)
        val wa = winPct(after)
        return max(0.0, if (white) wb - wa else wa - wb)
    }

    /** Đánh giá nước thứ [k]; null nếu chưa phân tích. */
    fun classify(k: Int): MoveClass? {
        val loss = lossAt(k) ?: return null
        val played = moves.getOrNull(k) ?: return null
        val b = best.getOrNull(k)
        return when {
            b != null && b == played -> MoveClass.BEST
            loss < 10 -> MoveClass.GOOD
            loss < 20 -> MoveClass.INACCURACY
            loss < 30 -> MoveClass.MISTAKE
            else -> MoveClass.BLUNDER
        }
    }

    /** Độ chính xác (0-100) của một bên, theo công thức của Lichess; null nếu chưa có dữ liệu. */
    fun accuracy(white: Boolean): Int? {
        var sum = 0.0
        var n = 0
        for (k in moves.indices) {
            if (positions[k].whiteToMove != white) continue
            val loss = lossAt(k) ?: continue
            sum += (103.1668 * exp(-0.04354 * loss) - 3.1669).coerceIn(0.0, 100.0)
            n++
        }
        return if (n == 0) null else (sum / n).toInt()
    }

    fun count(white: Boolean, c: MoveClass): Int =
        moves.indices.count { k -> positions[k].whiteToMove == white && classify(k) == c }
}
