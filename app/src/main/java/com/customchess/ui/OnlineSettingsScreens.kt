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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.customchess.vm.Screen

// ---------------------------------------------------------------------------------- đấu online

@Composable
fun OnlineScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val li = vm.lichessUi
    val google = rememberGoogleSignIn(vm)
    var guide by remember { mutableStateOf(false) }
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

            LichessLobby(vm)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (guide) GoogleGuideDialog { guide = false }
}
