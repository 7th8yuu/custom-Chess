package com.customchess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.customchess.data.BOTS
import com.customchess.data.Bot
import com.customchess.vm.AppViewModel
import com.customchess.vm.TIME_PRESETS

private fun colorFor(i: Int): Boolean? = when (i) { 0 -> true; 1 -> false; else -> null }

@Composable
fun BotsScreen(vm: AppViewModel) {
    var picked by remember { mutableStateOf<Bot?>(null) }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Chơi với bot", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                "Mười hai bot, mỗi bot một tính cách. Bot yếu hay bỏ quân và đi bừa, bot mạnh tính xa hơn. " +
                    "Điểm bên cạnh chỉ là ước lượng tương đối giữa các bot trong ứng dụng.",
                style = Type.Small,
            )
            Spacer(Modifier.height(14.dp))
            for (b in BOTS) {
                val wins = vm.progress.botWins[b.id] ?: 0
                GlassBox(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), onClick = { picked = b }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(Pal.PrimaryContainer.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(b.emoji, style = Type.Title.copy(fontSize = 28.sp)) }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(b.name, style = Type.Heading, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                if (wins > 0) {
                                    Icon(Icons.Rounded.Check, "Đã thắng", tint = Pal.Jade, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                                }
                            }
                            Text(b.tagline, style = Type.Small, maxLines = 2)
                        }
                        Pill2("~${b.rating}")
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    picked?.let { b ->
        BotDialog(b, wins = vm.progress.botWins[b.id] ?: 0, onDismiss = { picked = null }) { color, time ->
            picked = null
            vm.startBotGame(b, colorFor(color), TIME_PRESETS.getOrNull(time)?.takeIf { !it.unlimited })
        }
    }
}

@Composable
private fun BotDialog(bot: Bot, wins: Int, onDismiss: () -> Unit, onStart: (Int, Int) -> Unit) {
    var color by remember { mutableIntStateOf(2) }
    var time by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Pal.PanelHi).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(bot.emoji, style = Type.Display.copy(fontSize = 54.sp, lineHeight = 60.sp))
            Text(bot.name, style = Type.Title)
            Text("~${bot.rating} · ${bot.tagline}", style = Type.Small, modifier = Modifier.padding(top = 2.dp))
            if (wins > 0) Text("Bạn đã thắng $wins ván", style = Type.Small.copy(color = Pal.Jade), modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            Text("Bạn cầm quân", style = Type.Small, modifier = Modifier.align(Alignment.Start))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) { ColorPills(color) { color = it } }
            Spacer(Modifier.height(12.dp))
            Text("Thời gian", style = Type.Small, modifier = Modifier.align(Alignment.Start))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TIME_PRESETS.take(4).forEachIndexed { i, p -> Pill(p.label, time == i, { time = i }) }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Bắt đầu", { onStart(color, time) })
            Spacer(Modifier.height(8.dp))
            GhostButton("Hủy", onDismiss, Modifier.fillMaxWidth())
        }
    }
}
