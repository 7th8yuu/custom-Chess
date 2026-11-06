package com.customchess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customchess.core.MarkArrow
import com.customchess.core.MarkSquare
import com.customchess.core.Marks
import com.customchess.data.MarkStyle

/**
 * Nét vẽ của người dùng trên một bàn cờ (mũi tên và ô tô màu). Xóa khi đi nước mới hoặc khi chạm bàn cờ ở chế độ thường,
 * giống chess.com.
 */
@Stable
class BoardMarks {
    var arrows by mutableStateOf<List<MarkArrow>>(emptyList())
        private set
    var squares by mutableStateOf<List<MarkSquare>>(emptyList())
        private set

    /** true = chỉ để hiển thị (bản xem trước), không nhận cử chỉ vẽ. */
    var readOnly by mutableStateOf(false)

    /** Kiểu vẽ đang dùng (màu, độ đậm...), lấy từ Cài đặt. */
    var style by mutableStateOf(MarkStyle.DEFAULT)

    /** Chỉ số màu đang chọn trong [MarkStyle.colors]. */
    var color by mutableIntStateOf(0)

    /** Màu (chỉ số đã được kẹp vào khoảng hợp lệ) theo kiểu hiện tại. */
    fun colorOf(index: Int): Color = Color(style.colors[index.coerceIn(0, style.colors.lastIndex)])

    /** Bật thì kéo là vẽ mũi tên, chạm là tô ô, và không đi quân được. Tắt thì giữ ngón tay rồi kéo để vẽ. */
    var drawMode by mutableStateOf(false)

    /** Nét đang kéo dở (xem trước), -1 nếu không có. */
    var dragFrom by mutableIntStateOf(-1)
    var dragTo by mutableIntStateOf(-1)

    val isEmpty: Boolean get() = arrows.isEmpty() && squares.isEmpty()

    fun clear() {
        if (arrows.isNotEmpty()) arrows = emptyList()
        if (squares.isNotEmpty()) squares = emptyList()
    }

    fun arrow(from: Int, to: Int) {
        if (from == to) square(from) else arrows = Marks.toggleArrow(arrows, MarkArrow(from, to, color))
    }

    fun square(sq: Int) {
        squares = Marks.toggleSquare(squares, MarkSquare(sq, color))
    }

    /** Đặt sẵn nét vẽ (dùng cho bản xem trước kiểu vẽ). */
    fun preset(arrows: List<MarkArrow>, squares: List<MarkSquare>) {
        this.arrows = arrows
        this.squares = squares
    }

    fun cancelDrag() {
        dragFrom = -1
        dragTo = -1
    }

    fun endDrag() {
        val a = dragFrom
        val b = dragTo
        cancelDrag()
        if (a >= 0 && b >= 0) arrow(a, b)
    }
}

/** Dải điều khiển vẽ: bật/tắt chế độ vẽ, chọn màu, xóa hết nét vẽ. */
@Composable
fun DrawBar(marks: BoardMarks, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassBox(
                Modifier.weight(1f).height(46.dp), RoundedCornerShape(18.dp), selected = marks.drawMode,
                onClick = { marks.drawMode = !marks.drawMode },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text2(if (marks.drawMode) "Đang vẽ" else "Vẽ", Type.Button.copy(fontSize = 13.sp, color = Pal.Egg))
                }
            }
            for ((i, c) in marks.style.colors.map { Color(it) }.withIndex()) {
                val picked = marks.color.coerceIn(0, marks.style.colors.lastIndex) == i
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(c)
                        .then(if (picked) Modifier.border(3.dp, Pal.Egg, CircleShape) else Modifier)
                        .pressable({ marks.color = i }),
                )
            }
            GlassBox(
                Modifier.weight(1f).height(46.dp).alpha(if (marks.isEmpty) 0.35f else 1f), RoundedCornerShape(18.dp),
                onClick = marks::clear, enabled = !marks.isEmpty,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text2("Xóa nét vẽ", Type.Button.copy(fontSize = 13.sp, color = Pal.Egg))
                }
            }
        }
        if (marks.drawMode) {
            Text2(
                "Kéo từ ô này sang ô kia để vẽ mũi tên, chạm một ô để tô màu. Tắt chế độ vẽ để đi quân.",
                Type.Small, Modifier.padding(top = 6.dp),
            )
        }
    }
}
