package com.customchess

import com.customchess.core.Engine
import com.customchess.core.Move
import com.customchess.core.MoveClass
import com.customchess.core.MoveGrade
import com.customchess.core.Pgn
import com.customchess.core.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MoveGradeTest {
    // Ván thật từ chess.com của người dùng (Đen thắng bằng Qg2#); nước 7...Nxb2 là nước hy sinh Mã đúng.
    private val game = "d4 d5 Nf3 Nc6 Nc3 Na5 Nxd5 Qxd5 Ne5 Qd6 Nf3 Nc4 g3 Nxb2 Bxb2 Qb6 Ba3 f5 h4 Qa5+ " +
        "c3 Qxc3+ Nd2 Qxa3 Bh3 Qb4 O-O Qxd4 Bxf5 Bxf5 Rc1 Bh3 Re1 Qd5 Kh2 Qg2#"

    @Test fun winPercentIsSymmetricAndMonotonic() {
        assertEquals(50.0, MoveGrade.winPct(0), 0.001)
        assertEquals(100.0, MoveGrade.winPct(300) + MoveGrade.winPct(-300), 0.001)
        assertTrue(MoveGrade.winPct(200) > MoveGrade.winPct(100))
        assertTrue(MoveGrade.winPct(100_000) < 100.0 && MoveGrade.winPct(-100_000) > 0.0)
    }

    @Test fun materialCountsPiecesWithoutKings() {
        val start = Position.start()
        assertEquals(3900, MoveGrade.material(start, true))
        assertEquals(3900, MoveGrade.material(start, false))
    }

    @Test fun sacrificeIsMeasuredAfterTheReply() {
        // Trắng đưa Hậu tới e6 (bị Hậu hoặc tốt f7 ăn): sau nước đáp Hậu đen ăn Hậu, trắng mất 900.
        val pos = Position.fromFen("rnb1kbnr/pppqpppp/8/8/8/8/PPPPQPPP/RNB1KBNR w KQkq - 0 1")!!
        val move = pos.parseMove("Qe6")!!
        val reply = pos.apply(move).parseMove("Qxe6")
        assertNotNull(reply)
        assertEquals(900, MoveGrade.sacrificeCp(pos, move, reply))
        // Không có nước đáp thì chưa mất gì; đổi quân ngang giá (hoặc ăn quân) thì không tính là hy sinh.
        assertEquals(0, MoveGrade.sacrificeCp(pos, pos.parseMove("a3")!!, null))
        val trade = Position.fromFen("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")!!
        val exd5 = trade.parseMove("exd5")!!
        assertEquals(-100, MoveGrade.sacrificeCp(trade, exd5, null))
    }

    @Test fun plainLossBandsStillWork() {
        val pos = Position.start()
        val e4 = pos.parseMove("e4")!!
        val d4 = pos.parseMove("d4")!!
        // trắng từ +0,30 xuống -3,00 mà không phải nước tốt nhất: sai lầm
        assertEquals(MoveClass.MISTAKE, MoveGrade.classify(pos, e4, null, 30, -300, d4, null, null))
        assertEquals(MoveClass.BLUNDER, MoveGrade.classify(pos, e4, null, 30, -500, d4, null, null))
        assertEquals(MoveClass.GOOD, MoveGrade.classify(pos, e4, null, 30, 20, d4, null, null))
        assertEquals(MoveClass.BEST, MoveGrade.classify(pos, e4, null, 30, 20, e4, null, null))
    }

    @Test fun realGameHasExactlyOneBrilliantMoveAndItIsTheKnightSacrifice() {
        val g = Pgn.parse(game).game!!
        val ps = ArrayList<Position>().apply { add(Position.fromFen(g.startFen)!!) }
        val mv = ArrayList<Move>()
        for (u in g.moves) {
            val m = ps.last().parseMove(u)!!
            mv.add(m)
            ps.add(ps.last().apply(m))
        }
        val engine = Engine(3)
        // thời gian rất lớn để kết quả không phụ thuộc tốc độ máy: dừng theo độ sâu, không theo giờ
        val an = ps.map { engine.analyze(it, 4, 600_000L) }
        val alts = arrayOfNulls<Int>(ps.size)
        for (k in mv.indices) {
            val prev = if (k > 0) mv[k - 1] else null
            if (MoveGrade.worthAlternativeCheck(ps[k], mv[k], prev, an[k].scoreWhite, an[k].best)) {
                alts[k] = MoveGrade.secondBest(engine.rankMoves(ps[k], 4, 600_000L), mv[k])
            }
        }
        val classes = mv.indices.map { k ->
            MoveGrade.classify(
                ps[k], mv[k], if (k > 0) mv[k - 1] else null, an[k].scoreWhite, an[k + 1].scoreWhite,
                an[k].best, an.getOrNull(k + 1)?.best, alts[k],
            )
        }
        val brilliant = classes.indices.filter { classes[it] == MoveClass.BRILLIANT }
        assertEquals("chỉ nên có đúng một nước xuất sắc", listOf(13), brilliant)
        // 4.Nxd5 là nước bỏ Mã của đối thủ: không được khen
        assertTrue(classes[6] != MoveClass.BRILLIANT && classes[6] != MoveClass.GREAT && classes[6] != MoveClass.BEST)
        // 8...Qb6 bỏ lỡ 8...Qb4+ ăn Tượng
        assertTrue(abs(an[15].scoreWhite - an[16].scoreWhite) > 100)
        assertTrue(classes[15] in setOf(MoveClass.INACCURACY, MoveClass.MISTAKE, MoveClass.BLUNDER))
        // ăn lại quân hay ăn không quân hớ hênh không bị gắn nhầm là "rất hay"
        for (k in listOf(7, 14, 29)) assertTrue("nước $k", classes[k] != MoveClass.GREAT)
    }
}
