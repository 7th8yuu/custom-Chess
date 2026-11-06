package com.customchess

import com.customchess.core.Coach
import com.customchess.core.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachTest {
    @Test fun findsMateInOneForWhite() {
        // Trắng: Qh5 -> Qxf7# (thế kinh điển, Trắng đi, Qxf7 chiếu hết)
        val pos = Position.fromFen("r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4")!!
        val lines = Coach.topMoves(pos, count = 3, depth = 3, limitMs = 4000)
        assertTrue(lines.isNotEmpty())
        val top = lines[0]
        assertEquals("Qxf7#", top.san)
        assertEquals(1, top.mateIn)
        assertEquals("M1", Coach.label(top.cpWhite, top.mateIn, whiteToMove = true))
        assertTrue(top.cpWhite > 50_000)
    }

    @Test fun blackMateLabelIsSignedFromWhitePerspective() {
        // Đen có nước chiếu hết ngay (Qh4#): nhãn phải là "-M1" theo góc nhìn quân trắng.
        val pos = Position.fromFen("rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2")!!
        val top = Coach.topMoves(pos, count = 1, depth = 3, limitMs = 4000).first()
        assertEquals("Qh4#", top.san)
        assertEquals("-M1", Coach.label(top.cpWhite, top.mateIn, whiteToMove = false))
        assertTrue(top.cpWhite < -50_000)
    }

    @Test fun noMovesInFinishedPosition() {
        val mated = Position.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")!!
        assertTrue(Coach.topMoves(mated).isEmpty())
    }

    @Test fun mateInArithmetic() {
        assertEquals(1, Coach.mateIn(100_000 - 1))
        assertEquals(2, Coach.mateIn(100_000 - 3))
        assertEquals(-1, Coach.mateIn(-(100_000 - 2)))
        assertEquals(-2, Coach.mateIn(-(100_000 - 4)))
        assertEquals(null, Coach.mateIn(350))
    }

    @Test fun labelsAndSides() {
        assertEquals("+1.25", Coach.label(125))
        assertEquals("-0.40", Coach.label(-40))
        assertEquals("+0.00", Coach.label(0))
        assertEquals(1, Coach.side(80))
        assertEquals(-1, Coach.side(-80))
        assertEquals(0, Coach.side(10))
        assertNotNull(Coach.label(0))
    }
}
