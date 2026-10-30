package com.customchess.core

/** Tìm nước đi hợp lệ trong câu trả lời tự do của AI. */
object MoveExtract {
    private val SPLIT = Regex("[\\s,;:()\\[\\]{}\"'`*]+")

    /** Ưu tiên UCI, lấy nước được nhắc tới cuối cùng; nếu không có thì thử SAN. */
    fun fromReply(pos: Position, legal: List<Move>, text: String): Move? {
        val tokens = text.split(SPLIT)
            .map { it.trim('.', '!', '?') }
            .filter { it.isNotEmpty() }
        val byUci = legal.associateBy { it.uci() }
        for (t in tokens.asReversed()) byUci[t.lowercase()]?.let { return it }
        for (t in tokens.asReversed()) pos.parseMove(t, legal)?.let { return it }
        return null
    }
}
