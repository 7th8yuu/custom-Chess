package com.customchess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customchess.core.Game
import com.customchess.core.Move
import com.customchess.core.Pc
import com.customchess.core.Position
import com.customchess.vm.AppViewModel
import com.customchess.vm.Screen
import kotlinx.coroutines.delay

/** Ván "Opera" (Morphy, 1858) tự chơi lặp lại trên trang chủ. */
private val OPERA_GAME = (
    "e2e4 e7e5 g1f3 d7d6 d2d4 c8g4 d4e5 g4f3 d1f3 d6e5 f1c4 g8f6 f3b3 d8e7 b1c3 c7c6 " +
        "c1g5 b7b5 c3b5 c6b5 c4b5 b8d7 e1c1 a8d8 d1d7 d8d7 h1d1 e7e6 b5d7 f6d7 b3b8 d7b8 d1d8"
    ).split(' ')

@Composable
fun DemoBoard(theme: BoardTheme, modifier: Modifier = Modifier) {
    var board by remember { mutableStateOf(Position.start().board) }
    var last by remember { mutableStateOf<Move?>(null) }
    var check by remember { mutableIntStateOf(-1) }
    var ply by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            val g = Game()
            board = g.position.board.copyOf(); last = null; check = -1; ply = 0
            delay(1600)
            for (u in OPERA_GAME) {
                val m = g.position.parseMove(u) ?: break
                g.play(m)
                val p = g.position
                board = p.board.copyOf()
                last = m
                check = if (p.inCheck()) p.kingSquare(p.whiteToMove) else -1
                ply++
                delay(if (ply < 8) 700L else 1000L)
            }
            delay(3200)
        }
    }
    ChessBoard(
        board = board, flipped = false, lastMove = last, checkSquare = check, selected = -1,
        targets = emptySet(), plyCount = ply, theme = theme, modifier = modifier, interactive = false,
    )
}

// ---------------------------------------------------------------------------------- đăng nhập

@Composable
fun LoginScreen(vm: AppViewModel, theme: BoardTheme) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        DemoBoard(theme, Modifier.fillMaxWidth())
        Spacer(Modifier.height(28.dp))
        Text2("Custom Chess", Type.Display)
        Spacer(Modifier.height(8.dp))
        Text2("Cờ vua của riêng bạn: đấu với máy, với AI, với bạn bè, kể cả khi không có mạng.", Type.Body.copy(color = Pal.EggDim))
        Spacer(Modifier.height(28.dp))
        PrimaryButton(
            if (vm.authBusy) "Đang đăng nhập..." else "Đăng nhập bằng Google",
            onClick = { ctx.findActivity()?.let { vm.signInGoogle(it) } },
            enabled = !vm.authBusy && vm.googleConfigured,
        )
        if (!vm.googleConfigured) {
            ErrorText("Bản build này chưa có Google Web Client ID nên chưa đăng nhập Google được. Xem README để thêm. Bạn vẫn vào được bằng chế độ khách.")
        }
        ErrorText(vm.authError)
        Spacer(Modifier.height(12.dp))
        GhostButton("Chơi với tư cách khách", onClick = vm::continueAsGuest, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        InfoCard("Khách dùng được mọi chế độ offline và chơi với AI bằng API key của riêng bạn. Đấu online với người lạ cần tài khoản Google.")
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------------- trang chủ

@Composable
private fun ModeRow(piece: Int, title: String, subtitle: String, onClick: () -> Unit, locked: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (locked) 0.62f else 1f)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Pal.PanelHi),
            contentAlignment = Alignment.Center,
        ) { PieceIcon(piece, 40.dp) }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text2(title, Type.Heading.copy(fontSize = 19.sp))
            Text2(subtitle, Type.Small)
        }
        Text2(if (locked) "Khóa" else "›", Type.Title.copy(color = if (locked) Pal.GoldDeep else Pal.Gold, fontSize = if (locked) 14.sp else 26.sp))
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Pal.Hair))
}

@Composable
fun HomeScreen(vm: AppViewModel, theme: BoardTheme) {
    val user = vm.user
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text2("Custom Chess", Type.Title.copy(fontSize = 28.sp))
                Text2(
                    if (user == null || user.isGuest) "Chế độ khách" else "Xin chào, ${user.name}",
                    Type.Small,
                )
            }
            Avatar(user?.initial ?: "?", modifier = Modifier.clickable { vm.go(Screen.SETTINGS) })
        }
        DemoBoard(theme, Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        ModeRow(Pc.ROOK, "Chơi với máy", "Engine offline, 5 cấp độ", { vm.go(Screen.ENGINE_SETUP) })
        Hairline()
        ModeRow(Pc.QUEEN, "Chơi với AI qua API", "Gemini, OpenAI hoặc Claude bằng key của bạn", { vm.go(Screen.API_SETUP) })
        Hairline()
        ModeRow(Pc.PAWN, "Chơi với bạn bè", "Wi-Fi, Bluetooth hoặc chung một máy", { vm.go(Screen.FRIENDS) })
        Hairline()
        ModeRow(
            Pc.KING, "Đấu online",
            if (vm.canPlayOnline) "Ghép trận với người chơi trên Lichess" else "Cần đăng nhập Google",
            { vm.go(Screen.ONLINE) }, locked = !vm.canPlayOnline,
        )
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------------------------- chơi với máy

private fun colorOf(i: Int): Boolean? = when (i) { 0 -> true; 1 -> false; else -> null }

@Composable
fun ColorPills(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Trắng", selected == 0, { onSelect(0) })
        Pill("Đen", selected == 1, { onSelect(1) })
        Pill("Ngẫu nhiên", selected == 2, { onSelect(2) })
    }
}

@Composable
fun EngineSetupScreen(vm: AppViewModel) {
    var level by remember { mutableIntStateOf(vm.settings.engineLevel) }
    var color by remember { mutableIntStateOf(2) }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Chơi với máy", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text2("Engine chạy hoàn toàn trên máy bạn, không cần mạng.", Type.Small)
            SectionTitle("Cấp độ")
            for (l in 1..5) {
                ChoiceRow(com.customchess.vm.levelName(l), com.customchess.vm.levelHint(l), level == l, { level = l })
                Spacer(Modifier.height(8.dp))
            }
            SectionTitle("Bạn cầm quân")
            ColorPills(color) { color = it }
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton("Bắt đầu", { vm.startEngineGame(level, colorOf(color)) }, Modifier.padding(vertical = 16.dp))
    }
}

// ---------------------------------------------------------------------------------- chơi với AI qua API

@Composable
fun ApiSetupScreen(vm: AppViewModel) {
    var color by remember { mutableIntStateOf(2) }
    val provider = vm.settings.provider
    val ready = vm.hasKey(provider)
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Chơi với AI qua API", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text2("Chọn một trong ba hãng và dùng API key của chính bạn. Key chỉ lưu trên máy này.", Type.Small)
            SectionTitle("Hãng AI")
            for (p in com.customchess.ai.Provider.values()) {
                val slot = vm.slot(p)
                ChoiceRow(
                    title = p.label,
                    subtitle = slot.model.ifEmpty { p.defaultModel },
                    selected = provider == p,
                    onClick = { vm.selectProvider(p) },
                    trailing = if (vm.hasKey(p)) "Đã có key" else "Chưa có key",
                )
                Spacer(Modifier.height(8.dp))
            }
            SectionTitle("Bạn cầm quân")
            ColorPills(color) { color = it }
            Spacer(Modifier.height(12.dp))
            GhostButton("Thiết lập API key và model", { vm.go(Screen.AI_SETTINGS) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton(
            if (ready) "Bắt đầu" else "Thêm API key cho ${provider.label}",
            { if (ready) vm.startApiGame(colorOf(color)) else vm.go(Screen.AI_SETTINGS) },
            Modifier.padding(vertical = 16.dp),
        )
    }
}

/** Text dùng style của ứng dụng, viết gọn. */
@Composable
fun Text2(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(text, style = style, modifier = modifier)
}
