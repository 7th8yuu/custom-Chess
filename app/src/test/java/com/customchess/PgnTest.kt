package com.customchess

import com.customchess.core.Pgn
import com.customchess.core.Reason
import com.customchess.core.START_FEN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PgnTest {
    // Ván thật từ chess.com (có thẻ, số nước đi cho cả hai bên và chú thích giờ [%clk]).
    private val chessCom = """
        [Event "Live Chess"]
        [Site "Chess.com"]
        [Date "2026.11.06"]
        [Round "-"]
        [White "bandit"]
        [Black "loc4146"]
        [Result "0-1"]
        [WhiteElo "190"]
        [BlackElo "181"]
        [TimeControl "600"]
        [Termination "loc4146 won by checkmate"]

        1. d4 {[%clk 0:09:55.8]} 1... d5 {[%clk 0:09:54.2]} 2. Nf3 {[%clk 0:09:57.7]} 2... Nc6 {[%clk 0:09:56.3]}
        3. Nc3 {[%clk 0:09:56.2]} 3... Na5 {[%clk 0:09:50.7]} 4. Nxd5 {[%clk 0:09:52.3]} 4... Qxd5 {[%clk 0:09:47.5]}
        5. Ne5 {[%clk 0:09:49.2]} 5... Qd6 {[%clk 0:09:31.1]} 6. Nf3 {[%clk 0:09:39.2]} 6... Nc4 {[%clk 0:09:24.9]}
        7. g3 {[%clk 0:09:14.1]} 7... Nxb2 {[%clk 0:09:23.2]} 8. Bxb2 {[%clk 0:09:12.4]} 8... Qb6 {[%clk 0:09:09.1]}
        9. Ba3 {[%clk 0:09:06.2]} 9... f5 {[%clk 0:08:54.2]} 10. h4 {[%clk 0:08:57.8]} 10... Qa5+ {[%clk 0:08:45.8]}
        11. c3 {[%clk 0:08:50.4]} 11... Qxc3+ {[%clk 0:08:31.4]} 12. Nd2 {[%clk 0:08:41.0]} 12... Qxa3 {[%clk 0:08:24.5]}
        13. Bh3 {[%clk 0:08:36.5]} 13... Qb4 {[%clk 0:08:19.9]} 14. O-O {[%clk 0:08:30.4]} 14... Qxd4 {[%clk 0:08:13.8]}
        15. Bxf5 {[%clk 0:08:24.3]} 15... Bxf5 {[%clk 0:08:07.7]} 16. Rc1 {[%clk 0:08:16.4]} 16... Bh3 {[%clk 0:08:00.8]}
        17. Re1 {[%clk 0:08:05.8]} 17... Qd5 {[%clk 0:07:49.0]} 18. Kh2 {[%clk 0:07:55.5]} 18... Qg2# {[%clk 0:07:46.4]} 0-1
    """.trimIndent()

    @Test fun readsAChessComGame() {
        val r = Pgn.parse(chessCom)
        assertNull(r.error)
        val g = r.game!!
        assertEquals(36, g.moves.size)
        assertEquals("d2d4", g.moves.first())
        assertEquals("bandit", g.white)
        assertEquals("loc4146", g.black)
        assertEquals("190", g.whiteElo)
        assertEquals("181", g.blackElo)
        assertEquals("0-1", g.result)
        assertEquals(false, g.winnerWhite)
        assertTrue(g.finished)
        assertEquals(START_FEN, g.startFen)
        assertTrue(g.finalPosition.inCheck())
        assertTrue(g.finalPosition.legalMoves().isEmpty())
        assertEquals(Reason.CHECKMATE, Pgn.reasonOf(g))
    }

    @Test fun ignoresVariationsNagsAndAnnotations() {
        val r = Pgn.parse("1. e4! e5 2. Nf3 (2. f4 exf4 3. Nf3) 2... Nc6 \$1 3. Bb5 ; ghi chú cuối dòng\n*")
        assertNull(r.error)
        val g = r.game!!
        assertEquals(listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5"), g.moves)
        assertEquals("*", g.result)
        assertFalse(g.finished)
        assertNull(g.winnerWhite)
    }

    @Test fun readsLichessStyleNumbersAndTermination() {
        val r = Pgn.parse(
            "[White \"a\"]\n[Black \"b\"]\n[Result \"1-0\"]\n[Termination \"Time forfeit\"]\n\n" +
                "1. e4 e5 2. Nf3 Nc6 3. Bc4 Nf6 4. Ng5 d5 5. exd5 Nxd5 1-0",
        )
        val g = r.game!!
        assertEquals(10, g.moves.size)
        assertEquals(true, g.winnerWhite)
        assertEquals(Reason.TIMEOUT, Pgn.reasonOf(g))
    }

    @Test fun unicodeEllipsisAndNoSpaceAfterNumber() {
        val g = Pgn.parse("1.e4 1…e5 2.Nf3 2…Nc6").game!!
        assertEquals(listOf("e2e4", "e7e5", "g1f3", "b8c6"), g.moves)
    }

    @Test fun usesFenTag() {
        val fen = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"
        val g = Pgn.parse("[SetUp \"1\"]\n[FEN \"$fen\"]\n\n1. e4 Kd7 2. e5 *").game!!
        assertEquals(fen, g.startFen)
        assertEquals(listOf("e2e4", "e8d7", "e4e5"), g.moves)
    }

    @Test fun reportsTheMoveThatCannotBeRead() {
        val r = Pgn.parse("1. e4 e5 2. Nf9 Nc6")
        assertNull(r.game)
        assertNotNull(r.error)
        assertTrue(r.error!!, r.error!!.contains("thứ 3"))
    }

    @Test fun rejectsLinksEmptyAndOtherVariants() {
        assertTrue(Pgn.parse("https://www.chess.com/game/live/123").error!!.contains("đường dẫn"))
        assertNotNull(Pgn.parse("   ").error)
        assertNotNull(Pgn.parse("[Variant \"Chess960\"]\n\n1. e4 e5").error)
        assertNotNull(Pgn.parse("[White \"a\"]\n[Black \"b\"]\n\n*").error)
    }

    @Test fun onlyTheFirstGameIsRead() {
        val g = Pgn.parse("[White \"a\"]\n\n1. e4 e5 1/2-1/2\n\n[White \"b\"]\n\n1. d4 d5 2. c4 0-1").game!!
        assertEquals(listOf("e2e4", "e7e5"), g.moves)
        assertEquals("a", g.white)
        assertEquals("1/2-1/2", g.result)
        assertEquals(Reason.AGREEMENT, Pgn.reasonOf(g))
    }
}
