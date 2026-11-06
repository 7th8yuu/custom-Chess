package com.customchess.core

/** Một ván đọc từ văn bản PGN. [moves] là nước đi dạng UCI (e2e4, e7e8q) để lưu cùng định dạng với ván chơi trong ứng dụng. */
class PgnGame(
    val white: String,
    val black: String,
    val whiteElo: String,
    val blackElo: String,
    val startFen: String,
    val moves: List<String>,
    /** "1-0", "0-1", "1/2-1/2" hoặc "*" (chưa rõ). */
    val result: String,
    /** Giá trị thẻ Termination nếu có (ví dụ "Trắng thắng do hết giờ"), có thể rỗng. */
    val termination: String,
    val finalPosition: Position,
) {
    /** true = Trắng thắng, false = Đen thắng, null = hòa hoặc chưa rõ. */
    val winnerWhite: Boolean? get() = when (result) { "1-0" -> true; "0-1" -> false; else -> null }

    /** Ván đã có kết quả (kể cả hòa) hay chưa. */
    val finished: Boolean get() = result != "*"
}

class PgnResult(val game: PgnGame?, val error: String?)

/**
 * Đọc PGN (chess.com, Lichess, tệp .pgn...): bỏ chú thích {..} và ;.., biến thể (..), NAG ($1), số nước đi.
 * Chỉ lấy ván đầu tiên; không hỗ trợ biến thể cờ khác (Chess960...).
 */
object Pgn {
    private val TAG = Regex("""\[\s*([A-Za-z0-9_]+)\s+"((?:[^"\\]|\\.)*)"\s*]""")
    private val BRACE_COMMENT = Regex("""\{[^}]*}""")
    private val LINE_COMMENT = Regex(""";[^\n]*""")
    private val NAG = Regex("""\$\d+""")
    private val NUMBER = Regex("""^\d+\.+""")
    private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")

    private fun fail(msg: String) = PgnResult(null, msg)

    private fun stripVariations(s: String): String {
        val sb = StringBuilder()
        var depth = 0
        for (c in s) {
            when {
                c == '(' -> depth++
                c == ')' -> if (depth > 0) depth--
                depth == 0 -> sb.append(c)
            }
        }
        return sb.toString()
    }

    fun parse(raw: String): PgnResult {
        val text = raw.replace("﻿", "").replace("\r\n", "\n").replace('\r', '\n').replace('…', '.').trim()
        if (text.isEmpty()) return fail("Chưa có nội dung. Hãy dán PGN của ván cờ.")
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return fail("Đây là đường dẫn, không phải PGN. Trên chess.com bấm Chia sẻ, chọn tab PGN rồi sao chép nội dung.")
        }

        val tags = LinkedHashMap<String, String>()
        for (m in TAG.findAll(text)) tags.putIfAbsent(m.groupValues[1], m.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\"))

        val variant = tags["Variant"].orEmpty().trim()
        if (variant.isNotEmpty() && !variant.equals("Standard", true) && !variant.equals("From Position", true)) {
            return fail("Chưa hỗ trợ biến thể cờ \"$variant\". Chỉ xem lại được cờ vua tiêu chuẩn.")
        }
        val startFen = tags["FEN"]?.takeIf { it.isNotBlank() } ?: START_FEN
        var pos = Position.fromFen(startFen) ?: return fail("Thẻ FEN của PGN không hợp lệ.")

        var body = TAG.replace(text, " ")
        body = BRACE_COMMENT.replace(body, " ")
        body = LINE_COMMENT.replace(body, " ")
        body = stripVariations(body)
        body = NAG.replace(body, " ")

        val moves = ArrayList<String>()
        var result: String? = null
        for (token in body.split(Regex("\\s+"))) {
            if (token.isEmpty()) continue
            if (token in RESULTS) { result = token; break }
            val t = token.replace(NUMBER, "")
            if (t.isEmpty() || t.all { it == '.' }) continue
            val m = pos.parseMove(t)
                ?: return fail("Không đọc được nước thứ ${moves.size + 1} (\"$token\"). PGN có thể bị cắt, sai, hoặc là biến thể cờ khác.")
            moves.add(m.uci())
            pos = pos.apply(m)
        }
        if (moves.isEmpty()) return fail("Không thấy nước đi nào trong nội dung đã dán. Hãy chắc chắn đó là PGN (có các nước như 1. e4 e5 2. Nf3...).")

        val res = result ?: tags["Result"]?.takeIf { it in RESULTS } ?: "*"
        return PgnResult(
            PgnGame(
                white = tags["White"].orEmpty().trim(), black = tags["Black"].orEmpty().trim(),
                whiteElo = tags["WhiteElo"].orEmpty().trim().takeIf { it != "?" }.orEmpty(),
                blackElo = tags["BlackElo"].orEmpty().trim().takeIf { it != "?" }.orEmpty(),
                startFen = startFen, moves = moves, result = res, termination = tags["Termination"].orEmpty().trim(),
                finalPosition = pos,
            ),
            null,
        )
    }

    /** Suy ra lý do kết thúc (tên của [Reason]) từ thế cuối, kết quả và thẻ Termination. */
    fun reasonOf(g: PgnGame): Reason {
        val t = g.termination.lowercase()
        val p = g.finalPosition
        return when {
            p.legalMoves().isEmpty() && p.inCheck() -> Reason.CHECKMATE
            p.legalMoves().isEmpty() -> Reason.STALEMATE
            "time" in t -> Reason.TIMEOUT
            "resign" in t -> Reason.RESIGNATION
            "abandon" in t -> Reason.ABANDONED
            "agree" in t || "draw" in t -> Reason.AGREEMENT
            "repetition" in t -> Reason.REPETITION
            "insufficient" in t -> Reason.INSUFFICIENT_MATERIAL
            g.winnerWhite != null -> Reason.RESIGNATION
            else -> Reason.AGREEMENT
        }
    }
}
