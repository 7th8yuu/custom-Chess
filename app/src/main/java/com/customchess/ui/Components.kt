package com.customchess.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(Pal.Gold)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.Button.copy(color = Pal.OnGold))
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    val tint = if (danger) Pal.Alert else Pal.Egg
    Box(
        modifier
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, if (danger) Pal.Alert.copy(alpha = 0.6f) else Pal.Hair), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.Button.copy(color = tint, fontSize = 15.sp))
    }
}

/** Lựa chọn dạng viên thuốc (đã chọn: nền vàng). */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Pal.Gold else Pal.Panel)
            .border(BorderStroke(1.dp, if (selected) Pal.Gold else Pal.Hair), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.Body.copy(color = if (selected) Pal.OnGold else Pal.Egg, fontSize = 14.sp))
    }
}

/** Đầu trang có nút quay lại. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", style = Type.Display.copy(fontSize = 34.sp, lineHeight = 36.sp))
        }
        Spacer(Modifier.width(6.dp))
        Text(title, style = Type.Title, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.Heading, modifier = modifier.padding(top = 22.dp, bottom = 10.dp))
}

/** Một hàng chọn trong danh sách: tiêu đề, mô tả, có viền vàng khi được chọn. */
@Composable
fun ChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Pal.PanelHi else Pal.Panel)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Pal.Gold else Pal.Hair), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Heading)
            if (subtitle.isNotEmpty()) Text(subtitle, style = Type.Small)
        }
        if (trailing != null) Text(trailing, style = Type.Small.copy(color = if (selected) Pal.Gold else Pal.EggDim))
    }
}

@Composable
fun InfoCard(text: String, modifier: Modifier = Modifier, tone: Color = Pal.EggDim) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Pal.Panel)
            .border(BorderStroke(1.dp, Pal.Hair), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(text, style = Type.Small.copy(color = tone, lineHeight = 20.sp))
    }
}

@Composable
fun ErrorText(text: String?, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    Text(text, style = Type.Small.copy(color = Pal.Alert), modifier = modifier.padding(top = 8.dp), textAlign = TextAlign.Start)
}

@Composable
fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Body)
            if (subtitle.isNotEmpty()) Text(subtitle, style = Type.Small)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Pal.OnGold, checkedTrackColor = Pal.Gold,
                uncheckedThumbColor = Pal.EggDim, uncheckedTrackColor = Pal.PanelHi, uncheckedBorderColor = Pal.Hair,
            ),
        )
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Pal.Egg, unfocusedTextColor = Pal.Egg,
    focusedBorderColor = Pal.Gold, unfocusedBorderColor = Pal.Hair,
    focusedLabelColor = Pal.Gold, unfocusedLabelColor = Pal.EggDim,
    cursorColor = Pal.Gold,
    focusedContainerColor = Pal.Panel, unfocusedContainerColor = Pal.Panel,
)

@Composable
fun Avatar(initial: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(Pal.PanelHi).border(BorderStroke(1.5.dp, Pal.Gold), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = Type.Heading.copy(color = Pal.Gold))
    }
}
