package com.customchess.net

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread

object Lan {
    const val PORT = 8765
    const val DISCOVERY_PORT = 8766

    /** Địa chỉ IPv4 của máy này trên các mạng đang bật (Wi-Fi, điểm phát sóng...). */
    fun localAddresses(): List<String> {
        val out = ArrayList<String>()
        try {
            for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp || ni.isLoopback) continue
                for (a in Collections.list(ni.inetAddresses)) {
                    if (a is Inet4Address && !a.isLoopbackAddress) a.hostAddress?.let { out.add(it) }
                }
            }
        } catch (_: Exception) { }
        return out
    }

    private fun broadcastTargets(): List<InetAddress> {
        val out = ArrayList<InetAddress>()
        try {
            for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) ia.broadcast?.let { out.add(it) }
            }
            out.add(InetAddress.getByName("255.255.255.255"))
        } catch (_: Exception) { }
        return out.distinct()
    }

    /** Kết nối tới máy chủ (gọi ngoài luồng giao diện). */
    fun connect(host: String, port: Int): StreamLink {
        val s = Socket()
        s.connect(InetSocketAddress(host.trim(), port), 7000)
        s.tcpNoDelay = true
        return StreamLink(s.getInputStream(), s.getOutputStream()) { s.close() }
    }

    /** Dùng cho máy chủ: mở cổng, báo hiệu cho các máy cùng mạng, nhận đúng một kết nối. */
    class Host(
        private val name: String,
        private val port: Int = PORT,
        private val onConnected: (StreamLink, String) -> Unit,
        private val onError: (String) -> Unit,
    ) {
        @Volatile private var server: ServerSocket? = null
        @Volatile private var running = false

        fun start() {
            running = true
            thread(isDaemon = true, name = "wifi-accept") {
                try {
                    val s = ServerSocket()
                    s.reuseAddress = true
                    s.bind(InetSocketAddress(port))
                    server = s
                    announce()
                    val sock = s.accept()
                    running = false
                    s.close()
                    sock.tcpNoDelay = true
                    val link = StreamLink(sock.getInputStream(), sock.getOutputStream()) { sock.close() }
                    onConnected(link, sock.inetAddress?.hostAddress.orEmpty())
                } catch (e: Exception) {
                    if (running) onError(e.message ?: "Lỗi mạng")
                }
            }
        }

        private fun announce() {
            thread(isDaemon = true, name = "wifi-announce") {
                try {
                    DatagramSocket().use { ds ->
                        ds.broadcast = true
                        val data = Announce.encode(name, port).toByteArray(Charsets.UTF_8)
                        while (running) {
                            for (target in broadcastTargets()) {
                                try { ds.send(DatagramPacket(data, data.size, target, DISCOVERY_PORT)) } catch (_: Exception) { }
                            }
                            Thread.sleep(1000)
                        }
                    }
                } catch (_: Exception) { }
            }
        }

        fun stop() {
            running = false
            try { server?.close() } catch (_: Exception) { }
        }
    }

    /** Dùng cho máy khách: lắng nghe thông báo của các máy chủ trong cùng mạng. */
    class Scanner(private val context: Context, private val onFound: (LanHost) -> Unit) {
        @Volatile private var socket: DatagramSocket? = null
        private var lock: WifiManager.MulticastLock? = null

        fun start() {
            try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                lock = wm?.createMulticastLock("customchess")?.apply { setReferenceCounted(false); acquire() }
            } catch (_: Exception) { }
            thread(isDaemon = true, name = "wifi-scan") {
                try {
                    val s = DatagramSocket(null)
                    s.reuseAddress = true
                    s.bind(InetSocketAddress(DISCOVERY_PORT))
                    socket = s
                    val buf = ByteArray(256)
                    while (!s.isClosed) {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        val host = Announce.parse(String(p.data, 0, p.length, Charsets.UTF_8), p.address.hostAddress.orEmpty())
                        if (host != null) onFound(host)
                    }
                } catch (_: Exception) { }
            }
        }

        fun stop() {
            try { socket?.close() } catch (_: Exception) { }
            try { lock?.let { if (it.isHeld) it.release() } } catch (_: Exception) { }
        }
    }
}
