package com.customchess

import com.customchess.core.Engine
import com.customchess.core.Game
import com.customchess.core.Pc
import com.customchess.core.Position
import com.customchess.core.Reason
import com.customchess.core.START_FEN
import com.customchess.core.squareFromName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessRulesTest {

    private fun perft(p: Position, d: Int): Long {
        if (d == 0) return 1
        val ms = p.legalMoves()
        if (d == 1) return ms.size.toLong()
        var n = 0L
        for (m in ms) n += perft(p.apply(m), d - 1)
        return n
    }

    private fun check(fen: String, vararg expected: Long) {
        val p = Position.fromFen(fen)!!
        for ((i, e) in expected.withIndex()) assertEquals("perft depth ${i + 1} of $fen", e, perft(p, i + 1))
    }

    @Test fun perftStart() = check(START_FEN, 20, 400, 8902)
    @Test fun perftKiwipete() = check("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 48, 2039)
    @Test fun perftEndgame() = check("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2812)
    @Test fun perftPromotions() = check("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 6, 264)
    @Test fun perftCastlingAndCheck() = check("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 44, 1486)

    @Test fun fenRoundTrip() {
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        assertEquals(fen, Position.fromFen(fen)!!.toFen())
    }

    @Test fun parsesSanAndUci() {
        val g = Game()
        for (t in listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "O-O", "Be7")) {
            val m = g.position.parseMove(t)
            assertNotNull("parse $t", m)
            assertTrue(g.play(m!!))
        }
        assertEquals("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7", g.movetext())
        assertEquals("f1e1", g.position.parseMove("f1e1")?.uci())
        assertNull(g.position.parseMove("e4e5"))
        assertNull(g.position.parseMove("hello"))
    }

    @Test fun foolsMate() {
        val g = Game()
        for (u in listOf("f2f3", "e7e5", "g2g4", "d8h4")) assertTrue(g.play(g.position.parseMove(u)!!))
        assertEquals(Reason.CHECKMATE, g.result()?.reason)
        assertEquals(false, g.result()?.winnerWhite)
        assertEquals("Qh4#", g.sans.last())
    }

    @Test fun promotionForms() {
        val p = Position.fromFen("8/P6k/8/8/8/8/8/K7 w - - 0 1")!!
        assertEquals("a7a8q", p.parseMove("a8=Q")?.uci())
        assertEquals("a7a8q", p.parseMove("a8Q")?.uci())
        assertEquals("a7a8n", p.parseMove("a8n")?.uci())
        assertEquals("a7a8r", p.parseMove("a7a8r")?.uci())
        assertEquals("a7a8q", p.parseMove("a8")?.uci())
    }

    @Test fun enPassant() {
        val p = Position.fromFen("rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")!!
        val m = p.parseMove("dxe3")
        assertNotNull(m)
        assertEquals(0, p.apply(m!!).board[squareFromName("e4")])
    }

    @Test fun drawsByRule() {
        assertTrue(Position.fromFen("8/8/8/4k3/8/8/8/4K3 w - - 0 1")!!.insufficientMaterial())
        assertTrue(Position.fromFen("8/8/8/4k3/8/8/8/4KB2 w - - 0 1")!!.insufficientMaterial())
        assertFalse(Position.fromFen("8/8/8/4k3/8/8/4P3/4K3 w - - 0 1")!!.insufficientMaterial())
        assertEquals(Reason.STALEMATE, Game("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1").result()?.reason)
        val g = Game()
        for (u in listOf("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")) g.play(g.position.parseMove(u)!!)
        assertEquals(Reason.REPETITION, g.result()?.reason)
    }

    @Test fun operaGameEndsInMate() {
        val moves = ("e2e4 e7e5 g1f3 d7d6 d2d4 c8g4 d4e5 g4f3 d1f3 d6e5 f1c4 g8f6 f3b3 d8e7 b1c3 c7c6 " +
            "c1g5 b7b5 c3b5 c6b5 c4b5 b8d7 e1c1 a8d8 d1d7 d8d7 h1d1 e7e6 b5d7 f6d7 b3b8 d7b8 d1d8").split(' ')
        val g = Game()
        for (u in moves) assertTrue("play $u", g.play(g.position.parseMove(u)!!))
        assertEquals(Reason.CHECKMATE, g.result()?.reason)
        assertEquals(true, g.result()?.winnerWhite)
    }

    @Test fun engineFindsMateInOne() {
        val p = Position.fromFen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1")!!
        assertEquals("a1a8", Engine(3).bestMove(p)?.uci())
    }

    @Test fun engineDoesNotHangQueenForFree() {
        // Trắng có thể ăn hậu đen miễn phí bằng xe.
        val p = Position.fromFen("3q2k1/8/8/8/8/8/8/3RK3 w - - 0 1")!!
        assertEquals("d1d8", Engine(3).bestMove(p)?.uci())
    }

    @Test fun evaluationFavoursExtraQueen() {
        val up = Position.fromFen("4k3/8/8/8/8/8/8/3QK3 w - - 0 1")!!
        assertTrue(Engine.evaluate(up) > 500)
        assertEquals(Pc.QUEEN, 5)
    }
}
