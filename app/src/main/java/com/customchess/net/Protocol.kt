package com.customchess.net

/**
 * Giao thức văn bản, mỗi dòng một thông điệp, dùng chung cho Wi-Fi và Bluetooth.
 *
 *   HELLO 1 <tên>
 *   START <b|w: màu của người khách> <giây> <cộng giây>
 *   MOVE <uci>
 *   RESIGN | DRAW_OFFER | DRAW_ACCEPT | DRAW_DECLINE | TIMEOUT
 *   CHAT <nội dung>
 */
sealed class Msg {
    data class Hello(val name: String, val version: Int = VERSION) : Msg()
    data class Start(val guestIsWhite: Boolean, val baseSec: Int, val incSec: Int) : Msg()
    data class Move(val uci: String) : Msg()
    data object Resign : Msg()
    data object DrawOffer : Msg()
    data object DrawAccept : Msg()
    data object DrawDecline : Msg()

    /** Người gửi hết giờ (người gửi là bên thua). */
    data object Timeout : Msg()
    data class Chat(val text: String) : Msg()
    data class Unknown(val raw: String) : Msg()

    companion object {
        const val VERSION = 1
    }
}

object Protocol {
    private fun clean(s: String) = s.replace('\n', ' ').replace('\r', ' ').trim().take(60)

    fun encode(m: Msg): String = when (m) {
        is Msg.Hello -> "HELLO ${m.version} ${clean(m.name)}"
        is Msg.Start -> "START ${if (m.guestIsWhite) 'w' else 'b'} ${m.baseSec} ${m.incSec}"
        is Msg.Move -> "MOVE ${m.uci}"
        Msg.Resign -> "RESIGN"
        Msg.DrawOffer -> "DRAW_OFFER"
        Msg.DrawAccept -> "DRAW_ACCEPT"
        Msg.DrawDecline -> "DRAW_DECLINE"
        Msg.Timeout -> "TIMEOUT"
        is Msg.Chat -> "CHAT ${clean(m.text)}"
        is Msg.Unknown -> m.raw
    }

    fun parse(line: String): Msg {
        val l = line.trim()
        val sp = l.indexOf(' ')
        val cmd = if (sp < 0) l else l.substring(0, sp)
        val rest = if (sp < 0) "" else l.substring(sp + 1).trim()
        return when (cmd) {
            "HELLO" -> {
                val v = rest.substringBefore(' ').toIntOrNull() ?: return Msg.Unknown(l)
                Msg.Hello(rest.substringAfter(' ', "").ifBlank { "Bạn bè" }, v)
            }
            "START" -> {
                val p = rest.split(' ')
                if (p.size < 3) return Msg.Unknown(l)
                val white = when (p[0]) { "w" -> true; "b" -> false; else -> return Msg.Unknown(l) }
                Msg.Start(white, p[1].toIntOrNull() ?: 0, p[2].toIntOrNull() ?: 0)
            }
            "MOVE" -> if (rest.length in 4..5) Msg.Move(rest) else Msg.Unknown(l)
            "RESIGN" -> Msg.Resign
            "DRAW_OFFER" -> Msg.DrawOffer
            "DRAW_ACCEPT" -> Msg.DrawAccept
            "DRAW_DECLINE" -> Msg.DrawDecline
            "TIMEOUT" -> Msg.Timeout
            "CHAT" -> Msg.Chat(rest)
            else -> Msg.Unknown(l)
        }
    }
}
