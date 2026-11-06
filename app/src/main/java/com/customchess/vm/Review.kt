package com.customchess.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.customchess.core.Engine
import com.customchess.core.Move
import com.customchess.core.MoveClass
import com.customchess.core.MoveGrade
import com.customchess.core.Position
import com.customchess.data.SavedGame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.exp

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

    /** Điểm (góc nhìn bên đi) của nước tốt thứ hai ở thế trước mỗi nước đi; chỉ tính ở những thế đáng xét "nước rất hay". */
    private val alts = mutableStateListOf<Int?>().also { l -> repeat(positions.size) { l.add(null) } }
    private val cache = arrayOfNulls<MoveClass>(positions.size)

    var analyzing by mutableStateOf(false)
        private set
    var analyzed by mutableIntStateOf(0)
        private set

    /** 1 = đang chấm từng thế, 2 = đang tìm nước tốt thứ hai để xét "nước rất hay". */
    var phase by mutableIntStateOf(0)
        private set
    var done by mutableStateOf(false)
        private set

    /** Độ sâu của lần phân tích gần nhất (0 = chưa chạy); để giao diện đề nghị chạy kỹ hơn. */
    var depthUsed by mutableIntStateOf(0)
        private set

    val last: Int get() = moves.size

    fun goto(i: Int) {
        index = i.coerceIn(0, last)
    }

    fun bestMoveAt(i: Int): Move? = best.getOrNull(i)

    /** Xóa kết quả để phân tích lại (thường là ở độ sâu cao hơn). */
    fun reset() {
        for (i in evals.indices) { evals[i] = null; alts[i] = null; best[i] = null; cache[i] = null }
        analyzed = 0
        phase = 0
        done = false
        depthUsed = 0
    }

    private fun invalidate(i: Int) {
        if (i in cache.indices) cache[i] = null
    }

    /**
     * Phân tích từng thế bằng engine nội bộ rồi tìm nước tốt thứ hai ở những thế đáng xét.
     * Hủy bằng cách hủy coroutine; chạy tiếp thì bỏ qua phần đã làm.
     */
    suspend fun analyze(depth: Int = 4, msPerPosition: Long = 400) {
        if (done) return
        analyzing = true
        depthUsed = depth
        try {
            phase = 1
            for (i in positions.indices) {
                if (evals[i] != null) { analyzed = i + 1; continue }
                val ctx = currentCoroutineContext()
                ctx.ensureActive()
                val pos = positions[i]
                val a = withContext(Dispatchers.Default) { Engine(3).analyze(pos, depth, msPerPosition) { !ctx.isActiveSafe() } }
                best[i] = a.best
                evals[i] = a.scoreWhite
                invalidate(i - 1); invalidate(i)
                analyzed = i + 1
            }
            phase = 2
            for (k in moves.indices) {
                if (alts[k] != null) continue
                val ctx = currentCoroutineContext()
                ctx.ensureActive()
                val before = evals[k] ?: continue
                val prev = if (k > 0) moves[k - 1] else null
                if (!MoveGrade.worthAlternativeCheck(positions[k], moves[k], prev, before, best[k])) continue
                val pos = positions[k]
                val played = moves[k]
                val ranked = withContext(Dispatchers.Default) {
                    Engine(3).rankMoves(pos, depth, msPerPosition * 3) { !ctx.isActiveSafe() }
                }
                alts[k] = MoveGrade.secondBest(ranked, played)
                invalidate(k)
            }
            done = true
        } catch (e: CancellationException) {
            throw e
        } finally {
            analyzing = false
        }
    }

    private fun kotlin.coroutines.CoroutineContext.isActiveSafe(): Boolean = this[kotlinx.coroutines.Job]?.isActive ?: true

    /** Phần trăm thắng mà bên đi nước thứ [k] (0 = nước đầu tiên) đã đánh mất; null nếu chưa phân tích. */
    private fun lossAt(k: Int): Double? {
        val before = evals.getOrNull(k) ?: return null
        val after = evals.getOrNull(k + 1) ?: return null
        return MoveGrade.lossPct(positions[k].whiteToMove, before, after)
    }

    /** Đánh giá nước thứ [k]; null nếu chưa phân tích. */
    fun classify(k: Int): MoveClass? {
        val before = evals.getOrNull(k) ?: return null
        val after = evals.getOrNull(k + 1) ?: return null
        val alt = alts.getOrNull(k) // đọc để giao diện cập nhật khi tìm xong nước thay thế
        cache.getOrNull(k)?.let { return it }
        val move = moves.getOrNull(k) ?: return null
        val c = MoveGrade.classify(
            before = positions[k], move = move, prevMove = moves.getOrNull(k - 1),
            evalBefore = before, evalAfter = after, best = best[k], reply = best.getOrNull(k + 1), altMoverCp = alt,
        )
        cache[k] = c
        return c
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
