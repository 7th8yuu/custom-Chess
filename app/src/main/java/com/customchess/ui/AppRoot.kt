package com.customchess.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.customchess.color.PhotoSeeds
import com.customchess.vm.AppViewModel
import com.customchess.vm.Screen

@Composable
fun AppRoot(vm: AppViewModel) {
    val s = vm.settings
    val theme = BOARD_THEMES[s.boardTheme.coerceIn(0, BOARD_THEMES.lastIndex)]
    val system = isSystemInDarkTheme()
    val dark = when (s.themeMode) { 1 -> false; 2 -> system; else -> true }
    val pieceStyle = PIECE_STYLES[s.pieceSet.coerceIn(0, PIECE_STYLES.lastIndex)]

    // Thanh trạng thái và thanh điều hướng đổi màu biểu tượng theo chế độ sáng/tối.
    val activity = LocalContext.current.findActivity() as? ComponentActivity
    LaunchedEffect(dark) {
        val bars = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }

    CustomChessTheme(
        seed = if (s.adaptColors) theme.seed else PhotoSeeds.LACQUER,
        muted = s.adaptColors && theme.muted,
        dark = dark,
        pieceStyle = pieceStyle,
        showCoords = s.showCoords,
    ) {
        BackHandler(enabled = vm.stack.size > 1 && vm.screen != Screen.GAME) { vm.back() }

        Box(Modifier.fillMaxSize().background(Pal.Ink)) {
            // quầng sáng nhẹ ở đầu màn hình theo màu chủ đạo
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Pal.Gold.copy(alpha = 0.13f), Color.Transparent), startY = 0f, endY = 1100f),
                ),
            )
            Crossfade(targetState = vm.screen, animationSpec = tween(160), label = "screen") { screen ->
                when (screen) {
                    Screen.LOGIN -> LoginScreen(vm, theme)
                    Screen.HOME -> HomeScreen(vm, theme)
                    Screen.ENGINE_SETUP -> EngineSetupScreen(vm)
                    Screen.API_SETUP -> ApiSetupScreen(vm)
                    Screen.AI_SETTINGS -> AiSettingsScreen(vm)
                    Screen.FRIENDS -> FriendsScreen(vm)
                    Screen.LOCAL_SETUP -> LocalSetupScreen(vm)
                    Screen.WIFI_HOST -> WifiHostScreen(vm)
                    Screen.WIFI_JOIN -> WifiJoinScreen(vm)
                    Screen.BT_HOST -> BtHostScreen(vm)
                    Screen.BT_JOIN -> BtJoinScreen(vm)
                    Screen.ONLINE -> OnlineScreen(vm)
                    Screen.SETTINGS -> SettingsScreen(vm)
                    Screen.APPEARANCE -> AppearanceScreen(vm, theme)
                    Screen.GAME -> GameScreen(vm, theme)
                }
            }
        }
    }
}
