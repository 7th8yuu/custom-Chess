package com.customchess.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.customchess.core.Move
import com.customchess.core.Pc
import com.customchess.core.Position
import com.customchess.core.squareFromName
import com.customchess.vm.AppViewModel

private const val PREVIEW_FEN = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 4 5"

/** Màn hình Giao diện: xem trước bàn cờ, chế độ sáng/tối, bàn cờ (kể cả bàn cờ ảnh), bộ quân. */
@Composable
fun AppearanceScreen(vm: AppViewModel, theme: BoardTheme) {
    val s = vm.settings
    val position = remember { Position.fromFen(PREVIEW_FEN) ?: Position.start() }
    val selected = remember { squareFromName("f3") }
    val targets = remember { setOf("e5", "g5", "h4", "d4", "g1").map { squareFromName(it) }.toSet() }
    val last = remember { Move(squareFromName("f1"), squareFromName("c4")) }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Giao diện", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ChessBoard(
                board = position.board, flipped = false, lastMove = last, checkSquare = -1,
                selected = selected, targets = targets, plyCount = 0, theme = theme,
                modifier = Modifier.fillMaxWidth(), interactive = false, showHints = true,
            )
            Spacer(Modifier.height(14.dp))
            PaletteStrip()

            SectionTitle("Chế độ")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Tối", s.themeMode == 0, { vm.updateSettings(s.copy(themeMode = 0)) })
                Pill("Sáng", s.themeMode == 1, { vm.updateSettings(s.copy(themeMode = 1)) })
                Pill("Theo hệ thống", s.themeMode == 2, { vm.updateSettings(s.copy(themeMode = 2)) })
            }

            SectionTitle("Bàn cờ")
            Text2("Chọn bàn cờ ảnh, màu cả ứng dụng sẽ đổi theo màu chủ đạo của ảnh.", Type.Small)
            Spacer(Modifier.height(10.dp))
            val photos = BOARD_THEMES.withIndex().filter { it.value.isPhoto }
            val classics = BOARD_THEMES.withIndex().filter { !it.value.isPhoto }
            for (row in photos.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((i, t) in row) {
                        BoardCard(t, s.boardTheme == i, { vm.updateSettings(s.copy(boardTheme = i)) }, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((i, t) in classics) {
                    BoardCard(t, s.boardTheme == i, { vm.updateSettings(s.copy(boardTheme = i)) }, Modifier.weight(1f), compact = true)
                }
            }
            Spacer(Modifier.height(6.dp))
            SettingSwitch(
                "Màu ứng dụng theo bàn cờ",
                "Tắt để dùng màu vàng sơn mài mặc định cho mọi bàn cờ",
                s.adaptColors,
            ) { vm.updateSettings(s.copy(adaptColors = it)) }

            SectionTitle("Quân cờ")
            for (row in PIECE_STYLES.withIndex().toList().chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((i, st) in row) {
                        PieceCard(st, s.pieceSet == i, { vm.updateSettings(s.copy(pieceSet = i)) }, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }

            SectionTitle("Hiển thị")
            SettingSwitch("Tọa độ trên bàn cờ", "Chữ a-h và số 1-8 ở mép bàn cờ", s.showCoords) {
                vm.updateSettings(s.copy(showCoords = it))
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Dải màu của bảng màu hiện hành: nhấn, phụ, bổ trợ, nền thẻ. */
@Composable
private fun PaletteStrip() {
    val r = Skin.roles
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.Panel)
            .border(BorderStroke(1.dp, Pal.Hair), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text2("Màu hiện tại", Type.Small, Modifier.weight(1f))
        for (c in listOf(r.primary, r.secondary, r.tertiary, r.primaryContainer, r.surfaceContainerHighest)) {
            Box(
                Modifier.padding(start = 8.dp).size(26.dp).clip(CircleShape).background(Color(c))
                    .border(BorderStroke(1.dp, Pal.Hair), CircleShape),
            )
        }
    }
}

@Composable
private fun CheckBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(24.dp).clip(CircleShape).background(Pal.Gold), contentAlignment = Alignment.Center) {
        val tick = Pal.OnGold
        Canvas(Modifier.size(24.dp)) {
            val p = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.68f)
                lineTo(size.width * 0.74f, size.height * 0.34f)
            }
            drawPath(p, tick, style = Stroke(width = size.width * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Ô chọn bàn cờ: ảnh (hoặc bàn cờ nhỏ), tên và chấm màu chủ đạo sẽ áp cho ứng dụng. */
@Composable
private fun BoardCard(t: BoardTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .aspectRatio(if (compact) 1.05f else 1.35f)
            .clip(shape)
            .border(BorderStroke(if (selected) 2.5.dp else 1.dp, if (selected) Pal.Gold else Pal.Hair), shape)
            .clickable(onClick = onClick),
    ) {
        if (t.isPhoto) {
            Image(painterResource(t.thumb), contentDescription = t.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(t.frame)
                val pad = size.width * 0.08f
                val cell = (size.width - 2 * pad) / 4f
                for (r in 0 until 3) for (c in 0 until 4) {
                    drawRect(
                        if ((r + c) % 2 == 0) t.light else t.dark,
                        Offset(pad + c * cell, pad + r * cell), Size(cell, cell),
                    )
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(11.dp).clip(CircleShape).background(Color(t.seed)).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)), CircleShape))
            Spacer(Modifier.width(7.dp))
            Text2(t.name, Type.Small.copy(color = Color.White))
        }
        if (selected) CheckBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

/** Ô chọn bộ quân: hai quân của bộ đó trên hai ô trung tính. */
@Composable
private fun PieceCard(style: PieceStyle, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Pal.PanelHi else Pal.Panel)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Pal.Gold else Pal.Hair), shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))) {
            Box(Modifier.weight(1f).aspectRatio(1f).background(Color(0xFFE8DCC4)), contentAlignment = Alignment.Center) {
                PieceIcon(Pc.KNIGHT, 52.dp, style = style)
            }
            Box(Modifier.weight(1f).aspectRatio(1f).background(Color(0xFF5E5148)), contentAlignment = Alignment.Center) {
                PieceIcon(-Pc.KNIGHT, 52.dp, style = style)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text2(style.name, Type.Body.copy(color = if (selected) Pal.Gold else Pal.Egg), Modifier.weight(1f))
            if (selected) CheckBadge()
        }
    }
}
