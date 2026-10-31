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

data class Settings(
    val playerName: String = "",
    val showHints: Boolean = true,
    val boardTheme: Int = 0,
    val haptics: Boolean = true,
    val fallbackToEngine: Boolean = true,
    val engineLevel: Int = 3,
    val provider: Provider = Provider.GEMINI,
    /** Chỉ số trong [com.customchess.ui.PIECE_STYLES]. */
    val pieceSet: Int = 0,
    /** 0 = tối, 1 = sáng, 2 = theo hệ thống. */
    val themeMode: Int = 0,
    /** Đổi màu cả ứng dụng theo màu chủ đạo của bàn cờ đang chọn. */
    val adaptColors: Boolean = true,
    val showCoords: Boolean = true,
)

data class ApiSlot(val key: String = "", val model: String = "", val baseUrl: String = "")

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

    fun loadSettings(): Settings = Settings(
        playerName = sp.getString("s.name", "").orEmpty(),
        showHints = sp.getBoolean("s.hints", true),
        boardTheme = sp.getInt("s.theme", 0),
        haptics = sp.getBoolean("s.haptics", true),
        fallbackToEngine = sp.getBoolean("s.fallback", true),
        engineLevel = sp.getInt("s.level", 3).coerceIn(1, 5),
        provider = runCatching { Provider.valueOf(sp.getString("s.provider", Provider.GEMINI.name)!!) }
            .getOrDefault(Provider.GEMINI),
        pieceSet = sp.getInt("s.pieces", 0),
        themeMode = sp.getInt("s.mode", 0).coerceIn(0, 2),
        adaptColors = sp.getBoolean("s.adapt", true),
        showCoords = sp.getBoolean("s.coords", true),
    )

    fun saveSettings(s: Settings) {
        sp.edit()
            .putString("s.name", s.playerName)
            .putBoolean("s.hints", s.showHints)
            .putInt("s.theme", s.boardTheme)
            .putBoolean("s.haptics", s.haptics)
            .putBoolean("s.fallback", s.fallbackToEngine)
            .putInt("s.level", s.engineLevel)
            .putString("s.provider", s.provider.name)
            .putInt("s.pieces", s.pieceSet)
            .putInt("s.mode", s.themeMode)
            .putBoolean("s.adapt", s.adaptColors)
            .putBoolean("s.coords", s.showCoords)
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
}
