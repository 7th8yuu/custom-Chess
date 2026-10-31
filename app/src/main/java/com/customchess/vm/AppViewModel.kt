package com.customchess.vm

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.customchess.BuildConfig
import com.customchess.ai.AiClient
import com.customchess.ai.AiException
import com.customchess.ai.Provider
import com.customchess.core.Engine
import com.customchess.core.Game
import com.customchess.core.GameResult
import com.customchess.core.Move
import com.customchess.core.Pc
import com.customchess.core.Reason
import com.customchess.core.START_FEN
import com.customchess.core.rankOf
import com.customchess.data.ApiSlot
import com.customchess.data.AuthException
import com.customchess.data.GoogleAuth
import com.customchess.data.Settings
import com.customchess.data.Store
import com.customchess.data.User
import com.customchess.data.UserKind
import com.customchess.net.Bt
import com.customchess.net.BtDevice
import com.customchess.net.Cancelable
import com.customchess.net.Lan
import com.customchess.net.LanHost
import com.customchess.net.LichessClient
import com.customchess.net.LichessException
import com.customchess.net.Msg
import com.customchess.net.StreamLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

private val LOBBY_SCREENS = setOf(Screen.WIFI_HOST, Screen.WIFI_JOIN, Screen.BT_HOST, Screen.BT_JOIN)

class AppViewModel(private val app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    // ------------------------------------------------------------------ điều hướng & tài khoản
    val stack = mutableStateListOf<Screen>()
    val screen: Screen get() = stack.lastOrNull() ?: Screen.LOGIN

    var user by mutableStateOf(store.loadUser())
        private set
    var settings by mutableStateOf(store.loadSettings())
        private set
    var authBusy by mutableStateOf(false)
        private set
    var authError by mutableStateOf<String?>(null)
        private set

    /** Đấu online cần tài khoản thật (Lichess hoặc Google); khách thì không. */
    val canPlayOnline: Boolean get() = user?.isMember == true
    /** Lần đăng nhập Google gần nhất thất bại vì cấu hình: hiện nút hướng dẫn. */
    var googleSetupHint by mutableStateOf(false)
        private set
    val me: String get() = settings.playerName.ifBlank { user?.name ?: "Bạn" }

    init {
        stack.add(if (user == null) Screen.LOGIN else Screen.HOME)
    }

    fun go(s: Screen) {
        if (screen != s) stack.add(s)
    }

    fun back() {
        if (stack.size <= 1) return
        val popped = stack.removeAt(stack.size - 1)
        onLeave(popped)
    }

    fun goHome() {
        val old = stack.toList()
        stack.clear()
        stack.add(Screen.HOME)
        old.forEach { onLeave(it) }
    }

    private fun onLeave(s: Screen) {
        when (s) {
            in LOBBY_SCREENS -> if (!ui.active) stopLobbies()
            Screen.ONLINE -> if (!ui.active) stopLichess(keepLogin = true)
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ đăng nhập
    /**
     * Bắt đầu đăng nhập Google. Nếu bản build có Web client ID thì dùng Credential Manager,
     * không thì mở Google Sign-In cổ điển qua [launch] (chỉ cần OAuth client kiểu Android).
     */
    fun beginGoogle(activity: Activity?, launch: (Intent) -> Unit) {
        if (authBusy || activity == null) return
        authError = null
        googleSetupHint = false
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
            signInGoogle(activity)
            return
        }
        authBusy = true
        try {
            launch(GoogleAuth.legacyIntent(activity))
        } catch (e: Exception) {
            authBusy = false
            authError = e.message ?: "Không mở được đăng nhập Google"
            googleSetupHint = true
        }
    }

    /** Kết quả của Google Sign-In cổ điển (từ ActivityResult). */
    fun onGoogleLegacyResult(data: Intent?) {
        if (data == null) { authBusy = false; return } // đóng cửa sổ chọn tài khoản bằng nút Back
        try {
            applyUser(GoogleAuth.userFromLegacy(data))
        } catch (e: AuthException) {
            if (!e.cancelled) { authError = e.message; googleSetupHint = e.needsSetup }
        } catch (e: Exception) {
            authError = e.message ?: "Đăng nhập thất bại"
        } finally {
            authBusy = false
        }
    }

    private fun signInGoogle(activity: Activity) {
        authBusy = true
        viewModelScope.launch {
            try {
                applyUser(GoogleAuth.signIn(activity, BuildConfig.GOOGLE_WEB_CLIENT_ID))
            } catch (e: AuthException) {
                if (!e.cancelled) { authError = e.message; googleSetupHint = e.needsSetup }
            } catch (e: Exception) {
                authError = e.message ?: "Đăng nhập thất bại"
            } finally {
                authBusy = false
            }
        }
    }

    fun continueAsGuest() = applyUser(User(UserKind.GUEST, "Khách"))

    private fun applyUser(u: User) {
        store.saveUser(u)
        user = u
        if (screen == Screen.LOGIN) {
            stack.clear()
            stack.add(Screen.HOME)
        }
    }

    fun signOut() {
        viewModelScope.launch { GoogleAuth.signOut(app) }
        stopLichess(keepLogin = false)
        leaveGame(silent = true)
        store.saveUser(null)
        user = null
        stack.clear()
        stack.add(Screen.LOGIN)
    }

    fun updateSettings(s: Settings) {
        settings = s
        store.saveSettings(s)
    }

    // ------------------------------------------------------------------ thiết lập AI qua API
    val slots = mutableStateMapOf<Provider, ApiSlot>().also { m -> Provider.values().forEach { m[it] = store.loadSlot(it) } }
    var models by mutableStateOf<List<String>>(emptyList())
        private set
    var aiNote by mutableStateOf<String?>(null)
        private set
    var aiNoteIsError by mutableStateOf(false)
        private set
    var aiBusy by mutableStateOf(false)
        private set

    fun slot(p: Provider): ApiSlot = slots[p] ?: ApiSlot()

    fun updateSlot(p: Provider, s: ApiSlot) {
        slots[p] = s
        store.saveSlot(p, s)
    }

    fun selectProvider(p: Provider) {
        models = emptyList()
        aiNote = null
        updateSettings(settings.copy(provider = p))
    }

    fun hasKey(p: Provider) = slot(p).key.isNotBlank()

    fun loadModels(p: Provider) {
        if (aiBusy) return
        aiBusy = true
        aiNote = null
        viewModelScope.launch {
            try {
                models = AiClient.listModels(store.config(p))
                aiNoteIsError = false
                aiNote = if (models.isEmpty()) "Không có model nào" else "Tìm thấy ${models.size} model"
            } catch (e: AiException) {
                aiNoteIsError = true
                aiNote = e.message
            } finally {
                aiBusy = false
            }
        }
    }

    fun testAi(p: Provider) {
        if (aiBusy) return
        aiBusy = true
        aiNote = null
        viewModelScope.launch {
            try {
                val reply = AiClient.test(store.config(p))
                aiNoteIsError = false
                aiNote = "Kết nối tốt. AI trả lời: ${reply.take(40)}"
            } catch (e: AiException) {
                aiNoteIsError = true
                aiNote = e.message
            } finally {
                aiBusy = false
            }
        }
    }

    // ------------------------------------------------------------------ trạng thái ván cờ
    var ui by mutableStateOf(GameUi())
        private set
    var whiteMs by mutableLongStateOf(-1L)
        private set
    var blackMs by mutableLongStateOf(-1L)
        private set

    private var game = Game()
    private var mode = Mode.LOCAL
    private var myWhite: Boolean? = true
    private var clockInc = intArrayOf(0, 0)
    private var clockOn = false
    private val clockMs = longArrayOf(0, 0)
    private var lastTick = 0L
    private var aiJob: Job? = null
    private var clockJob: Job? = null
    private var forceEngineOnce = false

    private fun post(block: () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun refresh(extra: GameUi.() -> GameUi = { this }) {
        val p = game.position
        val res = game.result()
        val check = if (p.inCheck()) p.kingSquare(p.whiteToMove) else -1
        val editable = mode == Mode.ENGINE || mode == Mode.API || mode == Mode.LOCAL
        ui = ui.copy(
            board = p.board.copyOf(),
            whiteToMove = p.whiteToMove,
            lastMove = game.lastMove,
            checkSquare = check,
            sans = game.sans.toList(),
            plyCount = game.moves.size,
            result = res,
            selected = -1,
            targets = emptySet(),
            promotion = null,
            canUndo = editable && game.moves.isNotEmpty() && res == null,
        ).extra()
        if (res != null) {
            aiJob?.cancel()
            ui = ui.copy(thinking = false, drawOffered = false)
        }
    }

    private fun beginGame(
        newMode: Mode,
        white: Boolean?,
        whiteName: String,
        blackName: String,
        whiteSub: String = "",
        blackSub: String = "",
        clock: TimePreset? = null,
        startFen: String = START_FEN,
    ) {
        aiJob?.cancel()
        clockJob?.cancel()
        game = Game(startFen)
        mode = newMode
        myWhite = white
        val timed = clock != null && !clock.unlimited
        clockOn = timed
        clockInc = intArrayOf(clock?.incSec ?: 0, clock?.incSec ?: 0)
        clockMs[0] = (clock?.baseSec ?: 0) * 1000L
        clockMs[1] = clockMs[0]
        whiteMs = if (timed) clockMs[0] else -1L
        blackMs = if (timed) clockMs[1] else -1L
        forceEngineOnce = false
        ui = GameUi(
            active = true,
            mode = newMode,
            flipped = white == false,
            myWhite = white,
            whiteName = whiteName,
            blackName = blackName,
            whiteSub = whiteSub,
            blackSub = blackSub,
            timed = timed || newMode == Mode.LICHESS,
        )
        refresh()
        if (screen != Screen.GAME) stack.add(Screen.GAME)
        if (timed || newMode == Mode.LICHESS) startClockTicker()
        maybeAiMove()
    }

    /** Thoát ván: dọn mọi thứ đang chạy và quay về màn hình trước (hoặc trang chủ). */
    fun leaveGame(silent: Boolean = false) {
        val wasMode = mode
        val wasActive = ui.active
        aiJob?.cancel()
        clockJob?.cancel()
        if (wasMode == Mode.LINK) closeLink()
        if (wasMode == Mode.LICHESS) {
            lichessGameId?.let { id -> if (ui.result == null) io { runCatching { lichessClient?.let { c -> if (game.moves.size < 2) c.abort(id) else c.resign(id) } } } }
            gameStream?.cancel()
            gameStream = null
            lichessGameId = null
        }
        ui = GameUi()
        whiteMs = -1L
        blackMs = -1L
        if (silent || !wasActive) return
        if (screen == Screen.GAME) {
            if (wasMode == Mode.ENGINE || wasMode == Mode.API || wasMode == Mode.LOCAL) back() else goHome()
        }
    }

    // ------------------------------------------------------------------ bắt đầu các loại ván
    fun startEngineGame(level: Int, white: Boolean?) {
        val w = white ?: Random.nextBoolean()
        updateSettings(settings.copy(engineLevel = level))
        val engineName = "Máy · ${levelName(level)}"
        beginGame(
            Mode.ENGINE, w,
            whiteName = if (w) me else engineName, blackName = if (w) engineName else me,
            whiteSub = if (w) "" else "Engine offline", blackSub = if (w) "Engine offline" else "",
        )
    }

    /** Trả về false nếu chưa có API key (giao diện sẽ chuyển sang màn hình thiết lập). */
    fun startApiGame(white: Boolean?): Boolean {
        val cfg = store.config(settings.provider)
        if (!cfg.ready) return false
        val w = white ?: Random.nextBoolean()
        val aiName = settings.provider.label
        val sub = cfg.model
        beginGame(
            Mode.API, w,
            whiteName = if (w) me else aiName, blackName = if (w) aiName else me,
            whiteSub = if (w) "" else sub, blackSub = if (w) sub else "",
        )
        return true
    }

    private var lastLocalClock = TIME_PRESETS[0]

    fun startLocalGame(clock: TimePreset) {
        lastLocalClock = clock
        beginGame(Mode.LOCAL, null, "Trắng", "Đen", clock = clock)
    }

    /** Ván mới với cùng thiết lập (đổi màu quân khi chơi với máy/AI). */
    fun rematch() {
        when (mode) {
            Mode.ENGINE -> startEngineGame(settings.engineLevel, !(myWhite ?: true))
            Mode.API -> startApiGame(!(myWhite ?: true))
            Mode.LOCAL -> startLocalGame(lastLocalClock)
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ thao tác trên bàn cờ
    private fun isMyTurn(): Boolean = myWhite == null || myWhite == game.position.whiteToMove

    fun onSquareTap(sq: Int) {
        val s = ui
        if (!s.active || s.result != null || s.disconnected || s.promotion != null) return
        if (s.thinking || !isMyTurn()) return
        val p = game.position
        val piece = p.board[sq]
        val mine = piece != 0 && (piece > 0) == p.whiteToMove
        if (s.selected >= 0) {
            if (sq in s.targets) { tryMove(s.selected, sq); return }
            if (mine && sq != s.selected) { select(sq); return }
            ui = ui.copy(selected = -1, targets = emptySet())
            return
        }
        if (mine) select(sq)
    }

    private fun select(sq: Int) {
        val targets = game.position.legalMoves().filter { it.from == sq }.map { it.to }.toSet()
        ui = ui.copy(selected = sq, targets = targets)
    }

    private fun tryMove(from: Int, to: Int) {
        val p = game.position
        val piece = p.board[from]
        val lastRank = if (p.whiteToMove) 7 else 0
        if (abs(piece) == Pc.PAWN && rankOf(to) == lastRank) {
            ui = ui.copy(promotion = from to to, targets = emptySet())
            return
        }
        commitMove(Move(from, to), byMe = true)
    }

    fun choosePromotion(type: Int) {
        val pr = ui.promotion ?: return
        ui = ui.copy(promotion = null)
        commitMove(Move(pr.first, pr.second, type), byMe = true)
    }

    fun cancelPromotion() {
        ui = ui.copy(promotion = null, selected = -1, targets = emptySet())
    }

    private fun commitMove(m: Move, byMe: Boolean) {
        val mover = game.position.whiteToMove
        if (!game.play(m)) {
            refresh()
            return
        }
        if (clockOn && (mode == Mode.LOCAL || mode == Mode.LINK)) {
            val i = if (mover) 0 else 1
            clockMs[i] += clockInc[i] * 1000L
            publishClocks()
        }
        refresh { copy(moveTick = moveTick + 1, notice = null, drawOffered = false) }
        if (byMe) {
            when (mode) {
                Mode.LINK -> link?.send(Msg.Move(m.uci()))
                Mode.LICHESS -> sendLichessMove(m)
                else -> Unit
            }
        }
        if (ui.result == null) maybeAiMove()
    }

    fun flipBoard() {
        ui = ui.copy(flipped = !ui.flipped)
    }

    fun clearNotice() {
        if (ui.notice != null) ui = ui.copy(notice = null)
    }

    fun undo() {
        if (!ui.canUndo) return
        aiJob?.cancel()
        val n = when {
            mode == Mode.LOCAL -> 1
            game.position.whiteToMove == myWhite -> 2
            else -> 1
        }
        repeat(minOf(n, game.moves.size)) { game.undo() }
        ui = ui.copy(thinking = false, aiError = null)
        refresh()
        maybeAiMove()
    }

    fun resign() {
        if (ui.result != null) return
        when (mode) {
            Mode.ENGINE, Mode.API -> finish(GameResult(!(myWhite ?: true), Reason.RESIGNATION))
            Mode.LOCAL -> finish(GameResult(!game.position.whiteToMove, Reason.RESIGNATION))
            Mode.LINK -> {
                link?.send(Msg.Resign)
                finish(GameResult(!(myWhite ?: true), Reason.RESIGNATION))
            }
            Mode.LICHESS -> lichessGameId?.let { id ->
                val few = game.moves.size < 2
                io {
                    try { if (few) lichessClient?.abort(id) else lichessClient?.resign(id) }
                    catch (e: Exception) { post { ui = ui.copy(notice = e.message) } }
                }
            }
        }
    }

    fun offerDraw() {
        if (ui.result != null || ui.drawSent) return
        when (mode) {
            Mode.ENGINE, Mode.API -> {
                val aiWhite = !(myWhite ?: true)
                val eval = Engine.evaluate(game.position).let { if (aiWhite) it else -it }
                if (eval <= 60) finish(GameResult(null, Reason.AGREEMENT))
                else ui = ui.copy(notice = "Đối thủ từ chối hòa vì đang thấy mình hơn")
            }
            Mode.LOCAL -> ui = ui.copy(drawOffered = true)
            Mode.LINK -> {
                link?.send(Msg.DrawOffer)
                ui = ui.copy(drawSent = true, notice = "Đã gửi lời xin hòa")
            }
            Mode.LICHESS -> lichessGameId?.let { id ->
                io { runCatching { lichessClient?.draw(id, true) } }
                ui = ui.copy(drawSent = true, notice = "Đã gửi lời xin hòa")
            }
        }
    }

    fun answerDraw(accept: Boolean) {
        when (mode) {
            Mode.LINK -> link?.send(if (accept) Msg.DrawAccept else Msg.DrawDecline)
            Mode.LICHESS -> lichessGameId?.let { id -> io { runCatching { lichessClient?.draw(id, accept) } } }
            else -> Unit
        }
        if (accept && mode != Mode.LICHESS) finish(GameResult(null, Reason.AGREEMENT))
        else ui = ui.copy(drawOffered = false)
    }

    private fun finish(r: GameResult) {
        if (ui.result != null) return
        game.forcedResult = r
        aiJob?.cancel()
        ui = ui.copy(result = r, thinking = false, selected = -1, targets = emptySet(), drawOffered = false, promotion = null, canUndo = false)
    }

    // ------------------------------------------------------------------ đồng hồ
    private fun publishClocks() {
        whiteMs = clockMs[0]
        blackMs = clockMs[1]
    }

    private fun clockRunning(): Boolean = when (mode) {
        Mode.LICHESS -> game.moves.size >= 2
        else -> game.moves.size >= 1
    }

    private fun startClockTicker() {
        clockJob?.cancel()
        lastTick = SystemClock.elapsedRealtime()
        clockJob = viewModelScope.launch {
            while (isActive) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                val dt = now - lastTick
                lastTick = now
                if (ui.result != null || ui.disconnected || !clockRunning() || !ui.active) continue
                if (!clockOn && mode != Mode.LICHESS) continue
                val whiteTurn = game.position.whiteToMove
                val i = if (whiteTurn) 0 else 1
                clockMs[i] = max(0L, clockMs[i] - dt)
                publishClocks()
                if (clockMs[i] == 0L && mode != Mode.LICHESS) onFlag(whiteTurn)
            }
        }
    }

    private fun onFlag(whiteFlagged: Boolean) {
        when (mode) {
            Mode.LOCAL -> finish(GameResult(!whiteFlagged, Reason.TIMEOUT))
            Mode.LINK -> if (myWhite == whiteFlagged) {
                link?.send(Msg.Timeout)
                finish(GameResult(!whiteFlagged, Reason.TIMEOUT))
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ AI (engine nội bộ hoặc API)
    private fun maybeAiMove() {
        if (mode != Mode.ENGINE && mode != Mode.API) return
        if (ui.result != null) return
        val pos = game.position
        if (pos.whiteToMove == myWhite) return
        aiJob?.cancel()
        val movetext = game.movetext()
        val history = game.repetitionMap()
        val ply = game.moves.size
        val useApi = mode == Mode.API && !forceEngineOnce
        forceEngineOnce = false
        val level = if (mode == Mode.ENGINE) settings.engineLevel else 3
        val fallbackAllowed = mode == Mode.ENGINE || settings.fallbackToEngine || !useApi
        aiJob = viewModelScope.launch {
            ui = ui.copy(thinking = true, aiError = null)
            val t0 = SystemClock.elapsedRealtime()
            var move: Move? = null
            var error: String? = null
            if (useApi) {
                try {
                    move = AiClient.chooseMove(store.config(settings.provider), pos, movetext).move
                } catch (e: AiException) {
                    error = e.message ?: "Lỗi không rõ"
                }
            }
            if (move == null) {
                if (error != null && !fallbackAllowed) {
                    ui = ui.copy(thinking = false, aiError = error)
                    return@launch
                }
                move = withContext(Dispatchers.Default) {
                    Engine(level).bestMove(pos, history) { !isActive }
                }
                if (error != null) ui = ui.copy(notice = "AI lỗi: $error. Máy tự đi thay.")
            }
            val wait = 350 - (SystemClock.elapsedRealtime() - t0)
            if (wait > 0) delay(wait)
            if (game.moves.size != ply || ui.result != null) return@launch
            ui = ui.copy(thinking = false)
            if (move != null) commitMove(move, byMe = false)
        }
    }

    fun retryAi() {
        ui = ui.copy(aiError = null)
        maybeAiMove()
    }

    fun useEngineForThisMove() {
        forceEngineOnce = true
        retryAi()
    }

    // ------------------------------------------------------------------ chơi với bạn bè (Wi-Fi / Bluetooth)
    var lobbyColor by mutableStateOf(2) // 0 trắng, 1 đen, 2 ngẫu nhiên
    var lobbyTime by mutableStateOf(0)
    var lobbyError by mutableStateOf<String?>(null)
        private set
    var lobbyBusy by mutableStateOf(false)
        private set
    var hostAddresses by mutableStateOf<List<String>>(emptyList())
        private set
    var hostWaiting by mutableStateOf(false)
        private set
    val lanHosts = mutableStateListOf<LanHost>()
    val btDevices = mutableStateListOf<BtDevice>()

    private var link: StreamLink? = null
    private var linkIsHost = false
    private var peerName = "Bạn bè"
    private var wifiHost: Lan.Host? = null
    private var lanScanner: Lan.Scanner? = null
    private var btHost: Bt.Host? = null

    fun startWifiHost() {
        stopLobbies()
        lobbyError = null
        hostAddresses = Lan.localAddresses()
        hostWaiting = true
        val h = Lan.Host(
            name = me,
            onConnected = { l, _ -> post { onLinkConnected(l, host = true) } },
            onError = { msg -> post { lobbyError = "Không mở được cổng chờ: $msg"; hostWaiting = false } },
        )
        wifiHost = h
        h.start()
    }

    fun startWifiScan() {
        stopLobbies()
        lobbyError = null
        lanHosts.clear()
        val s = Lan.Scanner(app) { h ->
            post { if (lanHosts.none { it.address == h.address && it.port == h.port }) lanHosts.add(h) }
        }
        lanScanner = s
        s.start()
    }

    fun connectWifi(address: String, port: Int = Lan.PORT) {
        if (lobbyBusy) return
        val addr = address.trim()
        if (addr.isEmpty()) { lobbyError = "Hãy nhập địa chỉ IP của bạn bè"; return }
        lobbyBusy = true
        lobbyError = null
        viewModelScope.launch {
            try {
                val l = withContext(Dispatchers.IO) { Lan.connect(addr, port) }
                onLinkConnected(l, host = false)
            } catch (e: Exception) {
                lobbyError = "Không kết nối được tới $addr: ${e.message ?: "lỗi mạng"}"
            } finally {
                lobbyBusy = false
            }
        }
    }

    fun startBtHost() {
        stopLobbies()
        lobbyError = null
        val adapter = Bt.adapter(app)
        if (adapter == null || !adapter.isEnabled) { lobbyError = "Hãy bật Bluetooth trước"; return }
        hostWaiting = true
        val h = Bt.Host(
            adapter,
            onConnected = { l, _ -> post { onLinkConnected(l, host = true) } },
            onError = { msg -> post { lobbyError = "Bluetooth: $msg"; hostWaiting = false } },
        )
        btHost = h
        try { h.start() } catch (e: SecurityException) { lobbyError = "Thiếu quyền Bluetooth" }
    }

    fun refreshPairedDevices() {
        val adapter = Bt.adapter(app)
        if (adapter == null || !adapter.isEnabled) { lobbyError = "Hãy bật Bluetooth trước"; return }
        lobbyError = null
        val paired = Bt.pairedDevices(app)
        btDevices.removeAll { it.paired }
        paired.forEach { d -> if (btDevices.none { it.address == d.address }) btDevices.add(d) }
    }

    fun addBtDevice(d: BtDevice) {
        if (btDevices.none { it.address == d.address }) btDevices.add(d)
    }

    fun connectBt(address: String) {
        if (lobbyBusy) return
        val adapter = Bt.adapter(app)
        if (adapter == null || !adapter.isEnabled) { lobbyError = "Hãy bật Bluetooth trước"; return }
        lobbyBusy = true
        lobbyError = null
        viewModelScope.launch {
            try {
                val l = withContext(Dispatchers.IO) { Bt.connect(adapter, address) }
                onLinkConnected(l, host = false)
            } catch (e: SecurityException) {
                lobbyError = "Thiếu quyền Bluetooth"
            } catch (e: Exception) {
                lobbyError = "Không kết nối được. Hãy chắc rằng bạn bè đang ở màn hình \"Mở phòng\". (${e.message ?: "lỗi"})"
            } finally {
                lobbyBusy = false
            }
        }
    }

    fun showLobbyError(msg: String?) {
        lobbyError = msg
    }

    private fun stopLobbies() {
        wifiHost?.stop(); wifiHost = null
        lanScanner?.stop(); lanScanner = null
        btHost?.stop(); btHost = null
        hostWaiting = false
        lobbyBusy = false
    }

    private fun closeLink() {
        val l = link
        link = null
        l?.onClosed = {}
        l?.onLine = {}
        l?.close()
    }

    private fun onLinkConnected(l: StreamLink, host: Boolean) {
        stopLobbies()
        closeLink()
        link = l
        linkIsHost = host
        peerName = "Bạn bè"
        l.onLine = { line -> post { handleMsg(com.customchess.net.Protocol.parse(line)) } }
        l.onClosed = {
            post {
                if (link === l) {
                    if (ui.active && mode == Mode.LINK) {
                        if (ui.result == null) ui = ui.copy(disconnected = true, notice = "Mất kết nối với bạn bè")
                    } else {
                        lobbyError = "Kết nối đã bị ngắt"
                    }
                }
            }
        }
        l.start()
        l.send(Msg.Hello(me))
        lobbyBusy = true // chờ bắt tay xong
    }

    private fun handleMsg(m: Msg) {
        when (m) {
            is Msg.Hello -> {
                peerName = m.name
                if (linkIsHost && !(ui.active && mode == Mode.LINK)) {
                    val hostWhite = when (lobbyColor) { 0 -> true; 1 -> false; else -> Random.nextBoolean() }
                    val tp = TIME_PRESETS.getOrElse(lobbyTime) { TIME_PRESETS[0] }
                    link?.send(Msg.Start(guestIsWhite = !hostWhite, baseSec = tp.baseSec, incSec = tp.incSec))
                    startLinkGame(hostWhite, tp)
                }
            }
            is Msg.Start -> if (!linkIsHost) startLinkGame(!m.guestIsWhite, TimePreset("", m.baseSec, m.incSec))
            is Msg.Move -> {
                if (mode != Mode.LINK || !ui.active || ui.result != null) return
                val mv = game.position.parseMove(m.uci)
                if (mv != null && game.position.whiteToMove != myWhite) commitMove(mv, byMe = false)
                else ui = ui.copy(notice = "Nhận được nước đi không khớp. Hãy bắt đầu lại ván.")
            }
            Msg.Resign -> if (ui.active && mode == Mode.LINK) finish(GameResult(myWhite, Reason.RESIGNATION))
            Msg.DrawOffer -> if (ui.active && mode == Mode.LINK && ui.result == null) ui = ui.copy(drawOffered = true)
            Msg.DrawAccept -> if (ui.active && mode == Mode.LINK) finish(GameResult(null, Reason.AGREEMENT))
            Msg.DrawDecline -> if (ui.active && mode == Mode.LINK) ui = ui.copy(drawSent = false, notice = "Bạn bè từ chối hòa")
            Msg.Timeout -> if (ui.active && mode == Mode.LINK) finish(GameResult(myWhite, Reason.TIMEOUT))
            is Msg.Chat, is Msg.Unknown -> Unit
        }
    }

    private fun startLinkGame(iAmWhite: Boolean, tp: TimePreset) {
        lobbyBusy = false
        beginGame(
            Mode.LINK, iAmWhite,
            whiteName = if (iAmWhite) me else peerName, blackName = if (iAmWhite) peerName else me,
            whiteSub = if (iAmWhite) "" else "Bạn bè", blackSub = if (iAmWhite) "Bạn bè" else "",
            clock = tp,
        )
    }

    // ------------------------------------------------------------------ đấu online (Lichess)
    var lichessUi by mutableStateOf(
        LichessUi(connected = store.lichessToken.isNotBlank(), name = store.lichessName, ratings = store.lichessRatings),
    )
        private set
    private var lichessClient: LichessClient? = null
    private var eventStream: Cancelable? = null
    private var seekCall: Cancelable? = null
    private var gameStream: Cancelable? = null
    private var lichessGameId: String? = null

    private fun io(block: () -> Unit) {
        Thread { block() }.apply { isDaemon = true }.start()
    }

    init {
        // Kiểm tra lại token Lichess đã lưu (còn hiệu lực không) và cập nhật điểm xếp hạng.
        refreshLichess()
    }

    private fun refreshLichess() {
        val t = store.lichessToken
        if (t.isBlank()) return
        viewModelScope.launch {
            try {
                val p = withContext(Dispatchers.IO) { LichessClient(t).profile() }
                store.lichessName = p.name
                store.lichessRatings = p.ratings
                lichessUi = lichessUi.copy(connected = true, name = p.name, ratings = p.ratings)
            } catch (e: LichessException) {
                if (e.code == 401) lichessSessionExpired()
            } catch (_: Exception) {
                // không có mạng: giữ nguyên thông tin đã lưu
            }
        }
    }

    private fun lichessSessionExpired() {
        val wasLogin = user?.kind == UserKind.LICHESS
        stopLichess(keepLogin = false)
        if (wasLogin) {
            signOut()
            authError = "Phiên Lichess đã hết hạn, hãy đăng nhập lại."
        } else {
            lichessUi = lichessUi.copy(error = "Phiên Lichess đã hết hạn, hãy kết nối lại.")
        }
    }

    /** Mở luồng sự kiện khi vào màn hình Đấu online (nếu đã có token). */
    fun enterOnline() {
        if (!canPlayOnline) return
        if (store.lichessToken.isNotBlank() && eventStream == null) openEventStream(LichessClient(store.lichessToken))
    }

    /**
     * Đăng nhập Lichess bằng OAuth (PKCE): mở trang cấp quyền của Lichess trong tab trình duyệt ngay trong ứng dụng,
     * xong Lichess chuyển hướng về com.customchess.app://oauth và [handleOAuthRedirect] nhận token. Dùng được cả để đăng nhập
     * (màn hình đầu) lẫn để kết nối tài khoản Lichess cho người đang dùng Google.
     */
    fun startLichessLogin(activity: Activity?) {
        if (activity == null) return
        val verifier = LichessClient.randomString()
        val state = LichessClient.randomString(16)
        store.pkceVerifier = verifier
        store.pkceState = state
        try {
            val tabs = CustomTabsIntent.Builder().setShowTitle(true).setShareState(CustomTabsIntent.SHARE_STATE_OFF).build()
            tabs.launchUrl(activity, Uri.parse(LichessClient.authorizeUrl(state, verifier)))
            lichessUi = lichessUi.copy(error = null)
        } catch (e: ActivityNotFoundException) {
            lichessUi = lichessUi.copy(error = "Không mở được trình duyệt")
        }
    }

    /** Gọi từ MainActivity khi trình duyệt chuyển hướng về com.customchess.app://oauth?... */
    fun handleOAuthRedirect(uri: Uri) {
        if (uri.scheme != LichessClient.REDIRECT_SCHEME) return
        val err = uri.getQueryParameter("error")
        if (err != null) {
            lichessUi = lichessUi.copy(busy = false, error = "Lichess từ chối: ${uri.getQueryParameter("error_description") ?: err}")
            return
        }
        val code = uri.getQueryParameter("code") ?: return
        if (uri.getQueryParameter("state") != store.pkceState || store.pkceVerifier.isBlank()) {
            lichessUi = lichessUi.copy(error = "Phiên đăng nhập không khớp, hãy thử lại")
            return
        }
        val verifier = store.pkceVerifier
        store.pkceVerifier = ""
        store.pkceState = ""
        lichessUi = lichessUi.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) { LichessClient.exchangeCode(code, verifier) }
                connectLichess(token)
            } catch (e: Exception) {
                lichessUi = lichessUi.copy(busy = false, error = e.message ?: "Đăng nhập Lichess thất bại")
            }
        }
    }

    /**
     * Kết nối bằng một token (từ OAuth hoặc token cá nhân dán vào, quyền board:play). Nếu đang là khách hoặc chưa
     * đăng nhập thì tài khoản Lichess trở thành tài khoản của ứng dụng (mở khóa đấu online ngay).
     */
    fun connectLichess(token: String) {
        val t = token.trim()
        if (t.isEmpty()) return
        lichessUi = lichessUi.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val client = LichessClient(t)
                val p = withContext(Dispatchers.IO) { client.profile() }
                store.lichessToken = t
                store.lichessName = p.name
                store.lichessRatings = p.ratings
                lichessUi = LichessUi(connected = true, name = p.name, ratings = p.ratings)
                val cur = user
                if (cur == null || cur.isGuest) applyUser(User(UserKind.LICHESS, p.name, "lichess.org/@/${p.name}"))
                lichessClient = client
                // Luồng sự kiện chỉ mở khi đang ở màn Đấu online, kẻo ván bắt đầu trên trang web lại tự bật lên trong ứng dụng.
                if (screen == Screen.ONLINE) openEventStream(client)
            } catch (e: Exception) {
                lichessUi = lichessUi.copy(busy = false, error = e.message ?: "Không kết nối được Lichess")
            }
        }
    }

    /** Ngắt Lichess. Nếu đang dùng chính tài khoản Lichess để đăng nhập thì cũng đăng xuất. */
    fun lichessLogout() {
        if (user?.kind == UserKind.LICHESS) {
            signOut()
        } else {
            stopLichess(keepLogin = false)
        }
    }

    private fun stopLichess(keepLogin: Boolean) {
        seekCall?.cancel(); seekCall = null
        eventStream?.cancel(); eventStream = null
        if (!keepLogin) {
            gameStream?.cancel(); gameStream = null
            lichessGameId = null
            lichessClient = null
            store.lichessToken = ""
            store.lichessName = ""
            store.lichessRatings = listOf(0, 0, 0)
            lichessUi = LichessUi()
        } else {
            lichessUi = lichessUi.copy(searching = false)
        }
    }

    private fun openEventStream(client: LichessClient) {
        lichessClient = client
        eventStream?.cancel()
        eventStream = client.streamEvents(
            onEvent = { e -> post { onLichessEvent(e) } },
            onEnd = { err ->
                post {
                    eventStream = null
                    if (err != null && !ui.active) lichessUi = lichessUi.copy(searching = false, error = err.message ?: "Mất kết nối Lichess")
                }
            },
        )
    }

    fun seekLichess(preset: TimePreset, rated: Boolean) {
        val client = lichessClient ?: return
        if (lichessUi.searching) return
        if (eventStream == null) openEventStream(client)
        lichessUi = lichessUi.copy(searching = true, error = null)
        seekCall = client.seek(preset.baseSec / 60, preset.incSec, rated) { err ->
            post {
                if (err != null) lichessUi = lichessUi.copy(searching = false, error = err.message ?: "Không tìm được đối thủ")
                else if (lichessGameId == null) lichessUi = lichessUi.copy(searching = false)
            }
        }
    }

    fun cancelSeek() {
        seekCall?.cancel()
        seekCall = null
        lichessUi = lichessUi.copy(searching = false)
    }

    private fun onLichessEvent(e: JSONObject) {
        if (e.optString("type") != "gameStart") return
        val g = e.optJSONObject("game") ?: return
        val id = g.optString("gameId").ifEmpty { g.optString("id") }
        if (id.isEmpty() || id == lichessGameId) return
        val white = when (g.optString("color")) { "white" -> true; "black" -> false; else -> null }
        lichessGameId = id
        seekCall?.cancel(); seekCall = null
        lichessUi = lichessUi.copy(searching = false)
        val client = lichessClient ?: return
        gameStream?.cancel()
        gameStream = client.streamGame(
            id,
            onEvent = { ev -> post { onLichessGameEvent(id, ev, white) } },
            onEnd = { err ->
                post {
                    if (lichessGameId == id && ui.active && ui.result == null) {
                        ui = ui.copy(disconnected = true, notice = err?.message ?: "Mất kết nối với Lichess")
                    }
                }
            },
        )
    }

    private fun onLichessGameEvent(id: String, e: JSONObject, colorHint: Boolean?) {
        if (id != lichessGameId) return
        when (e.optString("type")) {
            "gameFull" -> {
                val w = e.optJSONObject("white")
                val b = e.optJSONObject("black")
                fun label(o: JSONObject?): String =
                    o?.optString("name")?.ifBlank { o.optString("id") }?.ifBlank { null }
                        ?: if (o?.has("aiLevel") == true) "Stockfish ${o.optInt("aiLevel")}" else "Ẩn danh"
                fun rating(o: JSONObject?): String = if (o != null && o.has("rating")) o.optInt("rating").toString() else ""
                val meLower = lichessUi.name.lowercase()
                val iAmWhite = colorHint
                    ?: (w?.optString("id")?.lowercase() == meLower || w?.optString("name")?.lowercase() == meLower)
                val fen = e.optString("initialFen").let { if (it.isBlank() || it == "startpos") START_FEN else it }
                beginGame(
                    Mode.LICHESS, iAmWhite,
                    whiteName = label(w), blackName = label(b), whiteSub = rating(w), blackSub = rating(b),
                    startFen = fen,
                )
                e.optJSONObject("state")?.let { applyLichessState(it) }
            }
            "gameState" -> applyLichessState(e)
        }
    }

    private fun applyLichessState(s: JSONObject) {
        if (mode != Mode.LICHESS || !ui.active) return
        val moves = s.optString("moves").trim()
        val list = if (moves.isEmpty()) emptyList() else moves.split(' ')
        var changed = false
        for (i in game.moves.size until list.size) {
            val mv = game.position.parseMove(list[i]) ?: break
            if (!game.play(mv)) break
            changed = true
        }
        clockMs[0] = s.optLong("wtime", clockMs[0])
        clockMs[1] = s.optLong("btime", clockMs[1])
        lastTick = SystemClock.elapsedRealtime()
        publishClocks()
        if (changed) refresh { copy(moveTick = moveTick + 1, notice = null) }
        val opp = if (myWhite == true) s.optBoolean("bdraw") else s.optBoolean("wdraw")
        ui = ui.copy(drawOffered = opp && ui.result == null)
        val status = s.optString("status")
        if (status.isNotEmpty() && status != "created" && status != "started") {
            val winner = when (s.optString("winner")) { "white" -> true; "black" -> false; else -> null }
            val reason = when (status) {
                "mate" -> Reason.CHECKMATE
                "resign" -> Reason.RESIGNATION
                "outoftime", "timeout" -> Reason.TIMEOUT
                "stalemate" -> Reason.STALEMATE
                "draw" -> Reason.AGREEMENT
                else -> Reason.ABANDONED
            }
            finish(GameResult(winner, reason))
        }
    }

    private fun sendLichessMove(m: Move) {
        val id = lichessGameId ?: return
        val client = lichessClient ?: return
        io {
            try {
                client.move(id, m.uci())
            } catch (e: Exception) {
                post {
                    if (game.lastMove == m) { game.undo(); refresh() }
                    ui = ui.copy(notice = "Lichess từ chối nước đi: ${e.message ?: ""}")
                }
            }
        }
    }

    override fun onCleared() {
        aiJob?.cancel()
        clockJob?.cancel()
        closeLink()
        stopLobbies()
        seekCall?.cancel()
        eventStream?.cancel()
        gameStream?.cancel()
    }
}
