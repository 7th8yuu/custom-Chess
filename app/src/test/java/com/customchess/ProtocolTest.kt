package com.customchess

import com.customchess.core.Game
import com.customchess.core.MoveExtract
import com.customchess.net.Announce
import com.customchess.net.Msg
import com.customchess.net.Protocol
import com.customchess.net.StreamLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

class ProtocolTest {

    @Test fun roundTrips() {
        val all = listOf(
            Msg.Hello("Minh Anh"), Msg.Start(true, 300, 2), Msg.Start(false, 0, 0), Msg.Move("e2e4"), Msg.Move("e7e8q"),
            Msg.Resign, Msg.DrawOffer, Msg.DrawAccept, Msg.DrawDecline, Msg.Timeout, Msg.Chat("xin chào"),
        )
        for (m in all) assertEquals(m, Protocol.parse(Protocol.encode(m)))
    }

    @Test fun rejectsGarbage() {
        assertTrue(Protocol.parse("") is Msg.Unknown)
        assertTrue(Protocol.parse("MOVE e2") is Msg.Unknown)
        assertTrue(Protocol.parse("START x 1 1") is Msg.Unknown)
        assertTrue(Protocol.parse("HELLO abc") is Msg.Unknown)
        assertTrue(Protocol.parse("BLAH") is Msg.Unknown)
    }

    @Test fun helloKeepsSpacesAndStripsNewlines() {
        val line = Protocol.encode(Msg.Hello("A\nB C"))
        assertEquals(false, line.contains('\n'))
        assertEquals(Msg.Hello("A B C"), Protocol.parse(line))
    }

    @Test fun announceRoundTrip() {
        val h = Announce.parse(Announce.encode("Phòng của An", 8765), "192.168.1.9")
        assertNotNull(h)
        assertEquals("Phòng của An", h!!.name)
        assertEquals(8765, h.port)
        assertEquals("192.168.1.9", h.address)
        assertNull(Announce.parse("HELLO|1|x|1", "1.1.1.1"))
        assertNull(Announce.parse("CUSTOMCHESS|1|x|99999", "1.1.1.1"))
    }

    @Test fun extractsMoveFromChattyReply() {
        val g = Game()
        val legal = g.position.legalMoves()
        assertEquals("e2e4", MoveExtract.fromReply(g.position, legal, "e2e4")?.uci())
        assertEquals("g1f3", MoveExtract.fromReply(g.position, legal, "I considered e2e4 but the best is **g1f3**.")?.uci())
        assertEquals("g1f3", MoveExtract.fromReply(g.position, legal, "My move: Nf3")?.uci())
        assertNull(MoveExtract.fromReply(g.position, legal, "e2e5 is great"))
        assertNull(MoveExtract.fromReply(g.position, legal, ""))
    }

    @Test fun streamLinkSendsLinesBothWays() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val client = Socket("127.0.0.1", server.localPort)
        val accepted = server.accept()
        val a = StreamLink(accepted.getInputStream(), accepted.getOutputStream()) { accepted.close() }
        val b = StreamLink(client.getInputStream(), client.getOutputStream()) { client.close() }
        val gotA = CopyOnWriteArrayList<String>()
        val gotB = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(4)
        a.onLine = { gotA.add(it); latch.countDown() }
        b.onLine = { gotB.add(it); latch.countDown() }
        a.start(); b.start()
        a.send(Msg.Hello("host")); a.send(Msg.Move("e2e4"))
        b.send(Msg.Hello("guest")); b.send(Msg.Move("e7e5"))
        assertTrue("timed out", latch.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("HELLO 1 guest", "MOVE e7e5"), gotA.toList())
        assertEquals(listOf("HELLO 1 host", "MOVE e2e4"), gotB.toList())

        val closed = CountDownLatch(1)
        a.onClosed = { closed.countDown() }
        b.close()
        assertTrue("close not noticed", closed.await(5, TimeUnit.SECONDS))
        a.close(); server.close()
    }
}
