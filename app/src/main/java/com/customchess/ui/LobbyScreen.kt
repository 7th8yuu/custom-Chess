package com.customchess.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customchess.core.Lobby
import com.customchess.core.TimeTile
import com.customchess.net.OngoingGame
import com.customchess.vm.AppViewModel

private fun colorName(i: Int) = when (i) { 0 -> "white"; 1 -> "black"; else -> "random" }

/** Tên loại ván theo cách Lichess gọi. */
private fun speedName(s: String) = when (s) {
    "correspondence" -> "Cờ qua thư"
    "ultraBullet", "bullet" -> "Bullet"
    "blitz" -> "Blitz"
    "rapid" -> "Rapid"
    "classical" -> "Classical"
    else -> s.replaceFirstChar { it.uppercase() }
}

/**
 * Sảnh Lichess sau khi đã kết nối: ván đang chơi, chọn xếp hạng hoặc giao hữu, rồi ba thẻ
 * Xếp cặp nhanh (lưới thời gian), Phòng chờ (tự chọn thời gian, màu, khoảng điểm) và Cờ qua thư.
 */
@Composable
fun LichessLobby(vm: AppViewModel) {
    val li = vm.lichessUi
    var tab by remember { mutableIntStateOf(0) }
    var rated by remember { mutableStateOf(false) }

    if (li.searching) {
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Pal.Gold, strokeWidth = 2.5.dp)
            Text2("Đang tìm đối thủ...", Type.Title, Modifier.padding(start = 14.dp))
        }
        if (li.searchLabel.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text2(li.searchLabel, Type.Heading.copy(color = Pal.Gold), Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        Text2(
            "Giữ màn hình này mở. Khi có người nhận lời, ván sẽ tự bắt đầu.",
            Type.Small, Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        GhostButton("Hủy tìm", { vm.cancelSeek() }, Modifier.fillMaxWidth())
        return
    }

    OngoingSection(vm)

    SectionTitle("Kiểu ván")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Giao hữu", !rated, { rated = false })
        Pill("Xếp hạng", rated, { rated = true })
    }
    Text2(
        if (rated) "Kết quả được tính vào điểm xếp hạng Lichess của bạn." else "Không đổi điểm xếp hạng, hợp để luyện tập.",
        Type.Small, Modifier.padding(top = 6.dp),
    )

    Spacer(Modifier.height(16.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val names = listOf("Xếp cặp nhanh", "Phòng chờ", "Cờ qua thư")
        items(names.size) { i -> Pill(names[i], tab == i, { tab = i; vm.showOnlineError(null) }) }
    }
    Spacer(Modifier.height(12.dp))

    AnimatedContent(
        targetState = tab,
        transitionSpec = { fadeIn(tween(if (Fx.anim) 200 else 0)) togetherWith fadeOut(tween(if (Fx.anim) 120 else 0)) },
        label = "lobbytab",
    ) { t ->
        Column(Modifier.fillMaxWidth()) {
            when (t) {
                0 -> QuickPairing(vm, rated)
                1 -> RoomTab(vm, rated)
                else -> CorrespondenceTab(vm, rated)
            }
        }
    }
    li.info?.let { Text2(it, Type.Small.copy(color = Pal.Jade), Modifier.padding(top = 10.dp)) }
    ErrorText(li.error)
}

// ---------------------------------------------------------------------------------- ván đang chơi

@Composable
private fun OngoingSection(vm: AppViewModel) {
    val list = vm.lichessUi.ongoing
    if (list.isEmpty()) return
    SectionTitle("Ván đang chơi")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { g -> OngoingRow(g) { vm.openOngoing(g) } }
    }
}

@Composable
private fun OngoingRow(g: OngoingGame, onOpen: () -> Unit) {
    GlassCard(onClick = onOpen, padding = 14.dp, tint = if (g.isMyTurn) Pal.Gold else androidx.compose.ui.graphics.Color.Unspecified) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(g.opponent.firstOrNull()?.uppercase() ?: "?", size = 40.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text2(
                    if (g.opponentRating > 0) "${g.opponent} (${g.opponentRating})" else g.opponent,
                    Type.Heading,
                )
                val turn = if (g.isMyTurn) "Đến lượt bạn" else "Chờ đối thủ"
                val kind = if (g.rated) "Xếp hạng" else "Giao hữu"
                val left = Lobby.leftText(g.secondsLeft)
                Text2(
                    if (g.correspondence && left.isNotEmpty()) "${speedName(g.speed)} · $kind · $turn · còn $left" else "${speedName(g.speed)} · $kind · $turn",
                    Type.Small.copy(color = if (g.isMyTurn) Pal.Gold else Pal.EggDim),
                )
            }
            Text2("Mở", Type.Button.copy(color = Pal.Gold, fontSize = 14.sp))
        }
    }
}

// ---------------------------------------------------------------------------------- xếp cặp nhanh

@Composable
private fun QuickPairing(vm: AppViewModel, rated: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Lobby.TILES.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    val ok = Lobby.seekAllowed(t.speed)
                    TimeTileBox(t, ok, Modifier.weight(1f)) { vm.seekLichess(t.minutes, t.increment, rated) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Text2("Số phút + số giây cộng thêm sau mỗi nước.", Type.Small, Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(8.dp))
    InfoCard("Bullet và Blitz bị mờ vì Lichess không cho ứng dụng bên ngoài ghép trận ngẫu nhiên ở tốc độ đó, chỉ từ Rapid trở lên. Blitz vẫn chơi được khi thách đấu trực tiếp một người cụ thể.")
}

@Composable
private fun TimeTileBox(t: TimeTile, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    GlassBox(modifier.alpha(if (enabled) 1f else 0.4f), RoundedCornerShape(18.dp), onClick = onClick, enabled = enabled) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text2(t.label, Type.Title)
            Text2(t.speed.label, Type.Small)
        }
    }
}

// ---------------------------------------------------------------------------------- phòng chờ

@Composable
private fun SeekOptions(
    color: Int, onColor: (Int) -> Unit,
    lo: String, onLo: (String) -> Unit,
    hi: String, onHi: (String) -> Unit,
) {
    Text2("Màu quân của bạn", Type.Small, Modifier.padding(top = 14.dp, bottom = 6.dp))
    ColorPills(color, onColor)
    Text2("Điểm đối thủ (có thể bỏ trống)", Type.Small, Modifier.padding(top = 14.dp, bottom = 6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberField(lo, onLo, "Từ", Modifier.weight(1f))
        NumberField(hi, onHi, "Đến", Modifier.weight(1f))
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        modifier = modifier, singleLine = true,
        label = { Text2(label, Type.Small) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = fieldColors(),
    )
}

@Composable
private fun RoomTab(vm: AppViewModel, rated: Boolean) {
    var minutes by remember { mutableStateOf("10") }
    var inc by remember { mutableStateOf("0") }
    var color by remember { mutableIntStateOf(2) }
    var lo by remember { mutableStateOf("") }
    var hi by remember { mutableStateOf("") }

    val m = Lobby.intOr(minutes, 0)
    val i = Lobby.intOr(inc, 0)
    val clockError = Lobby.checkClock(m, i)
    val speed = if (clockError == null) Lobby.speed(m, i) else null
    val allowed = speed != null && Lobby.seekAllowed(speed)

    Text2("Tự chọn thời gian, màu quân và khoảng điểm đối thủ. Phòng của bạn hiện trong sảnh Lichess cho mọi người nhận.", Type.Small)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(minutes, { minutes = it }, "Phút", Modifier.weight(1f))
        NumberField(inc, { inc = it }, "Giây cộng thêm", Modifier.weight(1f))
    }
    Text2(
        when {
            clockError != null -> clockError
            allowed -> "${Lobby.label(m, i)} · ${speed!!.label}"
            else -> "${Lobby.label(m, i)} · ${speed!!.label}: Lichess chỉ cho ứng dụng ngoài tạo phòng từ Rapid (quy ra từ 8 phút trở lên)"
        },
        Type.Small.copy(color = if (allowed) Pal.Jade else Pal.Alert),
        Modifier.padding(top = 8.dp),
    )
    SeekOptions(color, { color = it }, lo, { lo = it }, hi, { hi = it })
    Spacer(Modifier.height(16.dp))
    PrimaryButton("Tạo phòng", {
        val range = try { Lobby.ratingRange(lo, hi) } catch (e: IllegalArgumentException) { vm.showOnlineError(e.message); return@PrimaryButton }
        vm.seekLichess(m, i, rated, colorName(color), range)
    }, enabled = allowed)
    Spacer(Modifier.height(8.dp))
    Text2("Lichess không cho ứng dụng ngoài xem danh sách phòng của người khác. Muốn vào phòng có sẵn, hãy dùng Xếp cặp nhanh.", Type.Small)
}

// ---------------------------------------------------------------------------------- cờ qua thư

@Composable
private fun CorrespondenceTab(vm: AppViewModel, rated: Boolean) {
    var days by remember { mutableIntStateOf(3) }
    var color by remember { mutableIntStateOf(2) }
    var lo by remember { mutableStateOf("") }
    var hi by remember { mutableStateOf("") }

    Text2("Mỗi người có vài ngày cho một nước, đóng ứng dụng vẫn được: ván nằm ở Lichess và hiện trong mục Ván đang chơi.", Type.Small)
    Text2("Số ngày mỗi nước", Type.Small, Modifier.padding(top = 14.dp, bottom = 6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Lobby.CORRESPONDENCE_DAYS.size) { k ->
            val d = Lobby.CORRESPONDENCE_DAYS[k]
            Pill("$d", days == d, { days = d })
        }
    }
    SeekOptions(color, { color = it }, lo, { lo = it }, hi, { hi = it })
    Spacer(Modifier.height(16.dp))
    PrimaryButton(if (vm.lichessUi.busy) "Đang đăng..." else "Đăng yêu cầu", {
        val range = try { Lobby.ratingRange(lo, hi) } catch (e: IllegalArgumentException) { vm.showOnlineError(e.message); return@PrimaryButton }
        vm.seekCorrespondence(days, rated, colorName(color), range)
    }, enabled = !vm.lichessUi.busy)
}
