// Sinh bộ câu đố cờ vua offline bằng chính luật cờ và engine trong app.
//
//   kotlinc app/src/main/java/com/customchess/core/Chess.kt app/src/main/java/com/customchess/core/Engine.kt \
//           tools/PuzzleGen.kt -include-runtime -d puzzlegen.jar
//   java -jar puzzlegen.jar <phút> <file ra> [hạt giống]
//
// Cách làm: cho hai bên "chơi" các ván có lẫn nước đi yếu, tại mỗi thế sau một nước đi của đối thủ thì hỏi
//   (1) bên tới lượt có chiếu hết trong 1, 2 hoặc 3 nước không (đủ nghiêm ngặt: duy nhất với mate-in-2/3), hoặc
//   (2) nước đối thủ vừa đi có phải là sai lầm lớn mà bên tới lượt có nước ăn điểm duy nhất, được kiểm tra ở nhiều độ sâu.
// Mỗi dòng ra: id|fen_trước_nước_của_đối_thủ|nước_đối_thủ nước_giải ...|điểm|chủ_đề
// (cùng cấu trúc với CSV của Lichess nên ứng dụng dùng chung một bộ đọc cho bộ câu đố có sẵn và câu đố tải từ Lichess).
import com.customchess.core.Engine
import com.customchess.core.Move
import com.customchess.core.Pc
import com.customchess.core.Position
import com.customchess.core.fileOf
import com.customchess.core.rankOf
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.random.Random

const val INF = 1_000_000
const val MATE = 100_000
val VAL: IntArray = Engine.VALUE

class Root(val best: Move, val bestScore: Int, val secondScore: Int, val legalCount: Int)

class Srch {
    var nodes = 0L

    fun order(pos: Position, moves: List<Move>): List<Move> {
        val b = pos.board
        return moves.sortedByDescending { m ->
            var s = 0
            val victim = abs(b[m.to])
            if (victim != 0) s += 10 * VAL[victim] - VAL[abs(b[m.from])] / 10 + 1000
            else if (abs(b[m.from]) == Pc.PAWN && fileOf(m.from) != fileOf(m.to)) s += 1000 + 10 * VAL[Pc.PAWN]
            if (m.promo != 0) s += VAL[m.promo] + 500
            s
        }
    }

    fun qs(pos: Position, alphaIn: Int, beta: Int, qd: Int): Int {
        nodes++
        val stand = Engine.evaluateForSide(pos)
        if (stand >= beta) return beta
        var alpha = maxOf(alphaIn, stand)
        if (qd >= 6) return alpha
        val caps = pos.legalMoves().filter { pos.isCapture(it) || it.promo == Pc.QUEEN }
        for (m in order(pos, caps)) {
            val s = -qs(pos.apply(m), -beta, -alpha, qd + 1)
            if (s >= beta) return beta
            if (s > alpha) alpha = s
        }
        return alpha
    }

    fun nm(pos: Position, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
        nodes++
        if (pos.halfmove >= 100 || pos.insufficientMaterial()) return 0
        val legal = pos.legalMoves()
        if (legal.isEmpty()) return if (pos.inCheck()) -(MATE - ply) else 0
        if (depth <= 0) return qs(pos, alphaIn, beta, 0)
        var alpha = alphaIn
        for (m in order(pos, legal)) {
            val s = -nm(pos.apply(m), depth - 1, -beta, -alpha, ply + 1)
            if (s >= beta) return beta
            if (s > alpha) alpha = s
        }
        return alpha
    }

    /** Điểm tốt nhất và nhì (chính xác cho hai nước đầu) cho bên đang tới lượt. */
    fun root(pos: Position, depth: Int): Root? {
        val legal = pos.legalMoves()
        if (legal.isEmpty()) return null
        var best = -INF
        var second = -INF
        var bestMove = legal[0]
        for (m in order(pos, legal)) {
            val s = -nm(pos.apply(m), depth - 1, -INF, -second, 1)
            if (s > best) { second = best; best = s; bestMove = m }
            else if (s > second) second = s
        }
        return Root(bestMove, best, second, legal.size)
    }
}

// ----------------------------------------------------------------------------------------- chiếu hết

fun forcesMate(pos: Position, n: Int, checksOnly: Boolean): Boolean {
    val replies = pos.legalMoves()
    if (replies.isEmpty()) return pos.inCheck()
    if (n == 0) return false
    for (r in replies) {
        val p2 = pos.apply(r)
        var found = false
        for (m in p2.legalMoves()) {
            val p3 = p2.apply(m)
            if (checksOnly && n > 1 && !p3.inCheck()) continue
            if (forcesMate(p3, n - 1, checksOnly)) { found = true; break }
        }
        if (!found) return false
    }
    return true
}

fun mateFirstMoves(pos: Position, n: Int): List<Move> {
    val res = ArrayList<Move>()
    for (m in pos.legalMoves()) {
        val child = pos.apply(m)
        // mate-in-3 chỉ xét chuỗi chiếu liên tục cho nhanh; mate-in-2 xét mọi nước (kể cả nước yên lặng)
        if (n >= 3 && !child.inCheck()) continue
        if (forcesMate(child, n - 1, checksOnly = n >= 3)) res.add(m)
    }
    return res
}

/** Đường chiếu hết: bên tấn công chọn nước dẫn tới chiếu hết nhanh nhất, bên thủ chọn nước kéo dài nhất. */
fun mateLine(start: Position, first: Move, n: Int): List<Move>? {
    val line = ArrayList<Move>()
    line.add(first)
    var pos = start.apply(first)
    var left = n - 1
    while (true) {
        val replies = pos.legalMoves()
        if (replies.isEmpty()) return if (pos.inCheck()) line else null
        if (left == 0) return null
        // thủ: ưu tiên nước khiến bị chiếu hết chậm hơn (không chiếu hết ngay ở nước kế)
        var chosen: Move? = null
        var chosenNext: Move? = null
        var chosenImmediate = true
        for (r in replies) {
            val p2 = pos.apply(r)
            var nxt: Move? = null
            for (m in p2.legalMoves()) {
                if (forcesMate(p2.apply(m), left - 1, checksOnly = left - 1 >= 2)) { nxt = m; break }
            }
            if (nxt == null) return null
            val immediate = p2.apply(nxt).legalMoves().isEmpty()
            if (chosen == null || (chosenImmediate && !immediate)) { chosen = r; chosenNext = nxt; chosenImmediate = immediate }
        }
        line.add(chosen!!); line.add(chosenNext!!)
        pos = pos.apply(chosen).apply(chosenNext)
        left--
    }
}

// ----------------------------------------------------------------------------------------- chủ đề

fun solverWhite(p: Position) = p.whiteToMove

fun pieceCount(p: Position): Int { var n = 0; for (v in p.board) if (v != 0 && abs(v) != Pc.PAWN && abs(v) != Pc.KING) n++; return n }

fun materialFor(p: Position, white: Boolean): Int {
    var s = 0
    for (v in p.board) if (v != 0 && abs(v) != Pc.KING) s += (if ((v > 0) == white) 1 else -1) * VAL[abs(v)]
    return s
}

fun phaseTheme(p: Position): String = when {
    pieceCount(p) <= 5 -> "endgame"
    p.fullmove <= 12 && pieceCount(p) >= 12 -> "opening"
    else -> "middlegame"
}

fun attackedTargets(after: Position, to: Int, solverIsWhite: Boolean): List<Int> {
    val pass = Position(after.board.copyOf(), solverIsWhite, 0, -1, 0, 1)
    return pass.legalMoves().filter { it.from == to && after.board[it.to] != 0 && abs(after.board[it.to]) != Pc.KING }.map { it.to }
}

fun isBackRank(mated: Position): Boolean {
    val k = mated.kingSquare(mated.whiteToMove)
    val r = rankOf(k)
    if (r != 0 && r != 7) return false
    val dir = if (r == 0) 1 else -1
    var blocked = 0
    for (df in -1..1) {
        val f = fileOf(k) + df
        if (f !in 0..7) { blocked++; continue }
        val sq = (r + dir) * 8 + f
        val v = mated.board[sq]
        if (v != 0 && (v > 0) == mated.whiteToMove) blocked++
    }
    return blocked >= 2
}

fun isSmothered(mated: Position, last: Move, before: Position): Boolean {
    if (abs(before.board[last.from]) != Pc.KNIGHT) return false
    val k = mated.kingSquare(mated.whiteToMove)
    for (dr in -1..1) for (df in -1..1) {
        if (dr == 0 && df == 0) continue
        val r = rankOf(k) + dr; val f = fileOf(k) + df
        if (r !in 0..7 || f !in 0..7) continue
        val v = mated.board[r * 8 + f]
        if (v == 0 || (v > 0) != mated.whiteToMove) return false
    }
    return true
}

// ----------------------------------------------------------------------------------------- ván giả lập

class Puz(val id: String, val fen0: String, val moves: List<Move>, val rating: Int, val themes: List<String>) {
    fun line() = "$id|$fen0|${moves.joinToString(" ") { it.uci() }}|$rating|${themes.joinToString(" ")}"
}

fun idOf(fen: String): String {
    var h = 1469598103934665603L
    for (c in fen) { h = (h xor c.code.toLong()) * 1099511628211L }
    val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val sb = StringBuilder()
    var v = h and Long.MAX_VALUE
    repeat(5) { sb.append(chars[(v % chars.length).toInt()]); v /= chars.length }
    return sb.toString()
}

fun pickMove(pos: Position, rnd: Random, s: Srch, ply: Int): Move {
    val legal = pos.legalMoves()
    if (ply < 6) return legal.random(rnd)
    val scored = legal.map { m -> m to -s.qs(pos.apply(m), -INF, INF, 0) }.sortedByDescending { it.second }
    val r = rnd.nextDouble()
    return when {
        r < 0.52 -> scored[0].first
        r < 0.78 -> scored[minOf(rnd.nextInt(3), scored.size - 1)].first
        r < 0.94 -> scored[minOf(rnd.nextInt(8), scored.size - 1)].first
        else -> legal.random(rnd)
    }
}

class Quotas {
    val cap = mapOf("m1" to 150, "m2" to 120, "m3" to 50, "mat" to 280)
    val have = cap.keys.associateWith { AtomicInteger(0) }
    fun full() = cap.all { (k, v) -> have[k]!!.get() >= v }
    fun open(k: String) = have[k]!!.get() < cap[k]!!
    fun add(k: String) { have[k]!!.incrementAndGet() }
    /** Câu "ăn quân treo" quá dễ nên chỉ chiếm phần nhỏ của nhóm ăn quân. */
    val hang = AtomicInteger(0)
    val hangCap = 60
}

fun analyse(p0: Position, m0: Move, rnd: Random, s: Srch, q: Quotas): Puz? {
    val p1 = p0.apply(m0)
    val solverIsWhite = p1.whiteToMove
    val legal1 = p1.legalMoves()
    if (legal1.size < 2 || p1.insufficientMaterial()) return null
    val fen0 = p0.toFen()
    val phase = phaseTheme(p1)

    // --- chiếu hết ---
    val enemyKing = p1.kingSquare(!solverIsWhite)
    val kingEdge = enemyKing >= 0 && (rankOf(enemyKing) in listOf(0, 7) || fileOf(enemyKing) in listOf(0, 7))
    var free = 0
    if (enemyKing >= 0) for (dr in -1..1) for (df in -1..1) {
        if (dr == 0 && df == 0) continue
        val r = rankOf(enemyKing) + dr; val f = fileOf(enemyKing) + df
        if (r !in 0..7 || f !in 0..7) continue
        val v = p1.board[r * 8 + f]
        if (v == 0 || (v > 0) == solverIsWhite) free++
    }
    val mating = kingEdge || free <= 3
    val m1list = if (mating) mateFirstMoves(p1, 1) else emptyList()
    if (mating) {
        for (n in 1..3) {
            val key = "m$n"
            if (!q.open(key)) continue
            if (n == 3 && pieceCount(p1) < 3) continue
            if (n >= 2 && m1list.isNotEmpty()) break // đã có chiếu hết trong 1 nước thì không phải câu 2/3 nước
            val firsts = if (n == 1) m1list else mateFirstMoves(p1, n)
            if (firsts.isEmpty()) continue
            if (n >= 2 && firsts.size != 1) break // cần duy nhất
            val first = firsts[0]
            val line = mateLine(p1, first, n) ?: break
            if (n == 1) {
                // mate-in-1: chỉ lấy khi có đúng 1-2 nước chiếu hết, tránh thế quá dễ lặp
                if (firsts.size > 2) break
            }
            val finalPos = line.fold(p1) { p, m -> p.apply(m) }
            val themes = ArrayList<String>()
            themes.add("mate"); themes.add("mateIn$n")
            if (isBackRank(finalPos)) themes.add("backRankMate")
            if (isSmothered(finalPos, line.last(), line.dropLast(1).fold(p1) { p, m -> p.apply(m) })) themes.add("smotheredMate")
            if (line.any { it.promo != 0 }) themes.add("promotion")
            themes.add(if (n == 1) "oneMove" else if (n == 2) "short" else "long")
            themes.add(phase)
            val quiet = !p1.isCapture(first) && !p1.apply(first).inCheck()
            if (quiet) themes.add("quietMove")
            val base = when (n) { 1 -> 520; 2 -> 960; else -> 1420 }
            val rating = (base + rnd.nextInt(-90, 200) + (if (quiet) 120 else 0) + pieceCount(p1) * 8).coerceIn(400, 2300)
            q.add(key)
            return Puz(idOf(fen0), fen0, listOf(m0) + line, rating, themes)
        }
    }

    // --- ăn quân / đòn chiến thuật ---
    if (!q.open("mat")) return null
    val matBefore = materialFor(p0, solverIsWhite)
    if (matBefore > 500) return null
    val r0 = s.root(p0, 3) ?: return null            // đối thủ đáng lẽ nên đi thế nào
    val r3 = s.root(p1, 3) ?: return null
    if (r3.bestScore + r0.bestScore < 200) return null
    val r4 = s.root(p1, 4) ?: return null
    if (r4.bestScore < 180 || r4.bestScore - r4.secondScore < 170 || r4.legalCount < 2) return null
    if (abs(r4.bestScore) > MATE / 2) return null
    val r5 = s.root(p1, 5) ?: return null
    if (r5.best != r4.best || r5.bestScore < 150 || r5.bestScore - r5.secondScore < 130) return null

    // độ khó theo độ sâu nhỏ nhất mà engine thấy nước này
    var foundAt = 5
    for (d in 1..4) {
        val rd = s.root(p1, d) ?: break
        if (rd.best == r4.best) {
            var stable = true
            for (d2 in d + 1..4) { val rr = s.root(p1, d2); if (rr == null || rr.best != r4.best) { stable = false; break } }
            if (stable) { foundAt = d; break }
        }
    }

    val line = ArrayList<Move>()
    line.add(r4.best)
    var pos = p1.apply(r4.best)
    var gain = r4.bestScore
    for (step in 1..2) {
        val reply = s.root(pos, 4) ?: break
        val p2 = pos.apply(reply.best)
        val next = s.root(p2, 4) ?: break
        if (next.bestScore < gain - 160 || next.bestScore - next.secondScore < 150 || next.legalCount < 2) break
        // đừng kéo dài khi đã lời hẳn vì ăn quân lớn rồi và nước kế chỉ là ăn nốt đơn giản
        line.add(reply.best); line.add(next.best)
        pos = p2.apply(next.best)
        gain = next.bestScore
    }

    val m1 = line[0]
    val after1 = p1.apply(m1)
    val themes = ArrayList<String>()
    themes.add(if (gain >= 500) "crushing" else "advantage")
    val capturedValue = abs(p1.board[m1.to])
    val moverVal = VAL[abs(p1.board[m1.from])]
    val recapturable = after1.isAttacked(m1.to, !solverIsWhite)
    if (capturedValue != 0 && !recapturable) themes.add("hangingPiece")
    val targets = attackedTargets(after1, m1.to, solverIsWhite)
    val worthy = targets.filter { abs(after1.board[it]) != Pc.PAWN && (VAL[abs(after1.board[it])] >= moverVal || !after1.isAttacked(it, !solverIsWhite)) }
    if (worthy.size >= 2 || (after1.inCheck() && worthy.isNotEmpty())) themes.add("fork")
    val stand = Engine.evaluateForSide(p1)
    val afterQ = -s.qs(after1, -INF, INF, 0)
    if (afterQ < stand - 150 && gain >= 250) themes.add("sacrifice")
    if (line.any { it.promo != 0 }) themes.add("promotion")
    if (after1.inCheck() && !themes.contains("fork")) themes.add("check")
    themes.add(if (line.size == 1) "oneMove" else if (line.size == 3) "short" else "long")
    themes.add(phase)
    if (themes.contains("hangingPiece")) {
        if (q.hang.incrementAndGet() > q.hangCap) return null
    }
    var rating = when (foundAt) { 1 -> 720; 2 -> 1000; 3 -> 1330; 4 -> 1650; else -> 1900 }
    rating += rnd.nextInt(-110, 130) + (line.size / 2) * 90
    if (themes.contains("hangingPiece")) rating -= 140
    if (themes.contains("sacrifice")) rating += 120
    rating = rating.coerceIn(450, 2400)
    q.add("mat")
    return Puz(idOf(fen0), fen0, listOf(m0) + line, rating, themes)
}

fun worker(seed: Int, deadline: Long, q: Quotas, out: ConcurrentHashMap<String, Puz>, log: (String) -> Unit) {
    val rnd = Random(seed)
    val s = Srch()
    var games = 0
    while (System.currentTimeMillis() < deadline && !q.full()) {
        games++
        var cooldown = 0
        var madeInGame = 0
        var pos = Position.start()
        var ply = 0
        val maxPly = 90 + rnd.nextInt(40)
        while (ply < maxPly && System.currentTimeMillis() < deadline && !q.full()) {
            val legal = pos.legalMoves()
            if (legal.isEmpty() || pos.insufficientMaterial() || pos.halfmove >= 100) break
            val m = pickMove(pos, rnd, s, ply)
            if (cooldown > 0) cooldown--
            else if (ply >= 10 && madeInGame < 2 && rnd.nextDouble() < 0.55) {
                val pz = try { analyse(pos, m, rnd, s, q) } catch (e: Exception) { null }
                if (pz != null) {
                    cooldown = 8 + rnd.nextInt(6); madeInGame++
                    val key = pz.fen0
                    if (out.putIfAbsent(key, pz) != null) { /* trùng, bỏ */ }
                    else log("[${out.size}] m1=${q.have["m1"]} m2=${q.have["m2"]} m3=${q.have["m3"]} mat=${q.have["mat"]}  ${pz.line()}")
                }
            }
            pos = pos.apply(m)
            ply++
        }
    }
    log("worker $seed xong sau $games ván")
}

fun verify(p: Puz): Boolean {
    var pos = Position.fromFen(p.fen0) ?: return false
    for (m in p.moves) {
        if (m !in pos.legalMoves()) return false
        pos = pos.apply(m)
    }
    if (p.themes.contains("mate")) return pos.legalMoves().isEmpty() && pos.inCheck()
    return true
}

fun main(args: Array<String>) {
    val minutes = args.getOrNull(0)?.toDoubleOrNull() ?: 5.0
    val outFile = File(args.getOrNull(1) ?: "puzzles.txt")
    val seed = args.getOrNull(2)?.toIntOrNull() ?: 7
    val deadline = System.currentTimeMillis() + (minutes * 60_000).toLong()
    val q = Quotas()
    val found = ConcurrentHashMap<String, Puz>()
    val lock = Any()
    val logf = File(outFile.path + ".log")
    logf.writeText("")
    val log: (String) -> Unit = { synchronized(lock) { logf.appendText(it + "\n") } }
    val threads = (0 until 2).map { i -> Thread { worker(seed * 100 + i, deadline, q, found, log) }.also { it.start() } }
    threads.forEach { it.join() }
    val good = found.values.filter { verify(it) }.sortedBy { it.rating }
    outFile.writeText(good.joinToString("\n") { it.line() } + "\n")
    log("ghi ${good.size}/${found.size} câu đố vào ${outFile.path}; hạn mức: " + q.have.entries.joinToString { "${it.key}=${it.value}" })
}
