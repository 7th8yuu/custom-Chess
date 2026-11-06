package com.customchess.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.customchess.color.PhotoSeeds
import com.customchess.vm.AppViewModel
import com.customchess.vm.Screen
import com.customchess.vm.TAB_SCREENS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppRoot(vm: AppViewModel) {
    val s = vm.settings
    val ctx = LocalContext.current
    val sysAnim = remember { Fx.systemAnimationsOn(ctx) }
    // Gộp cài đặt, chế độ tiết kiệm pin và tùy chọn hệ thống thành trạng thái hiệu ứng dùng chung.
    remember(s, sysAnim) { Fx.apply(s, sysAnim); 0 }

    val theme = resolveBoard(s.boardId, vm.customBoards.toList(), vm.customs::boardPath).withVeil(s.boardVeil)
    val system = isSystemInDarkTheme()
    val dark = when (s.themeMode) { 1 -> false; 2 -> system; else -> true }

    // Bộ quân tự thêm được giải mã ngoài luồng giao diện rồi mới dùng.
    val pieceSets = vm.customPieces.toList()
    LaunchedEffect(pieceSets) {
        for (p in pieceSets) {
            if (PieceStyleCache.styles[p.id] == null) {
                val st = withContext(Dispatchers.IO) { loadPieceStyle(vm.customs.piecesDir(p.id)) }
                if (st != null) PieceStyleCache.styles[p.id] = st
            }
        }
    }
    val pieceStyle = resolvePieces(s.pieceId)

    // Thanh trạng thái và thanh điều hướng đổi màu biểu tượng theo chế độ sáng/tối.
    val activity = ctx.findActivity() as? ComponentActivity
    LaunchedEffect(dark) {
        val bars = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }

    // Gợi ý tốc độ khung hình cho hệ thống (0 = để hệ thống tự quyết).
    LaunchedEffect(Fx.fps) {
        activity?.window?.let { w ->
            val lp = w.attributes
            lp.preferredRefreshRate = Fx.fps.toFloat()
            w.attributes = lp
        }
    }

    // Giữ màn hình sáng khi đang chơi hoặc giải câu đố.
    val view = LocalView.current
    val keepAwake = s.keepScreenOn && (vm.screen == Screen.GAME || vm.screen == Screen.PUZZLE || vm.screen == Screen.REVIEW)
    SideEffect { view.keepScreenOn = keepAwake }

    CustomChessTheme(
        seed = if (s.adaptColors) theme.seed else PhotoSeeds.LACQUER,
        muted = s.adaptColors && theme.muted,
        dark = dark,
        pieceStyle = pieceStyle,
        showCoords = s.showCoords,
    ) {
        BackHandler(enabled = vm.stack.size > 1 && vm.screen != Screen.GAME) { vm.back() }
        BackHandler(enabled = vm.stack.size == 1 && vm.screen in TAB_SCREENS && vm.screen != Screen.HOME) { vm.selectTab(Screen.HOME) }

        Box(Modifier.fillMaxSize()) {
            AppBackdrop(theme)
            ScreenHost(vm, theme)
            val showNav = vm.stack.size == 1 && vm.screen in TAB_SCREENS
            AnimatedVisibility(
                visible = showNav,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = if (Fx.transitions) slideInVertically(tween(260)) { it } + fadeIn(tween(200)) else fadeIn(tween(0)),
                exit = if (Fx.transitions) slideOutVertically(tween(200)) { it } + fadeOut(tween(140)) else fadeOut(tween(0)),
            ) {
                GlassNavBar(current = vm.screen, onSelect = vm::selectTab)
            }
        }
    }
}

@Composable
private fun ScreenHost(vm: AppViewModel, theme: BoardTheme) {
    val screen = vm.screen
    val depth = vm.stack.size
    var lastDepth by remember { mutableIntStateOf(depth) }
    val forward = depth >= lastDepth
    SideEffect { lastDepth = depth }
    if (Fx.transitions) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val tabSwitch = initialState in TAB_SCREENS && targetState in TAB_SCREENS
                when {
                    tabSwitch -> (fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.97f)) togetherWith fadeOut(tween(140))
                    forward -> (slideInHorizontally(tween(280)) { it / 5 } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(280)) { -it / 8 } + fadeOut(tween(160)))
                    else -> (slideInHorizontally(tween(280)) { -it / 6 } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(280)) { it / 5 } + fadeOut(tween(160)))
                }
            },
            label = "screen",
        ) { sc -> ScreenFor(sc, vm, theme) }
    } else {
        ScreenFor(screen, vm, theme)
    }
}

@Composable
private fun ScreenFor(screen: Screen, vm: AppViewModel, theme: BoardTheme) {
    when (screen) {
        Screen.LOGIN -> LoginScreen(vm, theme)
        Screen.HOME -> HomeScreen(vm, theme)
        Screen.PLAY -> PlayScreen(vm)
        Screen.PUZZLES -> PuzzlesScreen(vm)
        Screen.LEARN -> LearnScreen(vm)
        Screen.PROFILE -> ProfileScreen(vm)
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
        Screen.BOTS -> BotsScreen(vm)
        Screen.HISTORY -> HistoryScreen(vm)
        Screen.REVIEW -> ReviewScreen(vm, theme)
        Screen.PUZZLE -> PuzzleScreen(vm, theme)
        Screen.LESSON -> LessonScreen(vm, theme)
        Screen.TOURNAMENTS -> TournamentsScreen(vm)
    }
}
