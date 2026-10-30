package com.customchess.core

import kotlin.math.abs

/** Mã quân: dương = trắng, âm = đen. */
object Pc {
    const val EMPTY = 0
    const val PAWN = 1
    const val KNIGHT = 2
    const val BISHOP = 3
    const val ROOK = 4
    const val QUEEN = 5
    const val KING = 6

    fun letter(type: Int): Char = when (type) {
        KNIGHT -> 'N'; BISHOP -> 'B'; ROOK -> 'R'; QUEEN -> 'Q'; KING -> 'K'; else -> 'P'
    }

    /** Ký tự FEN của một quân (chữ hoa = trắng). */
    fun fenChar(piece: Int): Char {
        val c = letter(abs(piece))
        return if (piece > 0) c else c.lowercaseChar()
    }

    fun fromFenChar(ch: Char): Int {
        val t = when (ch.uppercaseChar()) {
            'P' -> PAWN; 'N' -> KNIGHT; 'B' -> BISHOP; 'R' -> ROOK; 'Q' -> QUEEN; 'K' -> KING
            else -> return EMPTY
        }
        return if (ch.isUpperCase()) t else -t
    }
}

fun fileOf(sq: Int) = sq and 7
fun rankOf(sq: Int) = sq shr 3
fun squareName(sq: Int): String = "${'a' + fileOf(sq)}${'1' + rankOf(sq)}"
fun squareFromName(s: String): Int {
    if (s.length != 2) return -1
    val f = s[0] - 'a'
    val r = s[1] - '1'
    return if (f in 0..7 && r in 0..7) r * 8 + f else -1
}

data class Move(val from: Int, val to: Int, val promo: Int = 0) {
    fun uci(): String {
        val p = when (promo) {
            Pc.KNIGHT -> "n"; Pc.BISHOP -> "b"; Pc.ROOK -> "r"; Pc.QUEEN -> "q"; else -> ""
        }
        return squareName(from) + squareName(to) + p
    }

    override fun toString() = uci()
}

enum class Reason { CHECKMATE, STALEMATE, INSUFFICIENT_MATERIAL, FIFTY_MOVE, REPETITION, AGREEMENT, RESIGNATION, TIMEOUT, ABANDONED }

/** winnerWhite = null nghĩa là hòa. */
data class GameResult(val winnerWhite: Boolean?, val reason: Reason)

const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

/**
 * Thế cờ bất biến (immutable theo quy ước). Các hàm sinh nước đi chỉ làm việc trên bản sao
 * của bàn nên có thể gọi an toàn từ nhiều luồng.
 */
class Position(
    val board: IntArray,
    val whiteToMove: Boolean,
    /** bit 1 = trắng O-O, 2 = trắng O-O-O, 4 = đen O-O, 8 = đen O-O-O */
    val castling: Int,
    val ep: Int,
    val halfmove: Int,
    val fullmove: Int,
) {
    fun copy() = Position(board.copyOf(), whiteToMove, castling, ep, halfmove, fullmove)

    // ---------------------------------------------------------------- tấn công
    fun isAttacked(sq: Int, byWhite: Boolean): Boolean = isAttacked(board, sq, byWhite)

    fun kingSquare(white: Boolean): Int = kingSquare(board, white)

    fun inCheck(): Boolean {
        val k = kingSquare(whiteToMove)
        return k >= 0 && isAttacked(k, !whiteToMove)
    }

    // ---------------------------------------------------------------- sinh nước đi
    private fun genPseudo(b: IntArray): ArrayList<Move> {
        val out = ArrayList<Move>(48)
        val white = whiteToMove
        for (sq in 0 until 64) {
            val p = b[sq]
            if (p == 0 || (p > 0) != white) continue
            val f = sq and 7
            val r = sq shr 3
            when (abs(p)) {
                Pc.PAWN -> {
                    val dir = if (white) 1 else -1
                    val startRank = if (white) 1 else 6
                    val promoRank = if (white) 7 else 0
                    val nr = r + dir
                    if (nr in 0..7) {
                        val t = nr * 8 + f
                        if (b[t] == 0) {
                            addPawn(out, sq, t, nr == promoRank)
                            if (r == startRank) {
                                val t2 = (r + 2 * dir) * 8 + f
                                if (b[t2] == 0) out.add(Move(sq, t2))
                            }
                        }
                        for (df in -1..1 step 2) {
                            val nf = f + df
                            if (nf !in 0..7) continue
                            val tt = nr * 8 + nf
                            val q = b[tt]
                            if (q != 0 && (q > 0) != white) addPawn(out, sq, tt, nr == promoRank)
                            else if (q == 0 && tt == ep) out.add(Move(sq, tt))
                        }
                    }
                }
                Pc.KNIGHT -> for (i in 0 until 8) {
                    val nf = f + KN_DF[i]
                    val nr = r + KN_DR[i]
                    if (nf !in 0..7 || nr !in 0..7) continue
                    val t = nr * 8 + nf
                    if (b[t] == 0 || (b[t] > 0) != white) out.add(Move(sq, t))
                }
                Pc.BISHOP -> slide(out, b, sq, white, 4, 8)
                Pc.ROOK -> slide(out, b, sq, white, 0, 4)
                Pc.QUEEN -> slide(out, b, sq, white, 0, 8)
                Pc.KING -> {
                    for (i in 0 until 8) {
                        val nf = f + DIR_DF[i]
                        val nr = r + DIR_DR[i]
                        if (nf !in 0..7 || nr !in 0..7) continue
                        val t = nr * 8 + nf
                        if (b[t] == 0 || (b[t] > 0) != white) out.add(Move(sq, t))
                    }
                    genCastling(out, b, white)
                }
            }
        }
        return out
    }

    private fun addPawn(out: ArrayList<Move>, from: Int, to: Int, promo: Boolean) {
        if (promo) {
            out.add(Move(from, to, Pc.QUEEN)); out.add(Move(from, to, Pc.ROOK))
            out.add(Move(from, to, Pc.BISHOP)); out.add(Move(from, to, Pc.KNIGHT))
        } else out.add(Move(from, to))
    }

    private fun slide(out: ArrayList<Move>, b: IntArray, sq: Int, white: Boolean, d0: Int, d1: Int) {
        val f = sq and 7
        val r = sq shr 3
        for (d in d0 until d1) {
            var nf = f + DIR_DF[d]
            var nr = r + DIR_DR[d]
            while (nf in 0..7 && nr in 0..7) {
                val t = nr * 8 + nf
                val q = b[t]
                if (q == 0) out.add(Move(sq, t))
                else {
                    if ((q > 0) != white) out.add(Move(sq, t))
                    break
                }
                nf += DIR_DF[d]; nr += DIR_DR[d]
            }
        }
    }

    private fun genCastling(out: ArrayList<Move>, b: IntArray, white: Boolean) {
        val base = if (white) 0 else 56
        val king = if (white) Pc.KING else -Pc.KING
        val rook = if (white) Pc.ROOK else -Pc.ROOK
        if (b[base + 4] != king) return
        val kBit = if (white) 1 else 4
        val qBit = if (white) 2 else 8
        val enemy = !white
        if (castling and kBit != 0 && b[base + 7] == rook && b[base + 5] == 0 && b[base + 6] == 0 &&
            !isAttacked(b, base + 4, enemy) && !isAttacked(b, base + 5, enemy) && !isAttacked(b, base + 6, enemy)
        ) out.add(Move(base + 4, base + 6))
        if (castling and qBit != 0 && b[base] == rook && b[base + 1] == 0 && b[base + 2] == 0 && b[base + 3] == 0 &&
            !isAttacked(b, base + 4, enemy) && !isAttacked(b, base + 3, enemy) && !isAttacked(b, base + 2, enemy)
        ) out.add(Move(base + 4, base + 2))
    }

    /** Danh sách nước đi hợp lệ. */
    fun legalMoves(): List<Move> {
        val b = board.copyOf()
        val white = whiteToMove
        val ksq = kingSquare(b, white)
        val out = ArrayList<Move>(40)
        for (m in genPseudo(b)) {
            val piece = b[m.from]
            val captured = b[m.to]
            var epSq = -1
            var epPiece = 0
            if (abs(piece) == Pc.PAWN && m.to == ep && captured == 0 && fileOf(m.from) != fileOf(m.to)) {
                epSq = if (white) m.to - 8 else m.to + 8
                epPiece = b[epSq]
                b[epSq] = 0
            }
            b[m.to] = if (m.promo != 0) (if (white) m.promo else -m.promo) else piece
            b[m.from] = 0
            val k = if (abs(piece) == Pc.KING) m.to else ksq
            val ok = k < 0 || !isAttacked(b, k, !white)
            b[m.from] = piece
            b[m.to] = captured
            if (epSq >= 0) b[epSq] = epPiece
            if (ok) out.add(m)
        }
        return out
    }

    fun isCapture(m: Move): Boolean =
        board[m.to] != 0 || (abs(board[m.from]) == Pc.PAWN && fileOf(m.from) != fileOf(m.to))

    // ---------------------------------------------------------------- thực hiện nước đi
    fun apply(m: Move): Position {
        val nb = board.copyOf()
        val piece = nb[m.from]
        val white = piece > 0
        val type = abs(piece)
        var captured = nb[m.to] != 0
        if (type == Pc.PAWN && fileOf(m.from) != fileOf(m.to) && nb[m.to] == 0) {
            nb[if (white) m.to - 8 else m.to + 8] = 0 // bắt tốt qua đường
            captured = true
        }
        nb[m.to] = if (m.promo != 0) (if (white) m.promo else -m.promo) else piece
        nb[m.from] = 0
        if (type == Pc.KING && abs(fileOf(m.to) - fileOf(m.from)) == 2) {
            val base = if (white) 0 else 56
            if (m.to > m.from) { nb[base + 5] = nb[base + 7]; nb[base + 7] = 0 }
            else { nb[base + 3] = nb[base]; nb[base] = 0 }
        }
        val newEp = if (type == Pc.PAWN && abs(m.to - m.from) == 16) (m.from + m.to) / 2 else -1
        var c = castling
        if (type == Pc.KING) c = c and (if (white) 3 else 12).inv()
        c = c and cornerMask(m.from) and cornerMask(m.to)
        return Position(
            nb, !whiteToMove, c, newEp,
            if (type == Pc.PAWN || captured) 0 else halfmove + 1,
            if (white) fullmove else fullmove + 1,
        )
    }

    private fun cornerMask(sq: Int): Int = when (sq) {
        0 -> 2.inv(); 7 -> 1.inv(); 56 -> 8.inv(); 63 -> 4.inv(); else -> -1
    }

    // ---------------------------------------------------------------- kết quả thế cờ
    fun insufficientMaterial(): Boolean {
        var minors = 0
        var bishopColors = 0 // bit0: ô sáng, bit1: ô tối
        var onlyBishops = true
        for (sq in 0 until 64) {
            val t = abs(board[sq])
            if (t == 0 || t == Pc.KING) continue
            if (t == Pc.PAWN || t == Pc.ROOK || t == Pc.QUEEN) return false
            minors++
            if (t == Pc.BISHOP) bishopColors = bishopColors or (if ((fileOf(sq) + rankOf(sq)) % 2 == 0) 2 else 1)
            else onlyBishops = false
        }
        if (minors <= 1) return true
        return onlyBishops && (bishopColors == 1 || bishopColors == 2)
    }

    /** Khóa so sánh lặp thế: bàn + bên đi + quyền nhập thành + ô bắt qua đường (nếu thật sự bắt được). */
    fun repetitionKey(): String {
        val sb = StringBuilder(80)
        for (v in board) sb.append('a' + (v + 6))
        sb.append(if (whiteToMove) 'w' else 'b').append(castling)
        if (ep >= 0) {
            val dir = if (whiteToMove) -1 else 1
            val rr = rankOf(ep) + dir
            val own = if (whiteToMove) Pc.PAWN else -Pc.PAWN
            var can = false
            for (df in -1..1 step 2) {
                val ff = fileOf(ep) + df
                if (ff in 0..7 && rr in 0..7 && board[rr * 8 + ff] == own) can = true
            }
            if (can) sb.append(ep)
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- FEN
    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0..7) {
                val p = board[r * 8 + f]
                if (p == 0) empty++
                else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(Pc.fenChar(p))
                }
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }
        sb.append(if (whiteToMove) " w " else " b ")
        if (castling == 0) sb.append('-')
        else {
            if (castling and 1 != 0) sb.append('K')
            if (castling and 2 != 0) sb.append('Q')
            if (castling and 4 != 0) sb.append('k')
            if (castling and 8 != 0) sb.append('q')
        }
        sb.append(' ').append(if (ep >= 0) squareName(ep) else "-")
        sb.append(' ').append(halfmove).append(' ').append(fullmove)
        return sb.toString()
    }

    // ---------------------------------------------------------------- SAN
    fun san(m: Move, legal: List<Move> = legalMoves()): String {
        val piece = board[m.from]
        val type = abs(piece)
        val capture = isCapture(m)
        val sb = StringBuilder()
        if (type == Pc.KING && abs(fileOf(m.to) - fileOf(m.from)) == 2) {
            sb.append(if (m.to > m.from) "O-O" else "O-O-O")
        } else if (type == Pc.PAWN) {
            if (capture) sb.append('a' + fileOf(m.from)).append('x')
            sb.append(squareName(m.to))
            if (m.promo != 0) sb.append('=').append(Pc.letter(m.promo))
        } else {
            sb.append(Pc.letter(type))
            val others = legal.filter { it != m && abs(board[it.from]) == type && it.to == m.to }
            if (others.isNotEmpty()) {
                val sameFile = others.any { fileOf(it.from) == fileOf(m.from) }
                val sameRank = others.any { rankOf(it.from) == rankOf(m.from) }
                if (!sameFile) sb.append('a' + fileOf(m.from))
                else if (!sameRank) sb.append('1' + rankOf(m.from))
                else sb.append(squareName(m.from))
            }
            if (capture) sb.append('x')
            sb.append(squareName(m.to))
        }
        val next = apply(m)
        if (next.inCheck()) sb.append(if (next.legalMoves().isEmpty()) '#' else '+')
        return sb.toString()
    }

    /**
     * Đọc nước đi từ văn bản: chấp nhận UCI (e2e4, e7e8q) và SAN (Nf3, exd5, e8=Q, O-O, ...).
     * Trả về null nếu không hợp lệ hoặc mơ hồ.
     */
    fun parseMove(text: String, legal: List<Move> = legalMoves()): Move? {
        var core = text.trim().trim('.', ',', ';', ':', '"', '\'', '`', '*').trimEnd('+', '#', '!', '?')
        if (core.isEmpty()) return null

        when (core.replace('0', 'O').replace('o', 'O')) {
            "O-O", "OO" -> return legal.firstOrNull { abs(board[it.from]) == Pc.KING && it.to - it.from == 2 }
            "O-O-O", "OOO" -> return legal.firstOrNull { abs(board[it.from]) == Pc.KING && it.from - it.to == 2 }
        }

        val uci = UCI_RE.matchEntire(core.lowercase())
        if (uci != null) {
            val from = squareFromName(uci.groupValues[1])
            val to = squareFromName(uci.groupValues[2])
            val promo = when (uci.groupValues[3]) {
                "q" -> Pc.QUEEN; "r" -> Pc.ROOK; "b" -> Pc.BISHOP; "n" -> Pc.KNIGHT; else -> 0
            }
            val cands = legal.filter { it.from == from && it.to == to }
            if (cands.isEmpty()) return null
            if (promo != 0) return cands.firstOrNull { it.promo == promo }
            return cands.firstOrNull { it.promo == 0 } ?: cands.firstOrNull { it.promo == Pc.QUEEN }
        }

        // SAN
        core = PROMO_LOWER_RE.replace(core) { it.groupValues[1] + it.groupValues[2].uppercase() }
        val norm = core.replace("x", "").replace("X", "").replace("=", "").replace("-", "").replace("_", "")
        val m = SAN_RE.matchEntire(norm) ?: return null
        val type = if (m.groupValues[1].isEmpty()) Pc.PAWN else when (m.groupValues[1][0]) {
            'K' -> Pc.KING; 'Q' -> Pc.QUEEN; 'R' -> Pc.ROOK; 'B' -> Pc.BISHOP; else -> Pc.KNIGHT
        }
        val fileC = m.groupValues[2]
        val rankC = m.groupValues[3]
        val to = squareFromName(m.groupValues[4])
        val promo = when (m.groupValues[5]) {
            "Q" -> Pc.QUEEN; "R" -> Pc.ROOK; "B" -> Pc.BISHOP; "N" -> Pc.KNIGHT; else -> 0
        }
        val cands = legal.filter {
            abs(board[it.from]) == type && it.to == to &&
                (fileC.isEmpty() || fileOf(it.from) == fileC[0] - 'a') &&
                (rankC.isEmpty() || rankOf(it.from) == rankC[0] - '1') &&
                (promo == 0 || it.promo == promo)
        }
        val filtered = if (promo == 0 && cands.any { it.promo != 0 }) cands.filter { it.promo == Pc.QUEEN } else cands
        return if (filtered.size == 1) filtered[0] else null
    }

    companion object {
        private val DIR_DF = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1) // 0..3 thẳng, 4..7 chéo
        private val DIR_DR = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)
        private val KN_DF = intArrayOf(1, 2, 2, 1, -1, -2, -2, -1)
        private val KN_DR = intArrayOf(2, 1, -1, -2, -2, -1, 1, 2)
        private val UCI_RE = Regex("^([a-h][1-8])([a-h][1-8])([qrbn])?$")
        private val PROMO_LOWER_RE = Regex("^(.*[18])=?([qrbn])$")
        private val SAN_RE = Regex("^([KQRBN])?([a-h])?([1-8])?([a-h][1-8])([QRBN])?$")

        fun start(): Position = fromFen(START_FEN)!!

        fun kingSquare(b: IntArray, white: Boolean): Int {
            val k = if (white) Pc.KING else -Pc.KING
            for (i in 0 until 64) if (b[i] == k) return i
            return -1
        }

        fun isAttacked(b: IntArray, sq: Int, byWhite: Boolean): Boolean {
            val f = sq and 7
            val r = sq shr 3
            val c = if (byWhite) 1 else -1
            val pr = if (byWhite) r - 1 else r + 1
            if (pr in 0..7) {
                if (f > 0 && b[pr * 8 + f - 1] == c * Pc.PAWN) return true
                if (f < 7 && b[pr * 8 + f + 1] == c * Pc.PAWN) return true
            }
            for (i in 0 until 8) {
                val nf = f + KN_DF[i]
                val nr = r + KN_DR[i]
                if (nf in 0..7 && nr in 0..7 && b[nr * 8 + nf] == c * Pc.KNIGHT) return true
                val kf = f + DIR_DF[i]
                val kr = r + DIR_DR[i]
                if (kf in 0..7 && kr in 0..7 && b[kr * 8 + kf] == c * Pc.KING) return true
            }
            for (d in 0 until 8) {
                var nf = f + DIR_DF[d]
                var nr = r + DIR_DR[d]
                while (nf in 0..7 && nr in 0..7) {
                    val q = b[nr * 8 + nf]
                    if (q != 0) {
                        if ((q > 0) == byWhite) {
                            val t = abs(q)
                            if (t == Pc.QUEEN || (d < 4 && t == Pc.ROOK) || (d >= 4 && t == Pc.BISHOP)) return true
                        }
                        break
                    }
                    nf += DIR_DF[d]; nr += DIR_DR[d]
                }
            }
            return false
        }

        fun fromFen(fen: String): Position? {
            val parts = fen.trim().split(Regex("\\s+"))
            if (parts.size < 2) return null
            val rows = parts[0].split('/')
            if (rows.size != 8) return null
            val b = IntArray(64)
            for ((i, row) in rows.withIndex()) {
                val r = 7 - i
                var f = 0
                for (ch in row) {
                    if (ch.isDigit()) f += ch - '0'
                    else {
                        val p = Pc.fromFenChar(ch)
                        if (p == 0 || f > 7) return null
                        b[r * 8 + f] = p
                        f++
                    }
                }
                if (f != 8) return null
            }
            val white = when (parts[1]) { "w" -> true; "b" -> false; else -> return null }
            var c = 0
            val cs = parts.getOrElse(2) { "-" }
            if ('K' in cs) c = c or 1
            if ('Q' in cs) c = c or 2
            if ('k' in cs) c = c or 4
            if ('q' in cs) c = c or 8
            val ep = parts.getOrElse(3) { "-" }.let { if (it == "-") -1 else squareFromName(it) }
            val hm = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val fm = parts.getOrNull(5)?.toIntOrNull() ?: 1
            if (kingSquare(b, true) < 0 || kingSquare(b, false) < 0) return null
            return Position(b, white, c, ep, hm, fm)
        }
    }
}

/** Một ván cờ: lịch sử thế cờ, nước đi, SAN, kết quả (kể cả đầu hàng/hòa/hết giờ do bên ngoài đặt). */
class Game(val startFen: String = START_FEN) {
    private val positions = ArrayList<Position>()
    val moves = ArrayList<Move>()
    val sans = ArrayList<String>()
    var forcedResult: GameResult? = null

    init {
        positions.add(Position.fromFen(startFen) ?: Position.start())
    }

    val position: Position get() = positions.last()
    val positionCount: Int get() = positions.size
    fun positionAt(i: Int): Position = positions[i]
    val lastMove: Move? get() = moves.lastOrNull()

    fun legalMoves(): List<Move> = if (result() != null) emptyList() else position.legalMoves()

    /** Đi một nước; trả về false nếu không hợp lệ hoặc ván đã kết thúc. */
    fun play(m: Move): Boolean {
        if (result() != null) return false
        val legal = position.legalMoves()
        if (m !in legal) return false
        sans.add(position.san(m, legal))
        moves.add(m)
        positions.add(position.apply(m))
        return true
    }

    fun undo(): Boolean {
        if (moves.isEmpty()) return false
        moves.removeAt(moves.size - 1)
        sans.removeAt(sans.size - 1)
        positions.removeAt(positions.size - 1)
        forcedResult = null
        return true
    }

    fun repetitionCount(): Int {
        val key = position.repetitionKey()
        var n = 0
        for (p in positions) if (p.repetitionKey() == key) n++
        return n
    }

    /** Số lần mỗi thế đã xuất hiện (dùng để engine tránh lặp thế khi đang hơn). */
    fun repetitionMap(): Map<String, Int> {
        val map = HashMap<String, Int>()
        for (p in positions) map.merge(p.repetitionKey(), 1, Int::plus)
        return map
    }

    fun result(): GameResult? {
        forcedResult?.let { return it }
        val p = position
        if (p.legalMoves().isEmpty()) {
            return if (p.inCheck()) GameResult(!p.whiteToMove, Reason.CHECKMATE)
            else GameResult(null, Reason.STALEMATE)
        }
        if (p.insufficientMaterial()) return GameResult(null, Reason.INSUFFICIENT_MATERIAL)
        if (p.halfmove >= 100) return GameResult(null, Reason.FIFTY_MOVE)
        if (repetitionCount() >= 3) return GameResult(null, Reason.REPETITION)
        return null
    }

    /** Lịch sử dạng "1. e4 e5 2. Nf3 ...". */
    fun movetext(): String {
        val first = Position.fromFen(startFen) ?: Position.start()
        val sb = StringBuilder()
        var num = first.fullmove
        var white = first.whiteToMove
        for ((i, s) in sans.withIndex()) {
            if (white) sb.append(num).append(". ")
            else if (i == 0) sb.append(num).append("... ")
            sb.append(s).append(' ')
            if (!white) num++
            white = !white
        }
        return sb.toString().trim()
    }
}
