package com.customchess.core

import kotlin.math.abs

/** AI nội bộ (chạy offline): negamax + alpha-beta + quiescence + đào sâu dần theo thời gian. */
class Engine(private val level: Int) {

    private class Abort : RuntimeException(null, null, false, false)

    private var deadline = 0L
    private var nodes = 0L
    private var stop: () -> Boolean = { false }

    val depthForLevel: Int get() = when (level) { 1 -> 1; 2 -> 2; 3 -> 3; 4 -> 4; else -> 6 }
    private val timeMs: Long get() = when (level) { 1 -> 300L; 2 -> 600L; 3 -> 1500L; 4 -> 3000L; else -> 6000L }

    /**
     * Chọn nước đi cho bên đang tới lượt. [history] là số lần mỗi thế đã xuất hiện, dùng để
     * tránh lặp thế vô nghĩa khi đang hơn quân.
     */
    fun bestMove(
        root: Position,
        history: Map<String, Int> = emptyMap(),
        shouldStop: () -> Boolean = { false },
    ): Move? {
        val pos = root.copy()
        val legal = pos.legalMoves()
        if (legal.isEmpty()) return null
        if (legal.size == 1) return legal[0]
        stop = shouldStop
        deadline = System.nanoTime() + timeMs * 1_000_000L
        nodes = 0

        // Cấp 1: đôi khi đi ngẫu nhiên cho dễ thở.
        if (level == 1 && Math.random() < 0.35) return legal.random()

        var best: Move = orderMoves(pos, legal)[0]
        var bestScore = -INF
        for (depth in 1..depthForLevel) {
            try {
                var alpha = -INF
                var iterBest: Move? = null
                var iterScore = -INF
                for (m in orderMoves(pos, legal, best)) {
                    val child = pos.apply(m)
                    var s = -negamax(child, depth - 1, -INF, -alpha, 1)
                    val seen = history[child.repetitionKey()] ?: 0
                    if (seen > 0) s -= 25 * seen
                    if (s > iterScore) { iterScore = s; iterBest = m }
                    if (s > alpha) alpha = s
                }
                if (iterBest != null) { best = iterBest; bestScore = iterScore }
                if (abs(bestScore) > MATE - 200) break // đã thấy chiếu hết
            } catch (_: Abort) {
                break
            }
        }
        // Cấp thấp: thêm chút "nhiễu" để không đều đều.
        if (level == 2 && Math.random() < 0.15) return legal.random()
        return best
    }

    private fun checkTime() {
        if ((nodes and 1023L) == 0L && (System.nanoTime() > deadline || stop())) throw Abort()
    }

    private fun negamax(pos: Position, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
        nodes++
        checkTime()
        if (pos.halfmove >= 100 || pos.insufficientMaterial()) return 0
        val legal = pos.legalMoves()
        if (legal.isEmpty()) return if (pos.inCheck()) -(MATE - ply) else 0
        if (depth <= 0) return quiesce(pos, alphaIn, beta, 0)
        var alpha = alphaIn
        for (m in orderMoves(pos, legal)) {
            val s = -negamax(pos.apply(m), depth - 1, -beta, -alpha, ply + 1)
            if (s >= beta) return beta
            if (s > alpha) alpha = s
        }
        return alpha
    }

    private fun quiesce(pos: Position, alphaIn: Int, beta: Int, qd: Int): Int {
        nodes++
        checkTime()
        val stand = evaluateForSide(pos)
        if (stand >= beta) return beta
        var alpha = alphaIn
        if (stand > alpha) alpha = stand
        if (qd >= 6) return alpha
        val caps = pos.legalMoves().filter { pos.isCapture(it) || it.promo == Pc.QUEEN }
        for (m in orderMoves(pos, caps)) {
            val s = -quiesce(pos.apply(m), -beta, -alpha, qd + 1)
            if (s >= beta) return beta
            if (s > alpha) alpha = s
        }
        return alpha
    }

    private fun orderMoves(pos: Position, moves: List<Move>, first: Move? = null): List<Move> {
        val b = pos.board
        return moves.sortedByDescending { m ->
            var s = 0
            if (first != null && m == first) s += 100000
            val victim = abs(b[m.to])
            if (victim != 0) s += 10 * VALUE[victim] - VALUE[abs(b[m.from])] / 10 + 1000
            else if (abs(b[m.from]) == Pc.PAWN && fileOf(m.from) != fileOf(m.to)) s += 1000 + 10 * VALUE[Pc.PAWN]
            if (m.promo != 0) s += VALUE[m.promo] + 500
            s
        }
    }

    companion object {
        const val INF = 1_000_000
        const val MATE = 100_000
        val VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 0)

        /** Điểm từ góc nhìn bên đang tới lượt. */
        fun evaluateForSide(pos: Position): Int {
            val e = evaluate(pos)
            return if (pos.whiteToMove) e else -e
        }

        /** Điểm từ góc nhìn quân trắng (centipawn). */
        fun evaluate(pos: Position): Int {
            val b = pos.board
            var material = 0
            for (i in 0 until 64) {
                val p = b[i]
                if (p != 0) {
                    val t = abs(p)
                    if (t != Pc.KING && t != Pc.PAWN) material += VALUE[t]
                }
            }
            val endgame = material <= 2600
            var score = 0
            for (i in 0 until 64) {
                val p = b[i]
                if (p == 0) continue
                val t = abs(p)
                val white = p > 0
                val idx = if (white) (7 - rankOf(i)) * 8 + fileOf(i) else rankOf(i) * 8 + fileOf(i)
                val table = when (t) {
                    Pc.PAWN -> PAWN_T
                    Pc.KNIGHT -> KNIGHT_T
                    Pc.BISHOP -> BISHOP_T
                    Pc.ROOK -> ROOK_T
                    Pc.QUEEN -> QUEEN_T
                    else -> if (endgame) KING_END_T else KING_MID_T
                }
                val v = VALUE[t] + table[idx]
                score += if (white) v else -v
            }
            return score
        }

        private val PAWN_T = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0,
            5, -5, -10, 0, 0, -10, -5, 5,
            5, 10, 10, -20, -20, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0,
        )
        private val KNIGHT_T = intArrayOf(
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20, 0, 0, 0, 0, -20, -40,
            -30, 0, 10, 15, 15, 10, 0, -30,
            -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30,
            -30, 5, 10, 15, 15, 10, 5, -30,
            -40, -20, 0, 5, 5, 0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50,
        )
        private val BISHOP_T = intArrayOf(
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 10, 10, 5, 0, -10,
            -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 10, 10, 10, 10, 0, -10,
            -10, 10, 10, 10, 10, 10, 10, -10,
            -10, 5, 0, 0, 0, 0, 5, -10,
            -20, -10, -10, -10, -10, -10, -10, -20,
        )
        private val ROOK_T = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            5, 10, 10, 10, 10, 10, 10, 5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            0, 0, 0, 5, 5, 0, 0, 0,
        )
        private val QUEEN_T = intArrayOf(
            -20, -10, -10, -5, -5, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 5, 5, 5, 0, -10,
            -5, 0, 5, 5, 5, 5, 0, -5,
            0, 0, 5, 5, 5, 5, 0, -5,
            -10, 5, 5, 5, 5, 5, 0, -10,
            -10, 0, 5, 0, 0, 0, 0, -10,
            -20, -10, -10, -5, -5, -10, -10, -20,
        )
        private val KING_MID_T = intArrayOf(
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20,
            20, 30, 10, 0, 0, 10, 30, 20,
        )
        private val KING_END_T = intArrayOf(
            -50, -40, -30, -20, -20, -30, -40, -50,
            -30, -20, -10, 0, 0, -10, -20, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -30, 0, 0, 0, 0, -30, -30,
            -50, -30, -30, -30, -30, -30, -30, -50,
        )
    }
}
