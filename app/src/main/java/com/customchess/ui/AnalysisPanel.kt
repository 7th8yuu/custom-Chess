package com.customchess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.customchess.core.Coach
import com.customchess.vm.AppViewModel

/**
 * Khung phân tích dưới bàn cờ: các nước tốt nhất của engine (offline) kèm điểm, và lời giải thích của AI khi người dùng bấm hỏi.
 * Mũi tên trên bàn cờ (do màn ván vẽ) chỉ nước đứng đầu danh sách.
 */
@Composable
fun AnalysisPanel(vm: AppViewModel, a: AppViewModel.AnalysisUi, modifier: Modifier = Modifier) {
    val s = vm.settings
    val whiteToMove = vm.ui.whiteToMove
    GlassCard(modifier.padding(top = 10.dp), padding = 14.dp) {
        Text2("Phân tích thế cờ", Type.Heading)
        Spacer(Modifier.height(6.dp))
        when {
            a.engineBusy -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Pal.Gold, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text2("Engine đang tính các nước tốt nhất...", Type.Small)
            }
            a.lines.isEmpty() -> Text2("Thế cờ này đã hết nước đi nên không có gì để phân tích.", Type.Small)
            else -> {
                val top = a.lines[0]
                val verdict = when {
                    top.mateIn != null && (top.mateIn > 0) == whiteToMove -> "Trắng có đường chiếu hết"
                    top.mateIn != null -> "Đen có đường chiếu hết"
                    else -> when (Coach.side(top.cpWhite)) {
                        1 -> "Trắng đang hơn"
                        -1 -> "Đen đang hơn"
                        else -> "Thế cờ cân bằng"
                    }
                }
                Text2(verdict, Type.Body.copy(color = Pal.Gold))
                Spacer(Modifier.height(8.dp))
                for ((i, l) in a.lines.withIndex()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text2("${i + 1}.", Type.Small)
                        Text2(l.san, Type.Heading, Modifier.weight(1f))
                        Pill2(Coach.label(l.cpWhite, l.mateIn, whiteToMove), if (i == 0) Pal.Gold else Pal.EggDim)
                    }
                }
                Text2("Điểm tính theo Trắng (số dương là Trắng hơn). Mũi tên trên bàn cờ chỉ nước số 1.", Type.Small, Modifier.padding(top = 6.dp))

                if (s.analysisAi) {
                    Spacer(Modifier.height(10.dp))
                    val text = a.aiText
                    when {
                        text != null -> Text2(text, Type.Body)
                        a.aiBusy -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Pal.Gold, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text2("${s.provider.label} đang viết lời giải thích...", Type.Small)
                        }
                        else -> GhostButton(
                            if (vm.hasKey(s.provider)) "AI giải thích bằng lời (${s.provider.label})" else "Thêm API key ${s.provider.label} để AI giải thích",
                            vm::askAiAnalysis, Modifier.fillMaxWidth(),
                        )
                    }
                    ErrorText(a.aiError)
                }
            }
        }
    }
}
