package com.customchess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.customchess.vm.AppViewModel
import com.customchess.vm.Screen

@Composable
fun AppRoot(vm: AppViewModel) {
    val theme = BOARD_THEMES[vm.settings.boardTheme.coerceIn(0, BOARD_THEMES.lastIndex)]

    BackHandler(enabled = vm.stack.size > 1 && vm.screen != Screen.GAME) { vm.back() }

    Box(Modifier.fillMaxSize().background(Pal.Ink)) {
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
                Screen.GAME -> GameScreen(vm, theme)
            }
        }
    }
}
