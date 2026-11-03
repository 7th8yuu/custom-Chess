package com.customchess.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.customchess.vm.AppViewModel

/** Mục Ngôn ngữ trong Cài đặt: chọn ngôn ngữ, nhập file ngôn ngữ tự dịch, xuất file mẫu để dịch. */
@Composable
fun LanguageGroup(vm: AppViewModel) {
    val ctx = LocalContext.current
    val s = vm.settings
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            msg = if (text.isNullOrBlank()) false to "Không đọc được file" else vm.importLanguage(text).let { it.ok to it.message }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(Lang.templateJson(ctx)) } != null
            }.getOrDefault(false)
            msg = if (ok) true to "Đã lưu file mẫu. Dịch phần giá trị, sửa _meta rồi nhập lại" else false to "Không lưu được file"
        }
    }

    SectionTitle("Ngôn ngữ")
    GlassCard(padding = 14.dp) {
        Text("Đổi xong có hiệu lực ngay. AI (phân tích, chơi với AI) cũng trả lời bằng ngôn ngữ này.", style = Type.Small)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill("Theo hệ thống", s.language == Lang.AUTO, { vm.setLanguage(Lang.AUTO) })
            for (l in Lang.infos) Pill(l.nativeName, s.language == l.code, { vm.setLanguage(l.code) })
        }
        Text(
            "Muốn thêm ngôn ngữ khác: xuất file mẫu, dịch phần giá trị (nhờ AI hoặc người quen), điền mục _meta rồi nhập lại.",
            style = Type.Small,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Nhập file ngôn ngữ", { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, Modifier.weight(1f))
            GhostButton("Xuất file mẫu", { exporter.launch("customchess-language.json") }, Modifier.weight(1f))
        }
        for (l in Lang.infos.filter { it.custom }) {
            Spacer(Modifier.height(6.dp))
            GhostButton("Xóa ngôn ngữ ${l.nativeName}", { vm.deleteLanguage(l.code) }, Modifier.fillMaxWidth(), danger = true)
        }
        msg?.let { (ok, text) ->
            Text(text, style = Type.Small.copy(color = if (ok) Pal.Jade else Pal.Alert), modifier = Modifier.padding(top = 8.dp))
        }
    }
}
