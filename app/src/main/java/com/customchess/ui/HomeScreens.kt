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
    val li = vm.lichessUi
    val google = rememberGoogleSignIn(vm)
    var guide by remember { mutableStateOf(false) }
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

        ProviderButton(
            if (li.busy) "Đang xác nhận với Lichess..." else "Đăng nhập bằng Lichess",
            badge = { LichessBadge() },
            onClick = { vm.startLichessLogin(ctx.findActivity()) },
            enabled = !li.busy && !vm.authBusy,
            filled = true,
        )
        ErrorText(li.error)
        Text2(
            "Một lần bấm là xong: Lichess cấp quyền và ứng dụng tự nhận, không cần dán gì. Chưa có tài khoản thì tạo miễn phí ở bước đó.",
            Type.Small, Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(14.dp))
        ProviderButton(
            if (vm.authBusy) "Đang đăng nhập..." else "Đăng nhập bằng Google",
            badge = { GoogleBadge() },
            onClick = google,
            enabled = !vm.authBusy && !li.busy,
        )
        ErrorText(vm.authError)
        LinkText("Hướng dẫn bật đăng nhập Google", { guide = true })
        Spacer(Modifier.height(4.dp))
        GhostButton("Chơi với tư cách khách", onClick = vm::continueAsGuest, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        InfoCard("Khách dùng được mọi chế độ offline và chơi với AI bằng API key của riêng bạn. Đấu online với người lạ cần tài khoản Lichess hoặc Google.")
        Spacer(Modifier.height(24.dp))
    }
    if (guide) GoogleGuideDialog { guide = false }
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
