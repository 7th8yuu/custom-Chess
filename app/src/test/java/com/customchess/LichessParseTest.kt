package com.customchess

import com.customchess.net.parseOngoing
import com.customchess.net.parseOngoingList
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LichessParseTest {
    @Test
    fun parsesGameStartEvent() {
        val g = parseOngoing(
            JSONObject(
                """{"gameId":"rCRw1AuO","fullId":"rCRw1AuOvonq","color":"black","speed":"correspondence","perf":"correspondence",
                   "rated":true,"isMyTurn":false,"secondsLeft":172800,"opponent":{"id":"lance5500","username":"Lance5500","rating":2389}}""",
            ),
        )!!
        assertEquals("rCRw1AuO", g.id)
        assertEquals(false, g.white)
        assertEquals("Lance5500", g.opponent)
        assertEquals(2389, g.opponentRating)
        assertTrue(g.correspondence)
        assertTrue(g.rated)
        assertFalse(g.isMyTurn)
        assertEquals(172800, g.secondsLeft)
    }

    @Test
    fun missingIdIsRejected() {
        assertNull(parseOngoing(JSONObject("""{"color":"white"}""")))
    }

    @Test
    fun anonymousOpponentGetsAName() {
        val g = parseOngoing(JSONObject("""{"id":"abc12345","color":"white","speed":"rapid","opponent":{"id":null,"username":null,"rating":1500}}"""))!!
        assertEquals("Ẩn danh", g.opponent)
        assertEquals(true, g.white)
        assertFalse(g.correspondence)
    }

    @Test
    fun parsesNowPlayingList() {
        val list = parseOngoingList(
            """{"nowPlaying":[{"gameId":"aaaaaaaa","color":"white","speed":"rapid","opponent":{"username":"A","rating":1800}},
                              {"gameId":"bbbbbbbb","color":"black","speed":"correspondence","isMyTurn":true,"opponent":{"username":"B","rating":1700}},
                              {"noId":true}]}""",
        )
        assertEquals(listOf("aaaaaaaa", "bbbbbbbb"), list.map { it.id })
        assertTrue(list[1].isMyTurn)
        assertEquals(emptyList<Any>(), parseOngoingList("""{"nowPlaying":[]}"""))
        assertEquals(emptyList<Any>(), parseOngoingList("{}"))
    }
}
