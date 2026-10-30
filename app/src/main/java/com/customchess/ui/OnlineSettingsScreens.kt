package com.customchess.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customchess.BuildConfig
import com.customchess.vm.AppViewModel
import com.customchess.vm.ONLINE_PRESETS

// ---------------------------------------------------------------------------------- đấu online

@Composable
fun OnlineScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val li = vm.lichessUi
    var time by remember { mutableIntStateOf(0) }
    var rated by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var tokenOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.enterOnline() }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Đấu online", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!vm.canPlayOnline) {
                Text2("Cần đăng nhập Google", Type.Title)
                Spacer(Modifier.height(8.dp))
                Text2(
                    "Bạn đang ở chế độ khách nên chưa ghép trận với người lạ được. " +
                        "Đăng nhập Google để mở khóa. Bạn vẫn chơi được với máy, với AI bằng API key và với bạn bè qua Wi-Fi hoặc Bluetooth.",
                    Type.Body.copy(color = Pal.EggDim),
                )
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    if (vm.authBusy) "Đang đăng nhập..." else "Đăng nhập bằng Google",
                    { ctx.findActivity()?.let { vm.signInGoogle(it) } },
                    enabled = !vm.authBusy && vm.googleConfigured,
                )
                if (!vm.googleConfigured) ErrorText("Bản build này chưa cấu hình Google Sign-In (xem README).")
                ErrorText(vm.authError)
                return@Column
            }

            if (!li.connected) {
                Text2("Ghép trận với người chơi thật", Type.Title)
                Spacer(Modifier.height(8.dp))
                Text2(
                    "Custom Chess kết nối với Lichess, nơi có hàng nghìn người chơi mỗi lúc và cho phép ứng dụng bên thứ ba ghép trận miễn phí. " +
                        "Chess.com không có cổng như vậy cho ứng dụng ngoài, nên chưa kết nối được.",
                    Type.Body.copy(color = Pal.EggDim),
                )
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    if (li.busy) "Đang kết nối..." else "Kết nối tài khoản Lichess",
                    { vm.startLichessLogin(ctx) }, enabled = !li.busy,
                )
                Text2("Trình duyệt sẽ mở để bạn cấp quyền chơi cờ. Chưa có tài khoản thì tạo miễn phí ở bước đó.", Type.Small, Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(16.dp))
                GhostButton(if (tokenOpen) "Ẩn ô token" else "Dùng token cá nhân", { tokenOpen = !tokenOpen }, Modifier.fillMaxWidth())
                if (tokenOpen) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = token, onValueChange = { token = it.trim() },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text2("lip_...", Type.Small) },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Tạo token", {
                            runCatching { uri.openUri("https://lichess.org/account/oauth/token/create?scopes[]=board:play&description=Custom+Chess") }
                        })
                        GhostButton("Kết nối", { vm.connectLichess(token) }, enabled = token.isNotBlank() && !li.busy)
                    }
                    Text2("Token cần quyền Play games with the board API.", Type.Small, Modifier.padding(top = 6.dp))
                }
                ErrorText(li.error)
                return@Column
            }

            // đã kết nối
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(li.name.firstOrNull()?.uppercase() ?: "L", size = 44.dp)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text2(li.name, Type.Heading)
                    Text2("Đã kết nối Lichess", Type.Small.copy(color = Pal.Jade))
                }
                GhostButton("Ngắt", { vm.lichessLogout() })
            }

            if (li.searching) {
                Spacer(Modifier.height(32.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Pal.Gold, strokeWidth = 2.5.dp)
                    Text2("Đang tìm đối thủ...", Type.Title, Modifier.padding(start = 14.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text2(
                    "Giữ màn hình này mở. Khi có người nhận lời, ván sẽ tự bắt đầu.",
                    Type.Small, Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
                GhostButton("Hủy tìm", { vm.cancelSeek() }, Modifier.fillMaxWidth())
            } else {
                SectionTitle("Thời gian mỗi bên")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ONLINE_PRESETS.size) { i -> Pill(ONLINE_PRESETS[i].label, time == i, { time = i }) }
                }
                Text2("Số phút + số giây cộng thêm sau mỗi nước.", Type.Small, Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(8.dp))
                SettingSwitch("Ván tính điểm", "Tắt để chơi giao hữu, không đổi điểm xếp hạng", rated) { rated = it }
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Tìm đối thủ", { vm.seekLichess(ONLINE_PRESETS[time], rated) })
                ErrorText(li.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------------- cài đặt

@Composable
private fun ThemeSwatch(theme: BoardTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Pal.Gold else Pal.Hair), RoundedCornerShape(14.dp))
            .background(Pal.Panel)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(theme.frame).padding(4.dp)) {
            Column {
                for (r in 0..1) Row {
                    for (c in 0..1) Box(Modifier.size(28.dp).background(if ((r + c) % 2 == 0) theme.light else theme.dark))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text2(theme.name, Type.Small.copy(color = if (selected) Pal.Gold else Pal.EggDim))
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val user = vm.user
    var name by remember { mutableStateOf(vm.settings.playerName) }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Cài đặt", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.Panel)
                    .border(BorderStroke(1.dp, Pal.Hair), RoundedCornerShape(14.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(user?.initial ?: "?", size = 48.dp)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text2(user?.name ?: "Chưa đăng nhập", Type.Heading)
                    Text2(
                        if (user == null || user.isGuest) "Chế độ khách, không đấu online được" else user.email,
                        Type.Small,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (user == null || user.isGuest) {
                PrimaryButton(
                    if (vm.authBusy) "Đang đăng nhập..." else "Đăng nhập bằng Google",
                    { ctx.findActivity()?.let { vm.signInGoogle(it) } },
                    enabled = !vm.authBusy && vm.googleConfigured,
                )
                if (!vm.googleConfigured) ErrorText("Bản build này chưa cấu hình Google Sign-In (xem README).")
                ErrorText(vm.authError)
                Spacer(Modifier.height(8.dp))
                GhostButton("Quay về màn hình đăng nhập", { vm.signOut() }, Modifier.fillMaxWidth())
            } else {
                GhostButton("Đăng xuất", { vm.signOut() }, Modifier.fillMaxWidth(), danger = true)
            }

            SectionTitle("Tên hiển thị")
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(24)
                    vm.updateSettings(vm.settings.copy(playerName = name.trim()))
                },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text2(user?.name ?: "Bạn", Type.Small) },
                colors = fieldColors(),
            )
            Text2("Tên này hiện cho bạn bè khi chơi qua Wi-Fi hoặc Bluetooth.", Type.Small, Modifier.padding(top = 6.dp))

            SectionTitle("Bàn cờ")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BOARD_THEMES.forEachIndexed { i, t ->
                    ThemeSwatch(t, vm.settings.boardTheme == i, { vm.updateSettings(vm.settings.copy(boardTheme = i)) }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingSwitch("Gợi ý nước đi", "Hiện chấm ở các ô quân có thể đến", vm.settings.showHints) {
                vm.updateSettings(vm.settings.copy(showHints = it))
            }
            SettingSwitch("Rung nhẹ khi đi quân", "", vm.settings.haptics) {
                vm.updateSettings(vm.settings.copy(haptics = it))
            }

            SectionTitle("AI qua API")
            GhostButton("API key và model", { vm.go(com.customchess.vm.Screen.AI_SETTINGS) }, Modifier.fillMaxWidth())

            SectionTitle("Giới thiệu")
            Text2("Custom Chess ${BuildConfig.VERSION_NAME}", Type.Body)
            Text2("Mã nguồn tại github.com/7th8yuu/Custom-Chess", Type.Small)
            Spacer(Modifier.height(28.dp))
        }
    }
}

