package com.customchess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.customchess.ai.Provider
import com.customchess.vm.AppViewModel

@Composable
fun AiSettingsScreen(vm: AppViewModel) {
    val provider = vm.settings.provider
    val slot = vm.slot(provider)
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    var showKey by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(slot.baseUrl.isNotEmpty()) }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("API key và model", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in Provider.values()) Pill(p.label, p == provider, { vm.selectProvider(p) })
            }
            Spacer(Modifier.height(10.dp))
            Text2(provider.tagline, Type.Small)
            if (provider == Provider.CLAUDE) {
                Text2("Mỗi nước đi gọi model Sonnet 5.5 với effort ở mức thấp để trả lời nhanh và rẻ.", Type.Small)
            }

            SectionTitle("API key")
            OutlinedTextField(
                value = slot.key,
                onValueChange = { vm.updateSlot(provider, slot.copy(key = it.trim())) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text2("Dán key vào đây", Type.Small) },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = fieldColors(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Dán", {
                    clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { vm.updateSlot(provider, slot.copy(key = it)) }
                })
                GhostButton(if (showKey) "Ẩn" else "Hiện", { showKey = !showKey })
                GhostButton("Lấy key", { runCatching { uri.openUri(provider.keyUrl) } })
            }

            SectionTitle("Model")
            OutlinedTextField(
                value = slot.model,
                onValueChange = { vm.updateSlot(provider, slot.copy(model = it.trim())) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text2(provider.defaultModel, Type.Small) },
                colors = fieldColors(),
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(
                if (vm.aiBusy) "Đang tải..." else "Tải danh sách model từ tài khoản của bạn",
                { vm.loadModels(provider) },
                enabled = slot.key.isNotBlank() && !vm.aiBusy,
            )
            if (vm.models.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.models.take(60)) { m ->
                        Pill(m, m == slot.model, { vm.updateSlot(provider, slot.copy(model = m)) })
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            GhostButton(if (advanced) "Ẩn địa chỉ API tùy chỉnh" else "Địa chỉ API tùy chỉnh (nâng cao)", { advanced = !advanced })
            if (advanced) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = slot.baseUrl,
                    onValueChange = { vm.updateSlot(provider, slot.copy(baseUrl = it.trim())) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text2(provider.defaultBase, Type.Small) },
                    colors = fieldColors(),
                )
                Text2(
                    "Để trống để dùng địa chỉ chính thức. Với OpenAI có thể nhập địa chỉ của DeepSeek, Groq, OpenRouter hoặc máy chủ tự dựng (có đuôi /v1).",
                    Type.Small, Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                if (vm.aiBusy) "Đang kiểm tra..." else "Kiểm tra kết nối",
                { vm.testAi(provider) },
                enabled = slot.key.isNotBlank() && slot.model.isNotBlank() && !vm.aiBusy,
            )
            vm.aiNote?.let { note ->
                if (vm.aiNoteIsError) ErrorText(note)
                else Text2(note, Type.Small.copy(color = Pal.Jade), Modifier.padding(top = 8.dp))
            }

            SectionTitle("Khi AI gặp lỗi")
            SettingSwitch(
                "Để máy đi thay",
                "Nếu AI không trả lời được, engine nội bộ sẽ đi nước đó để ván không bị dừng",
                vm.settings.fallbackToEngine,
            ) { vm.updateSettings(vm.settings.copy(fallbackToEngine = it)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
