package com.customchess.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customchess.core.Move
import com.customchess.core.Pc
import com.customchess.core.Position
import com.customchess.core.squareFromName
import com.customchess.vm.AppViewModel

private const val PREVIEW_FEN = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 4 5"

/**
 * Màn hình Giao diện: xem trước bàn cờ, chế độ sáng/tối, bàn cờ (có sẵn và ảnh tự thêm từ máy hoặc từ link),
 * bộ quân (có sẵn và tự thêm bằng JSON, ZIP hoặc link).
 */
@Composable
fun AppearanceScreen(vm: AppViewModel, theme: BoardTheme) {
    val s = vm.settings
    val position = remember { Position.fromFen(PREVIEW_FEN) ?: Position.start() }
    val selected = remember { squareFromName("f3") }
    val targets = remember { setOf("e5", "g5", "h4", "d4", "g1").map { squareFromName(it) }.toSet() }
    val last = remember { Move(squareFromName("f1"), squareFromName("c4")) }

    var url by remember { mutableStateOf("") }
    var pieceUrl by remember { mutableStateOf("") }
    var pasteOpen by remember { mutableStateOf(false) }
    var templateOpen by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addBoardFromUri(uri, "Ảnh ${vm.customBoards.size + 1}")
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addPiecesFromUri(uri, "Bộ quân ${vm.customPieces.size + 1}")
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Giao diện", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ChessBoard(
                board = position.board, flipped = false, lastMove = last, checkSquare = -1,
                selected = selected, targets = targets, plyCount = 0, theme = theme,
                modifier = Modifier.fillMaxWidth(), interactive = false, showHints = true,
                highlightLast = s.highlightLast,
            )
            Spacer(Modifier.height(14.dp))
            PaletteStrip()

            SectionTitle("Chế độ")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Tối", s.themeMode == 0, { vm.updateSettings(s.copy(themeMode = 0)) })
                Pill("Sáng", s.themeMode == 1, { vm.updateSettings(s.copy(themeMode = 1)) })
                Pill("Theo hệ thống", s.themeMode == 2, { vm.updateSettings(s.copy(themeMode = 2)) })
            }

            // ---------------------------------------------------------------- bàn cờ
            SectionTitle("Bàn cờ")
            Text("Chọn bàn cờ ảnh hoặc thêm ảnh của riêng bạn. Màu cả ứng dụng sẽ đổi theo màu chủ đạo của ảnh.", style = Type.Small)
            Spacer(Modifier.height(10.dp))
            val custom = vm.customBoards.map { c ->
                resolveBoard("c:" + c.id, vm.customBoards.toList(), vm.customs::boardPath)
            }
            val all = BOARD_THEMES + custom
            val photos = all.filter { it.isPhoto }
            val classics = all.filter { !it.isPhoto }
            for (row in photos.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (t in row) {
                        BoardCard(
                            t, s.boardId == t.id, { vm.applyBoard(t.id) }, Modifier.weight(1f),
                            onDelete = if (t.id.startsWith(CUSTOM_PREFIX)) ({ vm.deleteCustomBoard(t.id.removePrefix(CUSTOM_PREFIX)) }) else null,
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (t in classics) BoardCard(t, s.boardId == t.id, { vm.applyBoard(t.id) }, Modifier.weight(1f), compact = true)
            }

            GlassCard(Modifier.padding(top = 12.dp), padding = 14.dp) {
                Text("Thêm ảnh làm bàn cờ", style = Type.Heading)
                Text("Ảnh được giữ trong ứng dụng, thu nhỏ còn tối đa 1600 px, và sao lưu lên Google Drive nếu bạn bật.", style = Type.Small)
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    "Chọn ảnh từ thiết bị",
                    { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !vm.customBusy,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it.trim() }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                    label = { Text("Hoặc dán link ảnh trực tiếp (https://...)") },
                )
                Spacer(Modifier.height(8.dp))
                GhostButton("Tải ảnh từ link và áp dụng", { vm.addBoardFromUrl(url, ""); url = "" }, Modifier.fillMaxWidth(), enabled = url.startsWith("http") && !vm.customBusy)
            }

            Spacer(Modifier.height(6.dp))
            SliderRow(
                "Độ đậm lớp phủ lên ô cờ ảnh", "${s.boardVeil}%", s.boardVeil.toFloat(), 0f..160f,
                { vm.updateSettings(s.copy(boardVeil = it.toInt())) },
            )
            SettingSwitch(
                "Màu ứng dụng theo bàn cờ",
                "Tắt để dùng màu vàng sơn mài mặc định cho mọi bàn cờ",
                s.adaptColors,
            ) { vm.updateSettings(s.copy(adaptColors = it)) }

            // ---------------------------------------------------------------- quân cờ
            SectionTitle("Quân cờ")
            val entries: List<Triple<PieceStyle, String?, Boolean>> =
                PIECE_STYLES.map { Triple(it, null, false) } +
                    vm.customPieces.map { c -> Triple(PieceStyleCache.styles[c.id] ?: PIECE_STYLES[0], c.id, PieceStyleCache.styles[c.id] != null) }
            for (row in entries.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((st, cid, ready) in row) {
                        val id = if (cid == null) st.id else CUSTOM_PREFIX + cid
                        PieceCard(
                            style = st, name = if (cid != null) (vm.customPieces.firstOrNull { it.id == cid }?.name ?: st.name) else st.name,
                            selected = s.pieceId == id, onClick = { if (cid == null || ready) vm.applyPieces(id) },
                            modifier = Modifier.weight(1f),
                            onDelete = if (cid != null) ({ vm.deleteCustomPieces(cid) }) else null,
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }

            GlassCard(padding = 14.dp) {
                Text("Thêm bộ quân của bạn", style = Type.Heading)
                Text(
                    "Nhập bằng tệp JSON, tệp ZIP (kèm ảnh PNG đặt tên wP.png ... bK.png), dán JSON, hoặc link tới một trong hai. " +
                        "JSON cho phép đổi màu, nét vẽ, đường vẽ SVG riêng từng quân, hoặc thay hẳn bằng ảnh PNG.",
                    style = Type.Small,
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    "Chọn tệp JSON hoặc ZIP",
                    { pickFile.launch(arrayOf("application/json", "application/zip", "application/x-zip-compressed", "text/plain", "application/octet-stream")) },
                    enabled = !vm.customBusy,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Dán JSON", { pasteOpen = true }, Modifier.weight(1f))
                    GhostButton("Xem mẫu JSON", { templateOpen = true }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pieceUrl, onValueChange = { pieceUrl = it.trim() }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                    label = { Text("Link tới tệp JSON hoặc ZIP") },
                )
                Spacer(Modifier.height(8.dp))
                GhostButton("Tải bộ quân từ link", { vm.addPiecesFromUrl(pieceUrl, "Bộ quân ${vm.customPieces.size + 1}"); pieceUrl = "" }, Modifier.fillMaxWidth(), enabled = pieceUrl.startsWith("http") && !vm.customBusy)
            }

            if (vm.customBusy) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Pal.Gold, strokeWidth = 2.dp)
                    Text("Đang xử lý...", style = Type.Small, modifier = Modifier.padding(start = 10.dp))
                }
            }
            vm.customMsg?.let {
                Text(it, style = Type.Small.copy(color = if (vm.customMsgError) Pal.Alert else Pal.Jade), modifier = Modifier.padding(top = 10.dp))
            }

            SectionTitle("Hiển thị")
            SettingSwitch("Tọa độ trên bàn cờ", "Chữ a-h và số 1-8 ở mép bàn cờ", s.showCoords) {
                vm.updateSettings(s.copy(showCoords = it))
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (pasteOpen) {
        AlertDialog(
            onDismissRequest = { pasteOpen = false },
            containerColor = Pal.Panel,
            title = { Text("Dán JSON bộ quân", style = Type.Title) },
            text = {
                OutlinedTextField(
                    value = pasteText, onValueChange = { pasteText = it },
                    modifier = Modifier.fillMaxWidth().height(220.dp), colors = fieldColors(),
                    textStyle = Type.Small.copy(fontFamily = FontFamily.Monospace, color = Pal.Egg),
                    placeholder = { Text("{ \"name\": \"...\", \"base\": \"royal\", ... }", style = Type.Small) },
                )
            },
            confirmButton = {
                TextButton(onClick = { pasteOpen = false; vm.addPiecesFromText(pasteText, "Bộ quân ${vm.customPieces.size + 1}"); pasteText = "" }, enabled = pasteText.isNotBlank()) {
                    Text("Thêm", style = Type.Button.copy(color = Pal.Gold))
                }
            },
            dismissButton = { TextButton(onClick = { pasteOpen = false }) { Text("Hủy", style = Type.Button.copy(color = Pal.EggDim)) } },
        )
    }
    if (templateOpen) {
        val clip = LocalClipboardManager.current
        val tpl = remember { vm.customs.template() }
        AlertDialog(
            onDismissRequest = { templateOpen = false },
            containerColor = Pal.Panel,
            title = { Text("Mẫu JSON bộ quân", style = Type.Title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Mọi trường đều tùy chọn. Khóa quân: w hoặc b (trắng, đen) cộng với P N B R Q K (tốt, mã, tượng, xe, hậu, vua). " +
                            "\"paths\" là đường vẽ SVG trong khung 100x100. \"images\" nhận link, base64 hoặc tên file trong ZIP.",
                        style = Type.Small,
                    )
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer { Text(tpl, style = Type.Small.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Pal.Egg)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { clip.setText(AnnotatedString(tpl)); pasteText = tpl; templateOpen = false; pasteOpen = true }) {
                    Text("Sao chép và sửa", style = Type.Button.copy(color = Pal.Gold))
                }
            },
            dismissButton = { TextButton(onClick = { templateOpen = false }) { Text("Đóng", style = Type.Button.copy(color = Pal.EggDim)) } },
        )
    }
}

/** Dải màu của bảng màu hiện hành: nhấn, phụ, bổ trợ, nền thẻ. */
@Composable
private fun PaletteStrip() {
    val r = Skin.roles
    GlassCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Màu hiện tại", style = Type.Small, modifier = Modifier.weight(1f))
            for (c in listOf(r.primary, r.secondary, r.tertiary, r.primaryContainer, r.surfaceContainerHighest)) {
                Box(
                    Modifier.padding(start = 8.dp).size(26.dp).clip(CircleShape).background(Color(c))
                        .border(BorderStroke(1.dp, Pal.Hair), CircleShape),
                )
            }
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
private fun BoardCard(
    t: BoardTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onDelete: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .aspectRatio(if (compact) 1.05f else 1.35f)
            .clip(shape)
            .border(BorderStroke(if (selected) 2.5.dp else 1.dp, if (selected) Pal.Gold else Pal.Hair), shape)
            .clickable(onClick = onClick),
    ) {
        if (t.isPhoto) {
            if (t.thumb != 0) {
                Image(painterResource(t.thumb), contentDescription = t.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                val photo = rememberBoardPhoto(t)
                if (photo != null) Image(photo, contentDescription = t.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(t.frameEdge))
            }
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
            Text(t.name, style = Type.Small.copy(color = Color.White), maxLines = 1)
        }
        if (selected) CheckBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        if (onDelete != null) {
            GlassIconButton(Icons.Rounded.Delete, "Xóa bàn cờ này", onDelete, Modifier.align(Alignment.TopStart).padding(6.dp), size = 34.dp, tint = Color.White)
        }
    }
}

/** Ô chọn bộ quân: hai quân của bộ đó trên hai ô trung tính. */
@Composable
private fun PieceCard(
    style: PieceStyle,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Pal.PanelHi else Pal.Panel)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Pal.Gold else Pal.Hair), shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
            Box(Modifier.weight(1f).aspectRatio(1f).background(Color(0xFFE8DCC4)), contentAlignment = Alignment.Center) {
                PieceIcon(Pc.KNIGHT, 52.dp, style = style)
            }
            Box(Modifier.weight(1f).aspectRatio(1f).background(Color(0xFF5E5148)), contentAlignment = Alignment.Center) {
                PieceIcon(-Pc.KNIGHT, 52.dp, style = style)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = Type.Body.copy(color = if (selected) Pal.Gold else Pal.Egg), modifier = Modifier.weight(1f), maxLines = 1)
            if (onDelete != null) GlassIconButton(Icons.Rounded.Delete, "Xóa bộ quân này", onDelete, size = 32.dp, tint = Pal.EggDim)
            if (selected) CheckBadge(Modifier.padding(start = 6.dp))
        }
    }
}
