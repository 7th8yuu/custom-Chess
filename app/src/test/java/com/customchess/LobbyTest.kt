package com.customchess

import com.customchess.core.Lobby
import com.customchess.core.Speed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LobbyTest {
    @Test
    fun speedFollowsLichessThresholds() {
        assertEquals(Speed.BULLET, Lobby.speed(1, 0))
        assertEquals(Speed.BULLET, Lobby.speed(2, 1)) // 120 + 40 = 160
        assertEquals(Speed.BLITZ, Lobby.speed(3, 0)) // đúng 180
        assertEquals(Speed.BLITZ, Lobby.speed(3, 2))
        assertEquals(Speed.BLITZ, Lobby.speed(5, 3)) // 300 + 120 = 420
        assertEquals(Speed.RAPID, Lobby.speed(8, 0)) // đúng 480
        assertEquals(Speed.RAPID, Lobby.speed(10, 5))
        assertEquals(Speed.RAPID, Lobby.speed(15, 10)) // 900 + 400 = 1300
        assertEquals(Speed.CLASSICAL, Lobby.speed(25, 0)) // đúng 1500
        assertEquals(Speed.CLASSICAL, Lobby.speed(30, 20))
    }

    @Test
    fun seekOnlyFromRapid() {
        assertFalse(Lobby.seekAllowed(Speed.BULLET))
        assertFalse(Lobby.seekAllowed(Speed.BLITZ))
        assertTrue(Lobby.seekAllowed(Speed.RAPID))
        assertTrue(Lobby.seekAllowed(Speed.CLASSICAL))
        assertFalse(Lobby.challengeAllowed(Speed.BULLET))
        assertTrue(Lobby.challengeAllowed(Speed.BLITZ))
    }

    @Test
    fun tilesCoverEverySpeedAndAreSorted() {
        val speeds = Lobby.TILES.map { it.speed }.toSet()
        assertEquals(Speed.values().toSet(), speeds)
        val order = Lobby.TILES.map { it.speed.ordinal }
        assertEquals(order.sorted(), order)
        assertEquals("10 + 5", Lobby.TILES.first { it.minutes == 10 && it.increment == 5 }.label)
    }

    @Test
    fun clockBounds() {
        assertNull(Lobby.checkClock(10, 0))
        assertNull(Lobby.checkClock(180, 180))
        assertTrue(Lobby.checkClock(0, 5) != null)
        assertTrue(Lobby.checkClock(181, 0) != null)
        assertTrue(Lobby.checkClock(10, 181) != null)
        assertTrue(Lobby.checkClock(10, -1) != null)
    }

    @Test
    fun ratingRangeParsing() {
        assertNull(Lobby.ratingRange("", " "))
        assertEquals("1500-1800", Lobby.ratingRange(" 1500", "1800 "))
        assertEquals("0-4000", Lobby.ratingRange("0", "4000"))
        for ((a, b) in listOf("1500" to "", "" to "1800", "abc" to "1800", "1900" to "1800", "-5" to "100", "100" to "4001")) {
            try {
                Lobby.ratingRange(a, b)
                fail("lẽ ra phải báo lỗi với $a-$b")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.isNotBlank())
            }
        }
    }

    @Test
    fun correspondenceDaysMatchLichess() {
        assertEquals(listOf(1, 2, 3, 5, 7, 10, 14), Lobby.CORRESPONDENCE_DAYS)
    }

    @Test
    fun leftTextIsReadable() {
        assertEquals("", Lobby.leftText(0))
        assertEquals("1 phút", Lobby.leftText(20))
        assertEquals("5 giờ", Lobby.leftText(5 * 3600 + 120))
        assertEquals("2 ngày", Lobby.leftText(2 * 86_400))
        assertEquals("2 ngày 3 giờ", Lobby.leftText(2 * 86_400 + 3 * 3600 + 59))
    }

    @Test
    fun intOrFallsBack() {
        assertEquals(7, Lobby.intOr(" 7 ", 1))
        assertEquals(1, Lobby.intOr("", 1))
        assertEquals(1, Lobby.intOr("x", 1))
        assertEquals(1, Lobby.intOr("-3", 1))
    }
}
