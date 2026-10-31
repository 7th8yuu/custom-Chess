package com.customchess.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customchess.core.Pc
import com.customchess.data.GoogleAuth
import com.customchess.vm.AppViewModel

/** Trả về hàm bắt đầu đăng nhập Google (mở cửa sổ chọn tài khoản). Gọi trong composable. */
@Composable
fun rememberGoogleSignIn(vm: AppViewModel): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.onGoogleLegacyResult(result.data)
    }
    return { vm.beginGoogle(ctx.findActivity()) { intent -> launcher.launch(intent) } }
}

/** Nút đăng nhập theo nhà cung cấp: biểu tượng ở đầu, chữ ở giữa. [filled] = nút chính (nền màu nhấn). */
@Composable
fun ProviderButton(
    text: String,
    badge: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(if (filled) Pal.Gold else Pal.Panel)
            .border(BorderStroke(1.dp, if (filled) Pal.Gold else Pal.Hair), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { badge() }
        Text2(
            text, Type.Button.copy(color = if (filled) Pal.OnGold else Pal.Egg),
            Modifier.weight(1f).padding(end = 30.dp),
        )
    }
}

/** Biểu tượng quân mã của Lichess (luôn vẽ bằng bộ quân gốc cho đồng nhất). */
@Composable
fun LichessBadge() {
    PieceIcon(-Pc.KNIGHT, 30.dp, style = PIECE_STYLES[0])
}

@Composable
fun GoogleBadge() {
    Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        Text2("G", Type.Button.copy(color = Color(0xFF4285F4), fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif))
    }
}

/** Chữ bấm được, gạch chân. */
@Composable
fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text2(text, Type.Small.copy(color = Pal.Gold, textDecoration = TextDecoration.Underline))
    }
}

@Composable
private fun CopyRow(label: String, value: String) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text2(label, Type.Small)
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(Pal.PanelHi).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text2(value, Type.Body.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp), Modifier.weight(1f).padding(vertical = 10.dp))
            TextButton(onClick = { clipboard.setText(AnnotatedString(value)) }) { Text2("Chép", Type.Button.copy(color = Pal.Gold, fontSize = 14.sp)) }
        }
    }
}

/** Hướng dẫn tạo OAuth client kiểu Android cho Google Sign-In, kèm tên gói và SHA-1 của bản cài hiện tại. */
@Composable
fun GoogleGuideDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val sha1 = GoogleAuth.signingSha1(ctx) ?: "(không đọc được, xem README)"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Panel,
        title = { Text2("Bật đăng nhập Google", Type.Title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text2(
                    "Google chỉ cho đăng nhập khi biết ứng dụng của bạn. Làm một lần, mất khoảng 3 phút, không cần build lại:",
                    Type.Small,
                )
                Spacer(Modifier.height(8.dp))
                Text2(
                    "1. Vào Google Cloud Console, tạo một project. Ở APIs & Services > OAuth consent screen, chọn External, " +
                        "điền tên ứng dụng và thêm email Google của bạn vào Test users.\n" +
                        "2. Credentials > Create credentials > OAuth client ID, loại Android.\n" +
                        "3. Dán đúng hai giá trị dưới đây rồi bấm Create.\n" +
                        "4. Quay lại đây và bấm Đăng nhập bằng Google.",
                    Type.Body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                )
                CopyRow("Package name", GoogleAuth.PACKAGE)
                CopyRow("SHA-1 certificate fingerprint", sha1)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text2("Đóng", Type.Button.copy(color = Pal.Gold)) } },
        dismissButton = {
            TextButton(onClick = { runCatching { uri.openUri("https://console.cloud.google.com/apis/credentials") } }) {
                Text2("Mở Google Cloud", Type.Button.copy(color = Pal.EggDim))
            }
        },
    )
}
