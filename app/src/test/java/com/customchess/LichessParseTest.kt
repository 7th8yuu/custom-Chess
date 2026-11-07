package com.customchess

import com.customchess.net.parseChallenge
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

    @Test
    fun parsesRealtimeChallenge() {
        val o = JSONObject(
            """{"id":"7pGLxJ4F","status":"created","rated":true,"speed":"rapid","color":"white",
               "variant":{"key":"standard","name":"Standard","short":"Std"},
               "challenger":{"id":"bot1","name":"Bot1","rating":1500,"title":null},
               "destUser":{"id":"thibot","name":"thibot","rating":1600},
               "timeControl":{"type":"clock","limit":600,"increment":5,"show":"10+5"}}""",
        )
        val c = parseChallenge(o, JSONObject("""{"bot":false,"board":true}"""))!!
        assertEquals("7pGLxJ4F", c.id)
        assertEquals("Bot1", c.from)
        assertEquals(1500, c.fromRating)
        assertEquals("thibot", c.to)
        assertEquals("10 + 5", c.clock)
        assertEquals("white", c.color)
        assertTrue(c.rated)
        assertTrue(c.playable)
        assertFalse(c.correspondence)
    }

    @Test
    fun parsesCorrespondenceAndRejectsUnplayable() {
        val corr = parseChallenge(
            JSONObject("""{"id":"abcd1234","speed":"correspondence","challenger":{"name":"X"},"timeControl":{"type":"correspondence","daysPerTurn":3}}"""),
        )!!
        assertEquals("3 ngày", corr.clock)
        assertTrue(corr.correspondence)
        assertEquals("random", corr.color)

        val odd = parseChallenge(
            JSONObject("""{"id":"abcd1235","speed":"blitz","variant":{"key":"atomic"},"challenger":{"name":"Y"},"timeControl":{"type":"clock","limit":180,"increment":2}}"""),
            JSONObject("""{"board":true}"""),
        )!!
        assertFalse(odd.playable) // biến thể không phải standard
        val notBoard = parseChallenge(
            JSONObject("""{"id":"abcd1236","speed":"bullet","challenger":{"name":"Z"},"timeControl":{"type":"clock","limit":60,"increment":0}}"""),
            JSONObject("""{"board":false}"""),
        )!!
        assertFalse(notBoard.playable)
        assertEquals("1 + 0", notBoard.clock)
        assertNull(parseChallenge(JSONObject("""{"speed":"rapid"}""")))
        assertEquals("30s + 0", parseChallenge(JSONObject("""{"id":"q","timeControl":{"type":"clock","limit":30,"increment":0}}"""))!!.clock)
        assertEquals("Không giới hạn", parseChallenge(JSONObject("""{"id":"u","timeControl":{"type":"unlimited"}}"""))!!.clock)
    }
}
