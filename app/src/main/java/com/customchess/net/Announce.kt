package com.customchess.net

data class LanHost(val name: String, val address: String, val port: Int)

/** Gói UDP phòng chơi phát trong mạng LAN: CUSTOMCHESS|1|<tên>|<cổng>. */
object Announce {
    private const val MAGIC = "CUSTOMCHESS"

    fun encode(name: String, port: Int) = "$MAGIC|1|${name.replace('|', ' ').replace('\n', ' ').take(30)}|$port"

    fun parse(text: String, from: String): LanHost? {
        val p = text.trim().split('|')
        if (p.size < 4 || p[0] != MAGIC) return null
        val port = p[3].toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        return LanHost(p[2].ifBlank { "Bạn bè" }, from, port)
    }
}
