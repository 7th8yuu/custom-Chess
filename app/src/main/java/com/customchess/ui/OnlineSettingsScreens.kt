package com.customchess.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import com.customchess.BuildConfig
import com.customchess.vm.AppViewModel
import com.customchess.vm.ONLINE_PRESETS
import com.customchess.vm.Screen

// ---------------------------------------------------------------------------------- đấu online

@Composable
fun OnlineScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val li = vm.lichessUi
    val google = rememberGoogleSignIn(vm)
    var guide by remember { mutableStateOf(false) }
    var time by remember { mutableIntStateOf(0) }
    var rated by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var tokenOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.enterOnline() }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Đấu online", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!vm.canPlayOnline) {
                // khách: mở khóa bằng cách đăng nhập
                Text2("Đăng nhập để đấu online", Type.Title)
                Spacer(Modifier.height(8.dp))
                Text2(
                    "Bạn đang ở chế độ khách nên chưa ghép trận với người lạ được. Đăng nhập Lichess (nhanh nhất, ứng dụng tự lấy quyền chơi) " +
                        "hoặc Google để mở khóa. Chơi với máy, với AI bằng API key và với bạn bè vẫn dùng bình thường.",
                    Type.Body.copy(color = Pal.EggDim),
                )
                Spacer(Modifier.height(20.dp))
                ProviderButton(
                    if (li.busy) "Đang xác nhận với Lichess..." else "Đăng nhập bằng Lichess",
                    badge = { LichessBadge() },
                    onClick = { vm.startLichessLogin(ctx.findActivity()) },
                    enabled = !li.busy && !vm.authBusy,
                    filled = true,
                )
                ErrorText(li.error)
                Spacer(Modifier.height(12.dp))
                ProviderButton(
                    if (vm.authBusy) "Đang đăng nhập..." else "Đăng nhập bằng Google",
                    badge = { GoogleBadge() },
                    onClick = google,
                    enabled = !vm.authBusy && !li.busy,
                )
                ErrorText(vm.authError)
                LinkText("Hướng dẫn bật đăng nhập Google", { guide = true })
                return@Column
            }

            if (!li.connected) {
                // đã đăng nhập Google nhưng chưa nối với Lichess
                Text2("Ghép trận với người chơi thật", Type.Title)
                Spacer(Modifier.height(8.dp))
                Text2(
                    "Custom Chess kết nối với Lichess, nơi có hàng nghìn người chơi mỗi lúc và cho phép ứng dụng bên thứ ba ghép trận miễn phí. " +
                        "Chess.com không có cổng như vậy cho ứng dụng ngoài, nên chưa kết nối được.",
                    Type.Body.copy(color = Pal.EggDim),
                )
                Spacer(Modifier.height(20.dp))
                ProviderButton(
                    if (li.busy) "Đang xác nhận với Lichess..." else "Kết nối tài khoản Lichess",
                    badge = { LichessBadge() },
                    onClick = { vm.startLichessLogin(ctx.findActivity()) },
                    enabled = !li.busy,
                    filled = true,
                )
                Text2("Một tab trình duyệt mở ra để bạn cấp quyền chơi cờ, xong tự quay lại ứng dụng.", Type.Small, Modifier.padding(top = 8.dp))
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

            // đã kết nối: hồ sơ và ghép trận
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pal.Panel)
                    .border(BorderStroke(1.dp, Pal.Hair), RoundedCornerShape(16.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(li.name.firstOrNull()?.uppercase() ?: "L", size = 46.dp)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text2(li.name, Type.Heading)
                    Text2(
                        li.ratingText.ifEmpty { "Đã kết nối Lichess" },
                        Type.Small.copy(color = if (li.ratingText.isEmpty()) Pal.Jade else Pal.EggDim),
                    )
                }
                GhostButton(if (vm.user?.kind == com.customchess.data.UserKind.LICHESS) "Đăng xuất" else "Ngắt", { vm.lichessLogout() })
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
    if (guide) GoogleGuideDialog { guide = false }
}

// ---------------------------------------------------------------------------------- cài đặt

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val user = vm.user
    val li = vm.lichessUi
    val google = rememberGoogleSignIn(vm)
    var guide by remember { mutableStateOf(false) }
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
                        when {
                            user == null || user.isGuest -> "Chế độ khách, không đấu online được"
                            user.kind == com.customchess.data.UserKind.LICHESS -> "Tài khoản Lichess" + li.ratingText.let { if (it.isEmpty()) "" else "  ·  $it" }
                            else -> user.email
                        },
                        Type.Small,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (user == null || user.isGuest) {
                ProviderButton(
                    if (li.busy) "Đang xác nhận với Lichess..." else "Đăng nhập bằng Lichess",
                    badge = { LichessBadge() },
                    onClick = { vm.startLichessLogin(ctx.findActivity()) },
                    enabled = !li.busy && !vm.authBusy,
                    filled = true,
                )
                ErrorText(li.error)
                Spacer(Modifier.height(10.dp))
                ProviderButton(
                    if (vm.authBusy) "Đang đăng nhập..." else "Đăng nhập bằng Google",
                    badge = { GoogleBadge() },
                    onClick = google,
                    enabled = !vm.authBusy && !li.busy,
                )
                ErrorText(vm.authError)
                LinkText("Hướng dẫn bật đăng nhập Google", { guide = true })
                Spacer(Modifier.height(4.dp))
                GhostButton("Quay về màn hình đăng nhập", { vm.signOut() }, Modifier.fillMaxWidth())
            } else {
                if (user.kind == com.customchess.data.UserKind.GOOGLE) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.Panel)
                            .border(BorderStroke(1.dp, Pal.Hair), RoundedCornerShape(14.dp)).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text2("Lichess", Type.Body)
                            Text2(if (li.connected) "Đã kết nối: ${li.name}" else "Chưa kết nối, cần để đấu online", Type.Small.copy(color = if (li.connected) Pal.Jade else Pal.EggDim))
                        }
                        if (li.connected) GhostButton("Ngắt", { vm.lichessLogout() })
                        else GhostButton("Kết nối", { vm.startLichessLogin(ctx.findActivity()) }, enabled = !li.busy)
                    }
                    ErrorText(li.error)
                    Spacer(Modifier.height(10.dp))
                }
                GhostButton("Đăng xuất", { vm.signOut() }, Modifier.fillMaxWidth(), danger = true)
            }

            SectionTitle("Giao diện")
            GhostButton("Màu sắc, bàn cờ ảnh, bộ quân", { vm.go(Screen.APPEARANCE) }, Modifier.fillMaxWidth())

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

            SectionTitle("Ván cờ")
            SettingSwitch("Gợi ý nước đi", "Hiện chấm ở các ô quân có thể đến", vm.settings.showHints) {
                vm.updateSettings(vm.settings.copy(showHints = it))
            }
            SettingSwitch("Rung nhẹ khi đi quân", "", vm.settings.haptics) {
                vm.updateSettings(vm.settings.copy(haptics = it))
            }

            SectionTitle("AI qua API")
            GhostButton("API key và model", { vm.go(Screen.AI_SETTINGS) }, Modifier.fillMaxWidth())

            SectionTitle("Giới thiệu")
            Text2("Custom Chess ${BuildConfig.VERSION_NAME}", Type.Body)
            Text2("Mã nguồn tại github.com/7th8yuu/Custom-Chess", Type.Small)
            Spacer(Modifier.height(28.dp))
        }
    }
    if (guide) GoogleGuideDialog { guide = false }
}
