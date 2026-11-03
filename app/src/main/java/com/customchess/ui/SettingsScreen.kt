package com.customchess.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customchess.BuildConfig
import com.customchess.data.Settings
import com.customchess.data.UserKind
import com.customchess.vm.AppViewModel
import com.customchess.vm.DriveOp
import com.customchess.vm.Screen

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionTitle(title)
    GlassCard(padding = 14.dp) { content() }
}

@Composable
private fun PillRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val s = vm.settings
    val user = vm.user
    val li = vm.lichessUi
    val google = rememberGoogleSignIn(vm)
    var guide by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(s.playerName) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    fun set(f: Settings.() -> Settings) = vm.updateSettings(vm.settings.f())

    // Màn hình xin quyền Drive của Google (lần đầu sao lưu).
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        vm.onDriveConsent(if (r.resultCode == Activity.RESULT_OK) r.data else null)
    }
    LaunchedEffect(vm.driveConsent) {
        vm.driveConsent?.let { consent.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Cài đặt", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // ---------------------------------------------------------------- tài khoản
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user?.initial ?: "?", size = 50.dp)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(user?.name ?: "Chưa đăng nhập", style = Type.Heading)
                        Text(
                            when {
                                user == null || user.isGuest -> "Chế độ khách, chưa đấu online được"
                                user.kind == UserKind.LICHESS -> "Tài khoản Lichess" + li.ratingText.let { if (it.isEmpty()) "" else "  ·  $it" }
                                else -> user.email
                            },
                            style = Type.Small,
                        )
                    }
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
            } else {
                if (user.kind == UserKind.GOOGLE) {
                    GlassCard(padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Lichess", style = Type.Body)
                                Text(
                                    if (li.connected) "Đã kết nối: ${li.name}" else "Chưa kết nối, cần để đấu online",
                                    style = Type.Small.copy(color = if (li.connected) Pal.Jade else Pal.EggDim),
                                )
                            }
                            if (li.connected) GhostButton("Ngắt", { vm.lichessLogout() })
                            else GhostButton("Kết nối", { vm.startLichessLogin(ctx.findActivity()) }, enabled = !li.busy)
                        }
                    }
                    ErrorText(li.error)
                    Spacer(Modifier.height(10.dp))
                }
                GhostButton("Đăng xuất", { vm.signOut() }, Modifier.fillMaxWidth(), danger = true)
            }

            SectionTitle("Tên hiển thị")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24); set { copy(playerName = name.trim()) } },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(user?.name ?: "Bạn", style = Type.Small) },
                colors = fieldColors(),
            )
            Text("Tên này hiện cho bạn bè khi chơi qua Wi-Fi hoặc Bluetooth.", style = Type.Small, modifier = Modifier.padding(top = 6.dp))

            Spacer(Modifier.height(8.dp))
            GhostButton("Giao diện: bàn cờ, ảnh nền, bộ quân, màu sắc", { vm.go(Screen.APPEARANCE) }, Modifier.fillMaxWidth())

            // ---------------------------------------------------------------- hiệu ứng
            Group("Hiệu ứng và hiệu năng") {
                SettingSwitch("Bật hiệu ứng chuyển động", "Công tắc tổng. Tắt để app nhẹ và tiết kiệm pin hơn", s.animations) { set { copy(animations = it) } }
                SettingSwitch("Kính mờ (liquid glass)", "Thẻ và nút trong suốt có vệt sáng. Tắt thì dùng nền phẳng", s.glass) { set { copy(glass = it) } }
                SliderRow(
                    "Độ trong của kính", "${s.glassStrength}%", s.glassStrength.toFloat(), 10f..100f,
                    { set { copy(glassStrength = it.toInt()) } }, enabled = s.glass,
                )
                SettingSwitch("Nền Aurora chuyển động", "Ba quầng màu trôi chậm sau lưng", s.aurora, enabled = s.animations) { set { copy(aurora = it) } }
                SettingSwitch("Hiệu ứng chuyển màn hình", "Trượt và mờ dần khi đổi màn hình", s.transitions, enabled = s.animations) { set { copy(transitions = it) } }
                SettingSwitch("Quân cờ trượt khi đi", "", s.slideAnim, enabled = s.animations) { set { copy(slideAnim = it) } }
                SliderRow(
                    "Thời gian trượt", "${s.moveAnimMs} ms", s.moveAnimMs.toFloat(), 60f..500f,
                    { set { copy(moveAnimMs = (it / 10).toInt() * 10) } }, enabled = s.animations && s.slideAnim,
                )
                SettingSwitch("Tia sáng khi ăn quân", "", s.burst, enabled = s.animations) { set { copy(burst = it) } }
                Text("Kiểu nền", style = Type.Body, modifier = Modifier.padding(top = 8.dp))
                PillRow {
                    Pill("Ảnh bàn cờ mờ", s.bgMode == 0, { set { copy(bgMode = 0) } })
                    Pill("Dải màu", s.bgMode == 1, { set { copy(bgMode = 1) } })
                    Pill("Phẳng", s.bgMode == 2, { set { copy(bgMode = 2) } })
                }
                Text("Giới hạn tốc độ khung hình", style = Type.Body, modifier = Modifier.padding(top = 8.dp))
                Text("Là gợi ý cho hệ thống; máy không hỗ trợ tần số đó sẽ dùng mức gần nhất. Hiệu ứng nền luôn tự giới hạn tối đa 30 khung hình/giây.", style = Type.Small)
                PillRow {
                    for ((label, v) in listOf("Tự động" to 0, "30" to 30, "60" to 60, "90" to 90, "120" to 120)) {
                        Pill(label, s.fps == v, { set { copy(fps = v) } })
                    }
                }
                SettingSwitch("Tiết kiệm pin", "Giới hạn 30 khung hình/giây, tắt Aurora và tia sáng", s.batterySaver) { set { copy(batterySaver = it) } }
                Text(
                    "Nếu bạn bật \"Xóa hoạt ảnh\" trong cài đặt hệ thống của điện thoại, ứng dụng cũng tự tắt hiệu ứng.",
                    style = Type.Small, modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ---------------------------------------------------------------- âm thanh
            Group("Âm thanh và rung") {
                SettingSwitch("Âm thanh", "Tiếng đi quân, ăn quân, chiếu, thắng, thua", s.sound) { set { copy(sound = it) } }
                SliderRow("Âm lượng", "${s.volume}%", s.volume.toFloat(), 0f..100f, { set { copy(volume = it.toInt()) } }, enabled = s.sound)
                Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Nghe thử đi quân", { Sfx.play(Snd.MOVE) }, enabled = s.sound)
                    GhostButton("Nghe thử ăn quân", { Sfx.play(Snd.CAPTURE) }, enabled = s.sound)
                }
                SettingSwitch("Rung nhẹ khi đi quân", "", s.haptics) { set { copy(haptics = it) } }
            }

            // ---------------------------------------------------------------- ván cờ
            Group("Ván cờ") {
                SettingSwitch("Gợi ý nước đi", "Hiện chấm ở các ô quân có thể đến", s.showHints) { set { copy(showHints = it) } }
                SettingSwitch("Tô nước đi cuối", "Tô sáng ô đi và ô đến của nước vừa rồi", s.highlightLast) { set { copy(highlightLast = it) } }
                SettingSwitch("Hiện quân đã bắt", "Quân đã ăn và chênh lệch điểm cạnh tên người chơi", s.showCaptured) { set { copy(showCaptured = it) } }
                SettingSwitch("Tự phong Hậu", "Không hỏi khi tốt tới hàng cuối", s.autoQueen) { set { copy(autoQueen = it) } }
                SettingSwitch("Cho phép đi lại", "Nút Đi lại trong ván với máy, bot và AI", s.allowTakeback) { set { copy(allowTakeback = it) } }
                SettingSwitch("Thanh đánh giá thế cờ", "Engine chấm điểm thế cờ khi đang chơi (tốn pin hơn)", s.evalBar) { set { copy(evalBar = it) } }
                SettingSwitch("Phân tích nước cờ trong ván", "Nút Phân tích: engine chỉ ra các nước tốt nhất. Chỉ hiện khi chơi với máy, bot, AI hoặc cùng một máy, còn ván online và với bạn bè thì sau khi kết thúc", s.analysis) { set { copy(analysis = it) } }
                SettingSwitch("AI giải thích khi phân tích", "Gửi thế cờ cho AI (Gemini mặc định) để giải thích bằng lời. Cần API key, tắt thì chỉ dùng engine offline", s.analysisAi, enabled = s.analysis) { set { copy(analysisAi = it) } }
                SettingSwitch("Giữ màn hình sáng", "Khi đang chơi, giải câu đố hoặc xem lại ván", s.keepScreenOn) { set { copy(keepScreenOn = it) } }
            }

            Group("Câu đố") {
                SettingSwitch("Cho phép gợi ý", "Hiện nút Gợi ý khi giải câu đố (dùng gợi ý thì không tính điểm)", s.puzzleHints) { set { copy(puzzleHints = it) } }
                SettingSwitch("Tự sang câu kế tiếp", "Sau khi giải đúng", s.puzzleAutoNext) { set { copy(puzzleAutoNext = it) } }
            }

            Group("Trang chủ") {
                SettingSwitch("Câu đố hằng ngày", "", s.homeDaily) { set { copy(homeDaily = it) } }
                SettingSwitch("Chuỗi ngày chơi", "", s.homeStreak) { set { copy(homeStreak = it) } }
                SettingSwitch("Ván gần đây", "", s.homeRecent) { set { copy(homeRecent = it) } }
                SettingSwitch("Thống kê", "", s.homeStats) { set { copy(homeStats = it) } }
            }

            // ---------------------------------------------------------------- sao lưu
            Group("Sao lưu Google Drive") {
                Text(
                    "Lưu bàn cờ ảnh, bộ quân tự thêm và tiến độ lên thư mục ẩn của ứng dụng trong Google Drive của bạn. " +
                        "Ứng dụng chỉ xin quyền thư mục riêng của nó, không đọc tệp nào khác.",
                    style = Type.Small,
                )
                if (vm.lastBackupText.isNotEmpty()) Text(vm.lastBackupText, style = Type.Small.copy(color = Pal.Gold), modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                if (vm.backupBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 10.dp).height(20.dp), color = Pal.Gold, strokeWidth = 2.dp)
                        Text("Đang làm việc với Google Drive...", style = Type.Small)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("Sao lưu ngay", { vm.startDrive(DriveOp.BACKUP) }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    GhostButton("Khôi phục từ Drive", { confirmRestore = true }, Modifier.fillMaxWidth())
                }
                val msg = vm.backupMsg
                if (msg != null) Text(msg, style = Type.Small.copy(color = if (vm.backupError) Pal.Alert else Pal.Jade), modifier = Modifier.padding(top = 8.dp))
                SettingSwitch("Tự sao lưu", "Sau mỗi lần thêm hoặc xóa bàn cờ, bộ quân", s.autoBackup) { set { copy(autoBackup = it) } }
            }

            Group("AI qua API") {
                GhostButton("API key và model", { vm.go(Screen.AI_SETTINGS) }, Modifier.fillMaxWidth())
            }

            Group("Dữ liệu") {
                GhostButton("Xóa lịch sử ván (${vm.history.size})", { confirmClear = true }, Modifier.fillMaxWidth(), enabled = vm.history.isNotEmpty())
                Spacer(Modifier.height(8.dp))
                GhostButton("Đặt lại mọi cài đặt về mặc định", { confirmReset = true }, Modifier.fillMaxWidth(), danger = true)
            }

            Group("Giới thiệu") {
                Text("Custom Chess ${BuildConfig.VERSION_NAME}", style = Type.Body)
                Text("Mã nguồn tại github.com/7th8yuu/Custom-Chess", style = Type.Small)
                Text(
                    "Câu đố có sẵn do ứng dụng tự tạo và kiểm tra. Câu đố online và giải đấu lấy từ Lichess (lichess.org).",
                    style = Type.Small, modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (guide) GoogleGuideDialog { guide = false }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            containerColor = Pal.Panel,
            title = { Text("Khôi phục từ Drive?", style = Type.Title) },
            text = { Text("Bàn cờ ảnh và bộ quân tự thêm hiện có sẽ được thay bằng bản sao lưu.", style = Type.Body) },
            confirmButton = { TextButton(onClick = { confirmRestore = false; vm.startDrive(DriveOp.RESTORE) }) { Text("Khôi phục", style = Type.Button.copy(color = Pal.Gold)) } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Hủy", style = Type.Button.copy(color = Pal.EggDim)) } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Pal.Panel,
            title = { Text("Xóa lịch sử ván?", style = Type.Title) },
            text = { Text("Mọi ván đã lưu sẽ bị xóa khỏi máy.", style = Type.Body) },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearHistory() }) { Text("Xóa", style = Type.Button.copy(color = Pal.Alert)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Hủy", style = Type.Button.copy(color = Pal.EggDim)) } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Pal.Panel,
            title = { Text("Đặt lại cài đặt?", style = Type.Title) },
            text = { Text("Các công tắc và thanh trượt về mặc định. Tên, bàn cờ, bộ quân và API key được giữ nguyên.", style = Type.Body) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    val c = vm.settings
                    vm.updateSettings(Settings(playerName = c.playerName, provider = c.provider, boardId = c.boardId, pieceId = c.pieceId, engineLevel = c.engineLevel))
                }) { Text("Đặt lại", style = Type.Button.copy(color = Pal.Alert)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Hủy", style = Type.Button.copy(color = Pal.EggDim)) } },
        )
    }
}
