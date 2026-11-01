package com.customchess.data

import android.content.Context
import com.customchess.ai.AiConfig
import com.customchess.ai.Provider

enum class UserKind { GOOGLE, LICHESS, GUEST }

data class User(val kind: UserKind, val name: String, val email: String = "") {
    val isGuest: Boolean get() = kind == UserKind.GUEST
    /** Tài khoản thật (Google hoặc Lichess), đủ điều kiện đấu online. */
    val isMember: Boolean get() = kind != UserKind.GUEST
    val initial: String get() = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

/**
 * Mọi tùy chọn của người dùng. Mỗi tính năng thêm vào đều có công tắc ở đây để tắt được trong Cài đặt.
 */
data class Settings(
    val playerName: String = "",
    val showHints: Boolean = true,
    val haptics: Boolean = true,
    val fallbackToEngine: Boolean = true,
    val engineLevel: Int = 3,
    val provider: Provider = Provider.GEMINI,

    // ---- giao diện
    /** Mã bàn cờ: có sẵn (lacquer, sky...) hoặc "c:<id>" cho bàn cờ ảnh tự thêm. */
    val boardId: String = "lacquer",
    /** Mã bộ quân: có sẵn (lacquer, royal...) hoặc "c:<id>" cho bộ quân tự thêm. */
    val pieceId: String = "lacquer",
    /** 0 = tối, 1 = sáng, 2 = theo hệ thống. */
    val themeMode: Int = 0,
    /** Đổi màu cả ứng dụng theo màu chủ đạo của bàn cờ đang chọn. */
    val adaptColors: Boolean = true,
    val showCoords: Boolean = true,
    /** Độ đậm lớp phủ lên ô cờ của bàn cờ ảnh (% so với mặc định). */
    val boardVeil: Int = 100,
    /** Nền ứng dụng: 0 = ảnh bàn cờ làm mờ, 1 = dải màu, 2 = phẳng. */
    val bgMode: Int = 0,

    // ---- hiệu ứng và hiệu năng
    val animations: Boolean = true,
    val glass: Boolean = true,
    val glassStrength: Int = 60,
    val aurora: Boolean = true,
    val transitions: Boolean = true,
    val slideAnim: Boolean = true,
    val moveAnimMs: Int = 180,
    val burst: Boolean = true,
    /** 0 = tự động, còn lại là số khung hình/giây tối đa gợi ý cho hệ thống. */
    val fps: Int = 0,
    val batterySaver: Boolean = false,

    // ---- âm thanh
    val sound: Boolean = true,
    val volume: Int = 70,

    // ---- ván cờ
    val autoQueen: Boolean = false,
    val highlightLast: Boolean = true,
    val showCaptured: Boolean = true,
    val keepScreenOn: Boolean = true,
    val allowTakeback: Boolean = true,
    val evalBar: Boolean = false,

    // ---- câu đố
    val puzzleHints: Boolean = true,
    val puzzleAutoNext: Boolean = true,

    // ---- trang chủ
    val homeDaily: Boolean = true,
    val homeStreak: Boolean = true,
    val homeRecent: Boolean = true,
    val homeStats: Boolean = true,

    // ---- sao lưu
    val autoBackup: Boolean = false,
)

data class ApiSlot(val key: String = "", val model: String = "", val baseUrl: String = "")

private val LEGACY_BOARDS = listOf("lacquer", "slate", "graphite", "sky", "dusk", "fire", "rose")
private val LEGACY_PIECES = listOf("lacquer", "royal", "wood", "metal", "jade", "photo", "neon", "line")

/** Lưu cài đặt, phiên đăng nhập và API key trong bộ nhớ riêng của ứng dụng. */
class Store(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("customchess", Context.MODE_PRIVATE)

    fun loadUser(): User? {
        val kind = sp.getString("user.kind", null) ?: return null
        return runCatching {
            User(UserKind.valueOf(kind), sp.getString("user.name", "").orEmpty(), sp.getString("user.email", "").orEmpty())
        }.getOrNull()
    }

    fun saveUser(user: User?) {
        sp.edit().apply {
            if (user == null) {
                remove("user.kind"); remove("user.name"); remove("user.email")
            } else {
                putString("user.kind", user.kind.name)
                putString("user.name", user.name)
                putString("user.email", user.email)
            }
        }.apply()
    }

    fun loadSettings(): Settings {
        val d = Settings()
        fun b(k: String, def: Boolean) = sp.getBoolean(k, def)
        fun i(k: String, def: Int) = sp.getInt(k, def)
        // bản cũ lưu chỉ số; chuyển sang mã
        val boardId = sp.getString("s.boardid", null)
            ?: LEGACY_BOARDS.getOrNull(sp.getInt("s.theme", 0)) ?: d.boardId
        val pieceId = sp.getString("s.pieceid", null)
            ?: LEGACY_PIECES.getOrNull(sp.getInt("s.pieces", 0)) ?: d.pieceId
        return Settings(
            playerName = sp.getString("s.name", "").orEmpty(),
            showHints = b("s.hints", d.showHints),
            haptics = b("s.haptics", d.haptics),
            fallbackToEngine = b("s.fallback", d.fallbackToEngine),
            engineLevel = i("s.level", d.engineLevel).coerceIn(1, 5),
            provider = runCatching { Provider.valueOf(sp.getString("s.provider", Provider.GEMINI.name)!!) }.getOrDefault(Provider.GEMINI),
            boardId = boardId,
            pieceId = pieceId,
            themeMode = i("s.mode", d.themeMode).coerceIn(0, 2),
            adaptColors = b("s.adapt", d.adaptColors),
            showCoords = b("s.coords", d.showCoords),
            boardVeil = i("s.veil", d.boardVeil).coerceIn(0, 160),
            bgMode = i("s.bgmode", d.bgMode).coerceIn(0, 2),
            animations = b("s.anim", d.animations),
            glass = b("s.glass", d.glass),
            glassStrength = i("s.glassk", d.glassStrength).coerceIn(10, 100),
            aurora = b("s.aurora", d.aurora),
            transitions = b("s.trans", d.transitions),
            slideAnim = b("s.slide", d.slideAnim),
            moveAnimMs = i("s.slidems", d.moveAnimMs).coerceIn(60, 500),
            burst = b("s.burst", d.burst),
            fps = i("s.fps", d.fps),
            batterySaver = b("s.saver", d.batterySaver),
            sound = b("s.sound", d.sound),
            volume = i("s.vol", d.volume).coerceIn(0, 100),
            autoQueen = b("s.autoq", d.autoQueen),
            highlightLast = b("s.hilast", d.highlightLast),
            showCaptured = b("s.captured", d.showCaptured),
            keepScreenOn = b("s.awake", d.keepScreenOn),
            allowTakeback = b("s.takeback", d.allowTakeback),
            evalBar = b("s.evalbar", d.evalBar),
            puzzleHints = b("s.phints", d.puzzleHints),
            puzzleAutoNext = b("s.pnext", d.puzzleAutoNext),
            homeDaily = b("s.hdaily", d.homeDaily),
            homeStreak = b("s.hstreak", d.homeStreak),
            homeRecent = b("s.hrecent", d.homeRecent),
            homeStats = b("s.hstats", d.homeStats),
            autoBackup = b("s.autobk", d.autoBackup),
        )
    }

    fun saveSettings(s: Settings) {
        sp.edit()
            .putString("s.name", s.playerName)
            .putBoolean("s.hints", s.showHints)
            .putBoolean("s.haptics", s.haptics)
            .putBoolean("s.fallback", s.fallbackToEngine)
            .putInt("s.level", s.engineLevel)
            .putString("s.provider", s.provider.name)
            .putString("s.boardid", s.boardId)
            .putString("s.pieceid", s.pieceId)
            .putInt("s.mode", s.themeMode)
            .putBoolean("s.adapt", s.adaptColors)
            .putBoolean("s.coords", s.showCoords)
            .putInt("s.veil", s.boardVeil)
            .putInt("s.bgmode", s.bgMode)
            .putBoolean("s.anim", s.animations)
            .putBoolean("s.glass", s.glass)
            .putInt("s.glassk", s.glassStrength)
            .putBoolean("s.aurora", s.aurora)
            .putBoolean("s.trans", s.transitions)
            .putBoolean("s.slide", s.slideAnim)
            .putInt("s.slidems", s.moveAnimMs)
            .putBoolean("s.burst", s.burst)
            .putInt("s.fps", s.fps)
            .putBoolean("s.saver", s.batterySaver)
            .putBoolean("s.sound", s.sound)
            .putInt("s.vol", s.volume)
            .putBoolean("s.autoq", s.autoQueen)
            .putBoolean("s.hilast", s.highlightLast)
            .putBoolean("s.captured", s.showCaptured)
            .putBoolean("s.awake", s.keepScreenOn)
            .putBoolean("s.takeback", s.allowTakeback)
            .putBoolean("s.evalbar", s.evalBar)
            .putBoolean("s.phints", s.puzzleHints)
            .putBoolean("s.pnext", s.puzzleAutoNext)
            .putBoolean("s.hdaily", s.homeDaily)
            .putBoolean("s.hstreak", s.homeStreak)
            .putBoolean("s.hrecent", s.homeRecent)
            .putBoolean("s.hstats", s.homeStats)
            .putBoolean("s.autobk", s.autoBackup)
            .apply()
    }

    fun loadSlot(p: Provider): ApiSlot = ApiSlot(
        key = sp.getString("api.${p.name}.key", "").orEmpty(),
        model = sp.getString("api.${p.name}.model", "").orEmpty().ifEmpty { p.defaultModel },
        baseUrl = sp.getString("api.${p.name}.base", "").orEmpty(),
    )

    fun saveSlot(p: Provider, slot: ApiSlot) {
        sp.edit()
            .putString("api.${p.name}.key", slot.key.trim())
            .putString("api.${p.name}.model", slot.model.trim())
            .putString("api.${p.name}.base", slot.baseUrl.trim())
            .apply()
    }

    fun config(p: Provider): AiConfig {
        val s = loadSlot(p)
        return AiConfig(p, s.key, s.model.ifEmpty { p.defaultModel }, s.baseUrl.ifEmpty { p.defaultBase })
    }

    var lichessToken: String
        get() = sp.getString("lichess.token", "").orEmpty()
        set(v) { sp.edit().putString("lichess.token", v.trim()).apply() }

    var lichessName: String
        get() = sp.getString("lichess.name", "").orEmpty()
        set(v) { sp.edit().putString("lichess.name", v).apply() }

    /** Điểm xếp hạng Lichess lần cuối lấy được: blitz, rapid, classical (0 = chưa có). */
    var lichessRatings: List<Int>
        get() = (sp.getString("lichess.ratings", "") ?: "").split(',').map { it.toIntOrNull() ?: 0 }
            .let { l -> List(3) { l.getOrElse(it) { 0 } } }
        set(v) { sp.edit().putString("lichess.ratings", v.joinToString(",")).apply() }

    // PKCE đang chờ hoàn tất (đăng nhập Lichess bằng trình duyệt)
    var pkceVerifier: String
        get() = sp.getString("pkce.verifier", "").orEmpty()
        set(v) { sp.edit().putString("pkce.verifier", v).apply() }

    var pkceState: String
        get() = sp.getString("pkce.state", "").orEmpty()
        set(v) { sp.edit().putString("pkce.state", v).apply() }

    // ---- tiến độ (câu đố, chuỗi ngày, bài học), lưu dạng JSON
    var progressJson: String
        get() = sp.getString("progress", "").orEmpty()
        set(v) { sp.edit().putString("progress", v).apply() }

    /** Lần sao lưu Google gần nhất (mili giây, 0 = chưa từng). */
    var lastBackup: Long
        get() = sp.getLong("backup.last", 0L)
        set(v) { sp.edit().putLong("backup.last", v).apply() }

    /** Tên file Drive đã dùng cho bản sao lưu. */
    var backupFileId: String
        get() = sp.getString("backup.fileid", "").orEmpty()
        set(v) { sp.edit().putString("backup.fileid", v).apply() }
}
