package com.customchess.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customchess.vm.Screen

/** Chỗ chừa dưới cùng của các màn hình gốc để nội dung không bị thanh điều hướng che. */
val NAV_SPACE = 108.dp

private class NavItem(val screen: Screen, val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem(Screen.HOME, "Trang chủ", Icons.Rounded.Home),
    NavItem(Screen.PLAY, "Chơi", Icons.Rounded.PlayArrow),
    NavItem(Screen.PUZZLES, "Câu đố", Icons.Rounded.Extension),
    NavItem(Screen.LEARN, "Học", Icons.Rounded.School),
    NavItem(Screen.PROFILE, "Tôi", Icons.Rounded.Person),
)

/** Thanh điều hướng nổi bằng kính ở đáy màn hình, mục đang chọn có viên thuốc sáng và biểu tượng nảy nhẹ. */
@Composable
fun GlassNavBar(current: Screen, onSelect: (Screen) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(30.dp)
    Box(modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        GlassBox(Modifier.fillMaxWidth().height(70.dp), shape, level = 1.25f) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                for (item in NAV_ITEMS) {
                    NavCell(item, item.screen == current, { onSelect(item.screen) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NavCell(item: NavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val color by animateColorAsState(
        if (selected) Pal.OnPrimaryContainer else Pal.EggDim,
        if (Fx.anim) spring(stiffness = Spring.StiffnessMedium) else snap(), label = "navc",
    )
    val pill by animateFloatAsState(if (selected) 1f else 0f, if (Fx.anim) spring(stiffness = Spring.StiffnessMediumLow) else snap(), label = "navp")
    val lift by animateFloatAsState(
        if (selected) 1f else 0f,
        if (Fx.anim) spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium) else snap(), label = "navl",
    )
    Box(
        modifier.height(58.dp).pressable(onClick, scale = 0.92f).clip(RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxWidth().height(58.dp)
                .graphicsLayer { alpha = pill; scaleX = 0.8f + 0.2f * pill; scaleY = 0.8f + 0.2f * pill }
                .clip(RoundedCornerShape(24.dp))
                .glass(RoundedCornerShape(24.dp), tint = Pal.PrimaryContainer, selected = false),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                item.icon, contentDescription = item.label, tint = color,
                modifier = Modifier.size(26.dp).graphicsLayer { translationY = -3.dp.toPx() * lift; scaleX = 1f + 0.12f * lift; scaleY = 1f + 0.12f * lift },
            )
            Text(item.label, style = Type.Small.copy(color = color, fontSize = 11.sp, lineHeight = 14.sp))
        }
    }
}
