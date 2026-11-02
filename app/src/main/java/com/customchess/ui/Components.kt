package com.customchess.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Nút chính: viên thuốc đổ màu từ màu nhấn sang màu bổ trợ, có vệt sáng phía trên. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .pressable(onClick, enabled)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Pal.Gold, lerp(Pal.Gold, Pal.Tertiary, 0.5f))))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.Transparent), endY = 90f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.Button.copy(color = Pal.OnGold))
    }
}

/** Nút phụ bằng kính. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    val tint = if (danger) Pal.Alert else Pal.Egg
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .pressable(onClick, enabled)
            .glass(shape, tint = if (danger) Pal.Alert else Color.Unspecified)
            .clip(shape)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.Button.copy(color = tint, fontSize = 15.sp))
    }
}

/** Lựa chọn dạng viên thuốc: kính, khi chọn thì đổ đặc màu nhấn. */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .height(40.dp)
            .pressable(onClick)
            .glass(shape, selected = false, tint = if (selected) Pal.Gold else Color.Unspecified)
            .clip(shape)
            .background(if (selected) Pal.Gold else Color.Transparent)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.Body.copy(color = if (selected) Pal.OnGold else Pal.Egg, fontSize = 14.sp))
    }
}

/** Nút tròn bằng kính chứa một biểu tượng. */
@Composable
fun GlassIconButton(icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = Pal.Egg, enabled: Boolean = true) {
    Box(
        modifier.size(size).alpha(if (enabled) 1f else 0.35f).pressable(onClick, enabled).glass(CircleShape).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Ô biểu tượng tròn tô màu nhạt, dùng cho ô chức năng, danh sách. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = Pal.Gold, size: Dp = 44.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

/** Đầu trang có nút quay lại. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại", onBack)
        Spacer(Modifier.width(12.dp))
        Text(title, style = Type.Title, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.Heading.copy(color = Pal.EggDim, fontSize = 14.sp, letterSpacing = 0.6.sp), modifier = modifier.padding(top = 24.dp, bottom = 10.dp, start = 4.dp))
}

/** Thẻ kính chứa nội dung dạng cột. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    tint: Color = Color.Unspecified,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassBox(modifier.fillMaxWidth(), RoundedCornerShape(24.dp), tint = tint, selected = selected, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(padding), content = content)
    }
}

/** Một hàng chọn trong danh sách: tiêu đề, mô tả, viền nhấn khi được chọn. */
@Composable
fun ChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    GlassBox(modifier.fillMaxWidth(), RoundedCornerShape(20.dp), selected = selected, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.Heading)
                if (subtitle.isNotEmpty()) Text(subtitle, style = Type.Small)
            }
            if (trailing != null) Text(trailing, style = Type.Small.copy(color = if (selected) Pal.Gold else Pal.EggDim))
        }
    }
}

@Composable
fun InfoCard(text: String, modifier: Modifier = Modifier, tone: Color = Pal.EggDim) {
    GlassBox(modifier.fillMaxWidth(), RoundedCornerShape(20.dp)) {
        Text(text, style = Type.Small.copy(color = tone, lineHeight = 20.sp), modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun ErrorText(text: String?, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    Text(text, style = Type.Small.copy(color = Pal.Alert), modifier = modifier.padding(top = 8.dp), textAlign = TextAlign.Start)
}

@Composable
fun SettingSwitch(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Body)
            if (subtitle.isNotEmpty()) Text(subtitle, style = Type.Small)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Pal.OnGold, checkedTrackColor = Pal.Gold,
                uncheckedThumbColor = Pal.EggDim, uncheckedTrackColor = Pal.PanelHi, uncheckedBorderColor = Pal.Hair,
            ),
        )
    }
}

/** Thanh trượt có tiêu đề và giá trị. */
@Composable
fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Type.Body, modifier = Modifier.weight(1f))
            Text(valueText, style = Type.Small.copy(color = Pal.Gold))
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps, enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Pal.Gold, activeTrackColor = Pal.Gold, inactiveTrackColor = Pal.PanelHi,
                activeTickColor = Pal.OnGold, inactiveTickColor = Pal.Hair,
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
    focusedContainerColor = Color.White.copy(alpha = if (Skin.dark) 0.06f else 0.45f),
    unfocusedContainerColor = Color.White.copy(alpha = if (Skin.dark) 0.04f else 0.35f),
)

@Composable
fun Avatar(initial: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(Pal.Gold, Pal.Tertiary))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = Type.Heading.copy(color = Pal.OnGold, fontSize = (size.value * 0.42f).sp))
    }
}
