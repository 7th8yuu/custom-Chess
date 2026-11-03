package com.customchess.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customchess.data.Progress
import com.customchess.data.PuzzleBank
import com.customchess.data.UserKind
import com.customchess.vm.AppViewModel
import com.customchess.vm.PuzzleMode
import com.customchess.vm.Screen

// ---------------------------------------------------------------------------------- khung dùng chung

/** Khung cuộn cho các màn hình gốc (có thanh điều hướng dưới). */
@Composable
fun TabColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        content()
        Spacer(Modifier.height(NAV_SPACE))
    }
}

@Composable
fun TabHeader(title: String, subtitle: String = "", trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Display.copy(fontSize = 30.sp, lineHeight = 34.sp))
            if (subtitle.isNotEmpty()) Text(subtitle, style = Type.Small, modifier = Modifier.padding(top = 2.dp))
        }
        trailing()
    }
}

/** Ô chức năng vuông bằng kính: biểu tượng, tên và mô tả ngắn. */
@Composable
fun FeatureTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Pal.Gold,
    badge: String? = null,
) {
    GlassBox(modifier, RoundedCornerShape(26.dp), tint = tint, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, tint = tint, size = 46.dp)
                Spacer(Modifier.weight(1f))
                if (badge != null) Pill2(badge)
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = Type.Heading)
            Text(subtitle, style = Type.Small, maxLines = 2)
        }
    }
}

/** Nhãn nhỏ bo tròn (số lượng, "Mới"...). */
@Composable
fun Pill2(text: String, tint: Color = Pal.Gold) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.2f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, style = Type.Small.copy(color = tint, fontSize = 12.sp, lineHeight = 14.sp))
    }
}

/** Hàng chức năng rộng bằng kính. [locked] làm mờ và hiện ổ khóa. */
@Composable
fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Pal.Gold,
    locked: Boolean = false,
    trailing: String? = null,
) {
    GlassBox(modifier.fillMaxWidth().alpha(if (locked) 0.7f else 1f), RoundedCornerShape(24.dp), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint = tint, size = 48.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, style = Type.Heading)
                if (subtitle.isNotEmpty()) Text(subtitle, style = Type.Small)
            }
            if (trailing != null) Pill2(trailing, tint)
            Icon(
                if (locked) Icons.Rounded.Lock else Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                tint = Pal.EggDim, modifier = Modifier.padding(start = 6.dp).size(22.dp),
            )
        }
    }
}

/** Công tắc chọn một trong vài mục, có viên thuốc trượt. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    GlassBox(modifier.fillMaxWidth().height(52.dp), shape) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val cell = maxWidth / options.size
            val x by animateFloatAsState(
                selected.toFloat(),
                if (Fx.anim) spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow) else snap(), label = "seg",
            )
            Box(
                Modifier.offset(cell * x, 0.dp).width(cell).fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp)).background(Pal.Gold),
            )
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { i, o ->
                    Box(Modifier.weight(1f).fillMaxHeight().pressable({ onSelect(i) }, scale = 0.97f), contentAlignment = Alignment.Center) {
                        Text(o, style = Type.Button.copy(color = if (i == selected) Pal.OnGold else Pal.Egg, fontSize = 15.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Type.Title.copy(fontSize = 24.sp, color = Pal.Gold), textAlign = TextAlign.Center)
        Text(label, style = Type.Small.copy(fontSize = 12.sp), textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------------------------- trang chủ

@Composable
fun HomeScreen(vm: AppViewModel, theme: BoardTheme) {
    val s = vm.settings
    val prog = vm.progress
    val ctx = LocalContext.current
    val streak = prog.streakNow()
    TabColumn {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(vm.user?.initial ?: "?", size = 46.dp, modifier = Modifier.pressable({ vm.selectTab(Screen.PROFILE) }))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Xin chào", style = Type.Small)
                Text(vm.me, style = Type.Title, maxLines = 1)
            }
            if (s.homeStreak) {
                GlassBox(Modifier.padding(end = 8.dp), RoundedCornerShape(20.dp), tint = Pal.Tertiary) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocalFireDepartment, null, tint = Pal.Tertiary, modifier = Modifier.size(20.dp))
                        Text("$streak", style = Type.Heading.copy(color = Pal.Tertiary), modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            GlassIconButton(Icons.Rounded.Settings, "Cài đặt", { vm.go(Screen.SETTINGS) })
        }

        GlassCard(padding = 14.dp, tint = Pal.Gold) {
            DemoBoard(theme, Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Chơi ngay", { vm.playSegment = 0; vm.selectTab(Screen.PLAY) })
            Spacer(Modifier.height(10.dp))
            GhostButton("Chơi offline", { vm.playSegment = 1; vm.selectTab(Screen.PLAY) }, Modifier.fillMaxWidth())
        }

        SectionTitle("Bắt đầu")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureTile(Icons.Rounded.Public, "Đấu online", "Ghép trận với người thật", { vm.go(Screen.ONLINE) }, Modifier.weight(1f))
            FeatureTile(Icons.Rounded.SmartToy, "Chơi với bot", "12 bot, từ Bé Tí tới Đại Kiện Tướng", { vm.go(Screen.BOTS) }, Modifier.weight(1f), tint = Pal.Tertiary)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureTile(Icons.Rounded.Wifi, "Chơi với bạn", "Wi-Fi, Bluetooth hoặc chung máy", { vm.go(Screen.FRIENDS) }, Modifier.weight(1f), tint = Pal.Secondary)
            FeatureTile(Icons.Rounded.EmojiEvents, "Giải đấu", "Arena đang diễn ra trên Lichess", { vm.go(Screen.TOURNAMENTS) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureTile(Icons.Rounded.Extension, "Giải câu đố", "Điểm hiện tại ${prog.puzzleRating}", { vm.selectTab(Screen.PUZZLES) }, Modifier.weight(1f), tint = Pal.Tertiary)
            FeatureTile(Icons.Rounded.School, "Bài học", "Từ cách đi quân tới chiến thuật", { vm.selectTab(Screen.LEARN) }, Modifier.weight(1f), tint = Pal.Secondary)
        }

        if (s.homeDaily) {
            SectionTitle("Câu đố hằng ngày")
            val daily = remember { PuzzleBank.dailyFallback(ctx, Progress.today()) }
            val start = remember(daily) { daily?.start() }
            val done = vm.dailyDone
            GlassCard(onClick = { vm.startPuzzles(PuzzleMode.DAILY) }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (daily != null && start != null) {
                        ChessBoard(
                            board = start.board, flipped = !start.whiteToMove, lastMove = daily.setupMove(), checkSquare = -1,
                            selected = -1, targets = emptySet(), plyCount = 0, theme = theme, modifier = Modifier.size(112.dp),
                            interactive = false,
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (done) "Hôm nay bạn đã giải" else "Một câu mỗi ngày", style = Type.Heading)
                        Text(
                            if (done) "Quay lại ngày mai để giữ chuỗi nhé." else "${if (start?.whiteToMove == false) "Đen" else "Trắng"} đi, tìm nước tốt nhất.",
                            style = Type.Small,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (done) Icon(Icons.Rounded.Check, null, tint = Pal.Jade, modifier = Modifier.size(26.dp))
                        else Pill2("Giải ngay")
                    }
                }
            }
        }

        if (s.homeStreak) {
            SectionTitle("Chuỗi ngày chơi")
            GlassCard {
                val today = Progress.today()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (i in 6 downTo 0) {
                        val d = today - i
                        val on = d in prog.days
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(if (on) Pal.Tertiary.copy(alpha = 0.9f) else Pal.Egg.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) Icon(Icons.Rounded.LocalFireDepartment, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Text(if (i == 0) "Nay" else "-$i", style = Type.Small.copy(fontSize = 11.sp), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Text(
                    if (streak > 0) "Bạn đã chơi liên tiếp $streak ngày." else "Chơi một ván hoặc giải một câu đố để bắt đầu chuỗi.",
                    style = Type.Small, modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        if (s.homeRecent && vm.history.isNotEmpty()) {
            SectionTitle("Ván gần đây")
            for (g in vm.history.take(3)) {
                GameRow(g, { vm.openReview(g) })
                Spacer(Modifier.height(8.dp))
            }
            GhostButton("Xem tất cả", { vm.go(Screen.HISTORY) }, Modifier.fillMaxWidth())
        }

        if (s.homeStats) {
            SectionTitle("Thống kê")
            GlassCard {
                Row(Modifier.fillMaxWidth()) {
                    StatBlock("${prog.puzzleRating}", "Điểm câu đố", Modifier.weight(1f))
                    StatBlock("${prog.solved}", "Câu đã giải", Modifier.weight(1f))
                    StatBlock("${prog.played}", "Ván đã chơi", Modifier.weight(1f))
                    StatBlock("${prog.wins}", "Thắng", Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------- tab Chơi

@Composable
fun PlayScreen(vm: AppViewModel) {
    TabColumn {
        TabHeader("Chơi", "Chọn cách bạn muốn đấu")
        SegmentedControl(listOf("Online", "Offline"), vm.playSegment, { vm.playSegment = it })
        Spacer(Modifier.height(16.dp))
        if (vm.playSegment == 0) {
            val li = vm.lichessUi
            ActionRow(
                Icons.Rounded.Public, "Đấu người chơi thật",
                when {
                    !vm.canPlayOnline -> "Đăng nhập Lichess hoặc Google để mở khóa"
                    li.connected -> "Ghép trận ngay trên Lichess" + li.ratingText.let { if (it.isEmpty()) "" else "  ·  $it" }
                    else -> "Kết nối Lichess để ghép trận"
                },
                { vm.go(Screen.ONLINE) }, locked = !vm.canPlayOnline,
            )
            Spacer(Modifier.height(10.dp))
            ActionRow(Icons.Rounded.EmojiEvents, "Giải đấu", "Arena đang diễn ra và sắp diễn ra", { vm.go(Screen.TOURNAMENTS) }, tint = Pal.Tertiary)
            Spacer(Modifier.height(10.dp))
            ActionRow(
                Icons.Rounded.AutoAwesome, "Chơi với AI", "Gemini bằng key của bạn (OpenAI, Claude tùy chọn)",
                { vm.go(Screen.API_SETUP) }, tint = Pal.Secondary,
            )
            Spacer(Modifier.height(10.dp))
            ActionRow(Icons.Rounded.Extension, "Câu đố từ Lichess", "Câu mới mỗi lần, lấy trực tiếp từ Lichess", { vm.startPuzzles(PuzzleMode.ONLINE) })
            Spacer(Modifier.height(14.dp))
            InfoCard("Chế độ Online cần Internet. Muốn chơi không cần mạng, chuyển sang Offline.")
        } else {
            ActionRow(Icons.Rounded.SmartToy, "Chơi với bot", "12 bot với tính cách riêng", { vm.go(Screen.BOTS) }, tint = Pal.Tertiary, trailing = "12")
            Spacer(Modifier.height(10.dp))
            ActionRow(Icons.Rounded.Memory, "Chơi với máy", "Engine chạy trên máy bạn, 5 cấp độ", { vm.go(Screen.ENGINE_SETUP) })
            Spacer(Modifier.height(10.dp))
            ActionRow(Icons.Rounded.PhoneAndroid, "Hai người một máy", "Đặt điện thoại giữa hai người", { vm.go(Screen.LOCAL_SETUP) }, tint = Pal.Secondary)
            Spacer(Modifier.height(10.dp))
            ActionRow(Icons.Rounded.Wifi, "Bạn bè gần đây", "Qua Wi-Fi hoặc Bluetooth, không cần Internet", { vm.go(Screen.FRIENDS) })
            Spacer(Modifier.height(10.dp))
            ActionRow(Icons.Rounded.Extension, "Giải câu đố", "Bộ câu đố có sẵn trong ứng dụng", { vm.selectTab(Screen.PUZZLES) }, tint = Pal.Tertiary)
            Spacer(Modifier.height(10.dp))
            ActionRow(Icons.Rounded.School, "Bài học", "Học cờ vua từng bước", { vm.selectTab(Screen.LEARN) }, tint = Pal.Secondary)
            Spacer(Modifier.height(10.dp))
            ActionRow(Icons.Rounded.History, "Lịch sử và phân tích", "${vm.history.size} ván đã lưu, xem lại và chấm điểm", { vm.go(Screen.HISTORY) })
        }
    }
}

// ---------------------------------------------------------------------------------- tab Tôi

private class Achievement(val title: String, val desc: String, val done: Boolean)

@Composable
fun ProfileScreen(vm: AppViewModel) {
    val user = vm.user
    val prog = vm.progress
    val li = vm.lichessUi
    TabColumn {
        TabHeader("Hồ sơ", "", trailing = { GlassIconButton(Icons.Rounded.Settings, "Cài đặt", { vm.go(Screen.SETTINGS) }) })
        GlassCard(padding = 18.dp, tint = Pal.Gold) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(user?.initial ?: "?", size = 64.dp)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(vm.me, style = Type.Title, maxLines = 1)
                    Text(
                        when {
                            user == null || user.isGuest -> "Chế độ khách"
                            user.kind == UserKind.LICHESS -> "Tài khoản Lichess"
                            else -> user.email.ifEmpty { "Tài khoản Google" }
                        },
                        style = Type.Small,
                    )
                    if (li.ratingText.isNotEmpty()) Text(li.ratingText, style = Type.Small.copy(color = Pal.Gold))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                StatBlock("${prog.puzzleRating}", "Điểm câu đố", Modifier.weight(1f))
                StatBlock("${prog.wins}-${prog.draws}-${prog.losses}", "Thắng-Hòa-Thua", Modifier.weight(1f))
                StatBlock("${prog.streakNow()}", "Chuỗi ngày", Modifier.weight(1f))
            }
        }

        SectionTitle("Thành tích")
        val list = listOf(
            Achievement("Ván đầu tiên", "Chơi xong một ván", prog.played >= 1),
            Achievement("Chiến thắng đầu tiên", "Thắng một ván", prog.wins >= 1),
            Achievement("Chơi 10 ván", "${prog.played}/10", prog.played >= 10),
            Achievement("Giải 10 câu đố", "${prog.solved}/10", prog.solved >= 10),
            Achievement("Giải 100 câu đố", "${prog.solved}/100", prog.solved >= 100),
            Achievement("Chuỗi 7 ngày", "Chơi 7 ngày liên tiếp", prog.streak >= 7),
            Achievement("Hạ Cáo Tinh", "Thắng bot Cáo Tinh", (prog.botWins["cao"] ?: 0) > 0),
            Achievement("Hạ Đại Kiện Tướng", "Thắng bot mạnh nhất", (prog.botWins["dai"] ?: 0) > 0),
            Achievement("Puzzle Rush 10", "Kỷ lục ${prog.rushBest}/10", prog.rushBest >= 10),
        )
        for (row in list.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (a in row) {
                    GlassBox(Modifier.weight(1f), RoundedCornerShape(20.dp), tint = if (a.done) Pal.Gold else Color.Unspecified) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(if (a.done) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock, tint = if (a.done) Pal.Gold else Pal.EggDim, size = 36.dp)
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(a.title, style = Type.Body.copy(fontSize = 14.sp, lineHeight = 18.sp, color = if (a.done) Pal.Egg else Pal.EggDim), maxLines = 1)
                                Text(a.desc, style = Type.Small.copy(fontSize = 11.sp, lineHeight = 14.sp), maxLines = 1)
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }

        SectionTitle("Của tôi")
        ActionRow(Icons.Rounded.History, "Lịch sử ván", "${vm.history.size} ván đã lưu", { vm.go(Screen.HISTORY) })
        Spacer(Modifier.height(10.dp))
        ActionRow(Icons.Rounded.Palette, "Giao diện", "Bàn cờ, ảnh nền, bộ quân, màu sắc", { vm.go(Screen.APPEARANCE) }, tint = Pal.Tertiary)
        Spacer(Modifier.height(10.dp))
        ActionRow(
            Icons.Rounded.Cloud, "Sao lưu Google",
            if (vm.lastBackupText.isEmpty()) "Lưu bàn cờ và bộ quân tự thêm lên Google Drive" else vm.lastBackupText,
            { vm.go(Screen.SETTINGS) }, tint = Pal.Secondary,
        )
        Spacer(Modifier.height(10.dp))
        ActionRow(Icons.Rounded.Settings, "Cài đặt", "Hiệu ứng, âm thanh, khung hình và mọi tùy chọn", { vm.go(Screen.SETTINGS) })
        Spacer(Modifier.height(16.dp))
        if (user != null && !user.isGuest) GhostButton("Đăng xuất", { vm.signOut() }, Modifier.fillMaxWidth(), danger = true)
        else GhostButton("Đăng nhập", { vm.signOut() }, Modifier.fillMaxWidth())
    }
}
