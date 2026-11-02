package com.customchess.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.customchess.net.PublicApi
import com.customchess.net.Tournament
import com.customchess.vm.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TournamentsScreen(vm: AppViewModel) {
    val uri = LocalUriHandler.current
    var list by remember { mutableStateOf<List<Tournament>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        error = null
        try {
            list = withContext(Dispatchers.IO) { PublicApi.tournaments() }
        } catch (e: Exception) {
            error = e.message ?: "Không tải được danh sách giải đấu"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Giải đấu", vm::back, trailing = { GlassIconButton(Icons.Rounded.Refresh, "Tải lại", { reload++ }, enabled = !loading) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                "Các giải Arena công khai trên Lichess. Chạm vào một giải để mở trang của giải trên lichess.org và tham gia bằng tài khoản Lichess của bạn.",
                style = Type.Small,
            )
            Spacer(Modifier.height(14.dp))
            if (loading) {
                Row(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    CircularProgressIndicator(color = Pal.Gold)
                }
            }
            ErrorText(error)
            if (!loading && error == null && list.isEmpty()) InfoCard("Hiện chưa có giải nào được liệt kê.")
            for (t in list) {
                TournamentRow(t) { uri.openUri("https://lichess.org/tournament/${t.id}") }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TournamentRow(t: Tournament, onClick: () -> Unit) {
    val live = t.status == 20
    val time = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(t.startsAt))
    GlassBox(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), onClick = onClick, tint = if (live) Pal.Jade else androidx.compose.ui.graphics.Color.Unspecified) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.EmojiEvents, tint = if (live) Pal.Jade else Pal.Gold, size = 48.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(t.name, style = Type.Heading, maxLines = 1)
                Text(
                    listOf(t.clock, t.perf, "${t.minutes} phút").filter { it.isNotBlank() }.joinToString(" · ") + if (t.rated) "" else " · giao hữu",
                    style = Type.Small, maxLines = 1,
                )
                Text(if (live) "Đang diễn ra · ${t.players} người" else "Bắt đầu $time · ${t.players} người đăng ký", style = Type.Small)
            }
            Pill2(if (live) "Đang diễn ra" else "Sắp tới", if (live) Pal.Jade else Pal.Gold)
        }
    }
}
