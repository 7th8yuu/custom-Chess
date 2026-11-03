package com.customchess.ai

import com.customchess.core.Coach
import com.customchess.core.CoachLine
import com.customchess.core.Position

/** Soạn lời nhắc cho AI giải thích thế cờ. Tách riêng, thuần Kotlin, để kiểm thử được. */
object CoachPrompt {
    fun system(answerLanguage: String): String =
        "You are a friendly, concise chess coach. You receive a position (FEN), the moves played so far and the engine's " +
            "best candidate moves with evaluations (in pawns, from White's point of view; M3 means White mates in 3, -M3 means Black mates in 3). " +
            "The engine lines are reliable: never contradict them and never invent a move that is not in the list. " +
            "Explain in $answerLanguage, in at most 6 short sentences and plain text without markdown or headings: " +
            "who is better and why (material, king safety, piece activity, pawn structure), what the best move aims to do, " +
            "and one practical tip for the side to move. Write chess moves exactly as given (SAN)."

    fun user(pos: Position, movetext: String, lines: List<CoachLine>, lastMoveSan: String?): String = buildString {
        val side = if (pos.whiteToMove) "White" else "Black"
        append("Side to move: $side\n")
        append("FEN: ${pos.toFen()}\n")
        append("Moves so far: ${movetext.ifEmpty { "(none)" }}\n")
        if (lastMoveSan != null) append("Last move played: $lastMoveSan\n")
        append("Engine candidate moves:\n")
        for ((i, l) in lines.withIndex()) {
            append("${i + 1}. ${l.san} (eval ${Coach.label(l.cpWhite, l.mateIn, pos.whiteToMove)})\n")
        }
        append("Explain the position and the best plan.")
    }
}
