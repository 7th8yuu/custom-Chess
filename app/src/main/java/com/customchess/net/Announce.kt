package com.customchess.net

/** [listening] = máy đó đang bật "Nhận lời thách đấu" (nhận được lời thách bất cứ lúc nào), khác với phòng mở sẵn. */
data class LanHost(val name: String, val address: String, val port: Int, val listening: Boolean = false)

/** Gói UDP phát trong mạng LAN: CUSTOMCHESS|1|<tên>|<cổng>, thêm |L cuối nếu là máy đang nhận lời thách đấu. */
object Announce {
    private const val MAGIC = "CUSTOMCHESS"

    fun encode(name: String, port: Int, listening: Boolean = false) =
        "$MAGIC|1|${name.replace('|', ' ').replace('\n', ' ').take(30)}|$port" + if (listening) "|L" else ""

    fun parse(text: String, from: String): LanHost? {
        val p = text.trim().split('|')
        if (p.size < 4 || p[0] != MAGIC) return null
        val port = p[3].toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        return LanHost(p[2].ifBlank { "Bạn bè" }, from, port, listening = p.getOrNull(4) == "L")
    }
}
