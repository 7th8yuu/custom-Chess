package com.customchess.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customchess.vm.AppViewModel
import com.customchess.vm.Invite
import com.customchess.vm.InviteSource
import com.customchess.vm.Screen

private fun colorLabel(c: String) = when (c) { "white" -> "Trắng"; "black" -> "Đen"; else -> "Ngẫu nhiên" }

/** Nút phong bì kèm chấm số lời thách chưa trả lời. */
@Composable
fun InviteButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(44.dp)) {
        Box(Modifier.fillMaxSize().pressable(onClick).glass(CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(22.dp)) {
                val w = size.width; val h = size.height
                val st = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawRoundRect(Pal.Egg, Offset(w * 0.06f, h * 0.2f), Size(w * 0.88f, h * 0.6f), CornerRadius(w * 0.1f), style = st)
                val flap = Path().apply {
                    moveTo(w * 0.08f, h * 0.26f); lineTo(w * 0.5f, h * 0.58f); lineTo(w * 0.92f, h * 0.26f)
                }
                drawPath(flap, Pal.Egg, style = st)
            }
        }
        if (count > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(Pal.Alert),
                contentAlignment = Alignment.Center,
            ) { Text2(if (count > 9) "9+" else "$count", Type.Small.copy(fontSize = 10.sp, color = Color.White)) }
        }
    }
}

/** Nút phong bì mở hộp thư mời; đặt ở phần phải của thanh tiêu đề. */
@Composable
fun InviteEntry(vm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    InviteButton(vm.invites.size, { open = true })
    if (open) InviteInbox(vm) { open = false }
}

/** Một lời thách: ai thách, thời gian, màu, và nút Từ chối, Chấp nhận. */
@Composable
fun InviteCard(inv: Invite, vm: AppViewModel, modifier: Modifier = Modifier, onLater: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(inv.from.firstOrNull()?.uppercase() ?: "?", size = 40.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text2(if (inv.rating > 0) "${inv.from} (${inv.rating})" else inv.from, Type.Heading)
                val kind = if (inv.source == InviteSource.LICHESS) (if (inv.rated) "Xếp hạng" else "Giao hữu") else ""
                val parts = listOf(inv.clock, if (inv.speed.isNotEmpty()) speedName(inv.speed) else "", kind, if (inv.source == InviteSource.LICHESS) "Lichess" else "Wi-Fi")
                Text2(parts.filter { it.isNotEmpty() }.joinToString(" · "), Type.Small)
            }
        }
        if (inv.color != "random") Text2("${inv.from} cầm quân ${colorLabel(inv.color)}", Type.Small, Modifier.padding(top = 6.dp))
        if (!inv.canAccept && inv.note.isNotEmpty()) Text2(inv.note, Type.Small.copy(color = Pal.Alert), Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onLater != null) SmallAction("Để sau", Pal.EggDim, Modifier.weight(1f), onLater)
            SmallAction("Từ chối", Pal.Alert, Modifier.weight(1f)) { vm.declineInvite(inv) }
            SmallAction("Chấp nhận", Pal.Gold, Modifier.weight(1.3f), enabled = inv.canAccept) { vm.acceptInvite(inv) }
        }
    }
}

@Composable
private fun SmallAction(text: String, color: Color, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    GlassBox(modifier.height(44.dp), shape, onClick = onClick, enabled = enabled) {
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
            Text2(text, Type.Button.copy(color = if (enabled) color else Pal.EggDim, fontSize = 14.sp))
        }
    }
}

/** Thẻ nổi ở đầu màn hình mỗi khi có người thách mình (ẩn khi đang trong ván). */
@Composable
fun InvitePopup(vm: AppViewModel, modifier: Modifier = Modifier) {
    val playing = vm.ui.active && vm.ui.result == null
    val inv = if (playing || vm.screen == Screen.LOGIN) null else vm.invites.firstOrNull { it.id !in vm.dismissedPopups }
    var shown by remember { mutableStateOf<Invite?>(null) }
    if (inv != null) shown = inv
    val shape = RoundedCornerShape(22.dp)
    AnimatedVisibility(
        visible = inv != null,
        modifier = modifier.systemBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
        enter = if (Fx.anim) slideInVertically(tween(280)) { -it } + fadeIn(tween(200)) else fadeIn(tween(0)),
        exit = if (Fx.anim) slideOutVertically(tween(220)) { -it } + fadeOut(tween(160)) else fadeOut(tween(0)),
    ) {
        shown?.let { cur ->
            Box(
                Modifier.fillMaxWidth().shadow(14.dp, shape).background(Pal.Panel, shape)
                    .border(1.dp, Pal.Gold.copy(alpha = 0.55f), shape).padding(16.dp),
            ) {
                Column {
                    Text2("Có người thách bạn đấu", Type.Small.copy(color = Pal.Gold), Modifier.padding(bottom = 8.dp))
                    InviteCard(cur, vm, onLater = { vm.dismissPopup(cur.id) })
                }
            }
        }
    }
}

/** Hộp thư: lời thách gửi tới bạn và lời thách bạn đã gửi (Lichess). */
@Composable
fun InviteInbox(vm: AppViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Panel,
        title = { Text2("Thư mời", Type.Title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (vm.invites.isEmpty() && vm.sentInvites.isEmpty()) {
                    Text2("Chưa có lời thách đấu nào. Khi có người thách bạn trên Lichess hoặc qua Wi-Fi, lời mời hiện ở đây và ở đầu màn hình.", Type.Body)
                }
                for (inv in vm.invites.toList()) InviteCard(inv, vm)
                if (vm.sentInvites.isNotEmpty()) {
                    Text2("Lời thách bạn đã gửi", Type.Small.copy(color = Pal.Gold))
                    for (s in vm.sentInvites.toList()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text2(s.to, Type.Heading)
                                Text2("${s.clock} · ${if (s.rated) "Xếp hạng" else "Giao hữu"} · đang chờ trả lời", Type.Small)
                            }
                            TextButton(onClick = { vm.cancelSentInvite(s) }) { Text2("Hủy", Type.Button.copy(color = Pal.Alert)) }
                        }
                    }
                }
                vm.inviteNote?.let { Text2(it, Type.Small.copy(color = Pal.Alert)) }
            }
        },
        confirmButton = { TextButton(onClick = { vm.clearInviteNote(); onDismiss() }) { Text2("Đóng", Type.Button.copy(color = Pal.Gold)) } },
    )
}
