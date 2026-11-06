package com.customchess.core

import java.util.Locale
import kotlin.math.abs

/** Một nước engine gợi ý: [cpWhite] là điểm theo góc nhìn quân trắng (centipawn), [mateIn] là số nước tới chiếu hết nếu có. */
data class CoachLine(val move: Move, val san: String, val cpWhite: Int, val mateIn: Int?)

/**
 * Phân tích một thế cờ bằng engine nội bộ (offline): các nước tốt nhất kèm điểm.
 * Phần lời giải thích do AI (nếu người dùng bật) viết dựa trên chính các số liệu này, nên AI không phải tự tính nước đi.
 */
object Coach {
    /** Các nước tốt nhất của bên đang tới lượt, điểm cao nhất trước. Rỗng nếu thế cờ đã hết nước đi. */
    fun topMoves(
        pos: Position,
        count: Int = 3,
        depth: Int = 4,
        limitMs: Long = 5000,
        shouldStop: () -> Boolean = { false },
    ): List<CoachLine> {
        val legal = pos.legalMoves()
        if (legal.isEmpty()) return emptyList()
        val white = pos.whiteToMove
        return Engine(3).rankMoves(pos, depth, limitMs, shouldStop)
            .sortedByDescending { it.second }
            .take(count)
            .map { (m, s) -> CoachLine(m, pos.san(m, legal), if (white) s else -s, mateIn(s)) }
    }

    /**
     * Nếu [scoreForMover] (điểm theo góc nhìn bên đi) là điểm chiếu hết thì trả về số nước tới chiếu hết:
     * dương khi bên đi chiếu hết đối phương, âm khi bên đi sắp bị chiếu hết. Không phải chiếu hết thì null.
     */
    fun mateIn(scoreForMover: Int): Int? {
        if (abs(scoreForMover) < Engine.MATE - 200) return null
        val k = Engine.MATE - abs(scoreForMover)
        return if (scoreForMover > 0) (k + 1) / 2 else -maxOf(1, k / 2)
    }

    /** Nhãn điểm ngắn như Lichess: "+1.25", "-0.40", "M3" (Trắng chiếu hết sau 3 nước), "-M2" (Đen chiếu hết sau 2 nước). */
    fun label(cpWhite: Int, mateIn: Int? = null, whiteToMove: Boolean = true): String {
        if (mateIn != null) {
            val whiteMates = (mateIn > 0) == whiteToMove
            return (if (whiteMates) "M" else "-M") + abs(mateIn)
        }
        val v = cpWhite / 100.0
        return String.format(Locale.ROOT, "%+.2f", v)
    }

    /** Ai đang hơn: 0 = cân bằng, 1 = Trắng hơn, -1 = Đen hơn (ngưỡng 0,3 tốt). */
    fun side(cpWhite: Int): Int = when {
        cpWhite >= 30 -> 1
        cpWhite <= -30 -> -1
        else -> 0
    }
}
