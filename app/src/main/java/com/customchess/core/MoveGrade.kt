package com.customchess.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** Các mức đánh giá một nước đi khi xem lại ván. Thứ tự: tốt nhất tới tệ nhất. */
enum class MoveClass(val label: String, val mark: String) {
    BRILLIANT("Nước xuất sắc", "!!"),
    GREAT("Nước rất hay", "!"),
    BEST("Nước tốt nhất", "★"),
    GOOD("Nước tốt", ""),
    INACCURACY("Thiếu chính xác", "?!"),
    MISTAKE("Sai lầm", "?"),
    BLUNDER("Sai lầm nghiêm trọng", "??"),
}

/**
 * Chấm một nước đi từ số liệu của engine nội bộ. Thuần Kotlin để kiểm thử được trên máy tính.
 *
 * Điểm đều là centipawn theo góc nhìn quân trắng. Vì engine chạy ở độ sâu thấp, hai nhãn "xuất sắc" và "rất hay"
 * được đặt điều kiện chặt: thà bỏ sót còn hơn gắn nhầm.
 */
object MoveGrade {
    /** Giá trị quân tính bằng centipawn (tốt, mã, tượng, xe, hậu); vua không tính. */
    private val VALUE = intArrayOf(0, 100, 300, 300, 500, 900, 0)

    /** Phải bỏ ít nhất chừng này (một quân nhẹ đổi lấy một tốt) mới coi là "hy sinh". */
    const val SACRIFICE_MIN_CP = 200

    /** Khoảng cách phần trăm thắng giữa nước tốt nhất và nước tốt thứ hai để coi là "nước duy nhất". */
    const val ONLY_MOVE_GAP = 15.0

    /** Xác suất thắng (0-100) của bên có điểm [cp] (centipawn theo góc nhìn bên đó), công thức của Lichess. */
    fun winPct(cp: Int): Double {
        val c = cp.coerceIn(-1000, 1000)
        return 50.0 + 50.0 * (2.0 / (1.0 + exp(-0.00368208 * c)) - 1.0)
    }

    /** Tổng giá trị quân (không tính vua) của một bên. */
    fun material(pos: Position, white: Boolean): Int {
        var s = 0
        for (p in pos.board) if (p != 0 && (p > 0) == white) s += VALUE[abs(p)]
        return s
    }

    /**
     * Bên đi [move] mất thêm bao nhiêu vật chất (centipawn, so với trước nước đi) nếu đối phương đáp lại bằng [reply].
     * Dương là hy sinh; ăn quân hoặc đổi quân ngang giá cho 0 hoặc âm.
     */
    fun sacrificeCp(before: Position, move: Move, reply: Move?): Int {
        val white = before.whiteToMove
        val b = material(before, white) - material(before, !white)
        val p1 = before.apply(move)
        val p2 = if (reply != null && reply in p1.legalMoves()) p1.apply(reply) else p1
        val a = material(p2, white) - material(p2, !white)
        return b - a
    }

    private fun moverCp(whiteCp: Int, white: Boolean) = if (white) whiteCp else -whiteCp

    /** Phần trăm thắng mà bên đi nước này đã đánh mất (0 nếu không mất). */
    fun lossPct(whiteToMove: Boolean, evalBefore: Int, evalAfter: Int): Double {
        val wb = winPct(moverCp(evalBefore, whiteToMove))
        val wa = winPct(moverCp(evalAfter, whiteToMove))
        return max(0.0, wb - wa)
    }

    /**
     * Có nên tốn thêm thời gian tìm nước tốt thứ hai ở thế này (để xét "nước rất hay") không.
     * Chỉ khi người chơi đã đi đúng nước engine chọn, thế chưa ngã ngũ, không phải ăn lại quân hay buộc phải đáp chiếu.
     */
    fun worthAlternativeCheck(before: Position, move: Move, prevMove: Move?, evalBefore: Int, best: Move?): Boolean {
        if (best == null || best != move) return false
        val legal = before.legalMoves()
        if (legal.size < 3) return false
        if (before.inCheck() && legal.size <= 4) return false
        if (prevMove != null && prevMove.to == move.to && before.isCapture(move)) return false // ăn lại: gần như bắt buộc
        val w = winPct(moverCp(evalBefore, before.whiteToMove))
        return w in 12.0..92.0
    }

    /**
     * Đánh giá nước [move] đi từ thế [before].
     * [evalBefore]/[evalAfter]: điểm engine của thế trước và sau nước đi; [best]: nước engine chọn ở thế trước;
     * [reply]: nước engine cho là đối phương đáp tốt nhất ở thế sau; [altMoverCp]: điểm (góc nhìn bên đi) của nước
     * tốt thứ hai ở thế trước, null nếu chưa tính.
     */
    fun classify(
        before: Position,
        move: Move,
        prevMove: Move?,
        evalBefore: Int,
        evalAfter: Int,
        best: Move?,
        reply: Move?,
        altMoverCp: Int?,
    ): MoveClass {
        val white = before.whiteToMove
        val loss = lossPct(white, evalBefore, evalAfter)
        val isBest = best != null && best == move
        val wBefore = winPct(moverCp(evalBefore, white))
        val wAfter = winPct(moverCp(evalAfter, white))

        if (isBest || loss < 3.0) {
            // xuất sắc: hy sinh quân thật sự mà vẫn giữ được thế, khi chưa thắng chắc
            if (wBefore <= 85.0 && wAfter >= 40.0 && before.legalMoves().size > 1 &&
                sacrificeCp(before, move, reply) >= SACRIFICE_MIN_CP
            ) return MoveClass.BRILLIANT
        }
        if (isBest) {
            // rất hay: nước duy nhất giữ được thế; ăn không một quân hớ hênh thì chỉ là "tốt nhất"
            if (altMoverCp != null && worthAlternativeCheck(before, move, prevMove, evalBefore, best) &&
                sacrificeCp(before, move, reply) > -SACRIFICE_MIN_CP
            ) {
                val wBest = winPct(moverCp(evalBefore, white))
                val wAlt = winPct(altMoverCp)
                if (wBest - wAlt >= ONLY_MOVE_GAP) return MoveClass.GREAT
            }
            return MoveClass.BEST
        }
        return when {
            loss < 10 -> MoveClass.GOOD
            loss < 20 -> MoveClass.INACCURACY
            loss < 30 -> MoveClass.MISTAKE
            else -> MoveClass.BLUNDER
        }
    }

    /** Điểm (góc nhìn bên đi) của nước tốt thứ hai trong danh sách (nước, điểm bên đi); null nếu không có. */
    fun secondBest(ranked: List<Pair<Move, Int>>, best: Move): Int? =
        ranked.filter { it.first != best }.maxOfOrNull { it.second }
}
