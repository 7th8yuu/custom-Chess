package com.customchess.core

/** Mũi tên người dùng tự vẽ trên bàn cờ; [color] là chỉ số màu trong bảng màu của giao diện. */
data class MarkArrow(val from: Int, val to: Int, val color: Int)

/** Ô người dùng tô màu. */
data class MarkSquare(val sq: Int, val color: Int)

/** Luật bật/tắt các nét vẽ, giống chess.com: vẽ lại đúng nét đó thì xóa, cùng nét khác màu thì đổi màu. */
object Marks {
    fun toggleArrow(list: List<MarkArrow>, a: MarkArrow): List<MarkArrow> {
        if (a.from == a.to || a.from !in 0..63 || a.to !in 0..63) return list
        val old = list.firstOrNull { it.from == a.from && it.to == a.to } ?: return list + a
        return if (old.color == a.color) list - old else list.map { if (it === old) a else it }
    }

    fun toggleSquare(list: List<MarkSquare>, s: MarkSquare): List<MarkSquare> {
        if (s.sq !in 0..63) return list
        val old = list.firstOrNull { it.sq == s.sq } ?: return list + s
        return if (old.color == s.color) list - old else list.map { if (it === old) s else it }
    }
}
