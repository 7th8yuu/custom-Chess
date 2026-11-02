package com.customchess.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.customchess.color.Dominant
import com.customchess.color.Hct
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max

/** Một bàn cờ ảnh do người dùng thêm. [file] là tên file trong thư mục boards; [seed] là màu chủ đạo đã tính lúc nhập. */
data class CustomBoard(val id: String, val name: String, val file: String, val seed: Int, val muted: Boolean)

/** Một bộ quân do người dùng thêm (thư mục pieces/<id> chứa set.json và các ảnh quân). */
data class CustomPieces(val id: String, val name: String)

class CustomException(message: String) : Exception(message)

/**
 * Kho bàn cờ và bộ quân tự thêm, lưu trong bộ nhớ riêng của ứng dụng (filesDir/customs).
 * Cũng là thứ được sao lưu lên Google Drive: [exportZip] nén cả thư mục, [restoreZip] giải nén lại.
 *
 * Định dạng bộ quân (set.json), mọi trường đều tùy chọn:
 * {
 *   "name": "Bộ của tôi",
 *   "base": "royal",                      // bộ có sẵn để lấy hình dạng và màu mặc định
 *   "colors": { "whiteTop": "#FFFFFF", "whiteBottom": "#D8C8A0", "blackTop": "#4A3F3A", "blackBottom": "#140E0C",
 *               "whiteLine": "#2B1D17", "blackLine": "#E3C98A" },
 *   "style": { "lineWidth": 4.4, "fillAlpha": 1.0, "gloss": 0.2, "shadow": 0.2, "glow": false },
 *   "paths":  { "wK": "M46 6 H54 ...", ... },       // đường vẽ SVG trong khung 100x100, thay hình dạng quân
 *   "images": { "wK": "wK.png" | "data:image/png;base64,..." | "https://.../wK.png", ... }  // ảnh PNG thay cả quân
 * }
 * Khóa quân: chữ w hoặc b (trắng, đen) + P N B R Q K (tốt, mã, tượng, xe, hậu, vua).
 */
class Customs(context: android.content.Context) {
    private val root = File(context.applicationContext.filesDir, "customs").also { it.mkdirs() }
    val boardsDir = File(root, "boards").also { it.mkdirs() }
    private val piecesDir = File(root, "pieces").also { it.mkdirs() }
    private val boardsFile = File(root, "boards.json")

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }

    // -------------------------------------------------------------------------------- bàn cờ ảnh

    fun loadBoards(): List<CustomBoard> = runCatching {
        if (!boardsFile.exists()) emptyList()
        else {
            val a = JSONArray(boardsFile.readText())
            List(a.length()) {
                val o = a.getJSONObject(it)
                CustomBoard(o.getString("id"), o.optString("name", "Ảnh của tôi"), o.getString("file"), o.optInt("seed"), o.optBoolean("muted"))
            }.filter { File(boardsDir, it.file).exists() }
        }
    }.getOrDefault(emptyList())

    private fun saveBoards(list: List<CustomBoard>) {
        val a = JSONArray()
        list.forEach { b ->
            a.put(JSONObject().put("id", b.id).put("name", b.name).put("file", b.file).put("seed", b.seed).put("muted", b.muted))
        }
        boardsFile.writeText(a.toString())
    }

    fun boardPath(b: CustomBoard): String = File(boardsDir, b.file).path

    /** Giải mã ảnh (đã thu nhỏ), lưu thành JPEG và tính màu chủ đạo bằng thuật toán chọn màu của Material. */
    fun addBoard(bytes: ByteArray, name: String): CustomBoard {
        val bmp = decodeScaled(bytes, 1600) ?: throw CustomException("Không đọc được ảnh này (cần JPG, PNG hoặc WebP)")
        val id = UUID.randomUUID().toString().take(8)
        val file = "$id.jpg"
        File(boardsDir, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val seed = seedOf(bmp)
        bmp.recycle()
        val b = CustomBoard(id, name.ifBlank { "Ảnh của tôi" }.take(30), file, seed, Hct.cam(seed).chroma < 10.0)
        saveBoards(loadBoards() + b)
        return b
    }

    fun deleteBoard(id: String) {
        val list = loadBoards()
        list.firstOrNull { it.id == id }?.let { File(boardsDir, it.file).delete() }
        saveBoards(list.filter { it.id != id })
    }

    private fun seedOf(bmp: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bmp, 100, 100, true)
        val px = IntArray(100 * 100)
        small.getPixels(px, 0, 100, 0, 0, 100, 100)
        if (small !== bmp) small.recycle()
        return Dominant.seed(px)
    }

    private fun decodeScaled(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val edge = max(raw.width, raw.height)
        if (edge <= maxEdge) return raw
        val k = maxEdge.toFloat() / edge
        val out = Bitmap.createScaledBitmap(raw, (raw.width * k).toInt().coerceAtLeast(1), (raw.height * k).toInt().coerceAtLeast(1), true)
        if (out !== raw) raw.recycle()
        return out
    }

    // -------------------------------------------------------------------------------- tải từ URL

    /** Tải tối đa [limit] byte từ một địa chỉ http(s). */
    fun download(url: String, limit: Int = 15 * 1024 * 1024): ByteArray {
        val u = url.trim()
        if (!(u.startsWith("https://") || u.startsWith("http://"))) throw CustomException("Địa chỉ phải bắt đầu bằng http:// hoặc https://")
        val req = try { Request.Builder().url(u).header("User-Agent", "CustomChess-Android").build() } catch (e: Exception) { throw CustomException("Địa chỉ không hợp lệ") }
        try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw CustomException("Máy chủ trả về lỗi ${r.code}")
                val body = r.body ?: throw CustomException("Phản hồi rỗng")
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                body.byteStream().use { s ->
                    while (true) {
                        val n = s.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > limit) throw CustomException("Tệp quá lớn")
                    }
                }
                return out.toByteArray()
            }
        } catch (e: CustomException) {
            throw e
        } catch (e: Exception) {
            throw CustomException("Không tải được: ${e.message ?: "lỗi mạng"}")
        }
    }

    // -------------------------------------------------------------------------------- bộ quân

    fun loadPieces(): List<CustomPieces> {
        val dirs = piecesDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { d ->
            val j = runCatching { JSONObject(File(d, "set.json").readText()) }.getOrNull() ?: return@mapNotNull null
            CustomPieces(d.name, j.optString("name", "Bộ quân của tôi"))
        }.sortedBy { File(piecesDir, it.id).lastModified() }
    }

    fun piecesDir(id: String): File = File(piecesDir, id)

    fun deletePieces(id: String) {
        File(piecesDir, id).deleteRecursively()
    }

    /** Mã quân hợp lệ: wP, bK... */
    private fun validKey(k: String) = k.length == 2 && k[0] in "wb" && k[1] in "PNBRQK"

    private fun normKey(raw: String): String? {
        val s = raw.trim()
        if (s.length == 2) {
            val k = "${s[0].lowercaseChar()}${s[1].uppercaseChar()}"
            if (validKey(k)) return k
        }
        return null
    }

    /**
     * Nhập bộ quân từ JSON (định dạng ở trên). Ảnh có thể là data URI, base64 trần hoặc địa chỉ http(s) (tải về lúc nhập).
     * [extraFiles] là các ảnh đi kèm (từ ZIP), khóa là tên file viết thường.
     */
    fun addPiecesJson(text: String, nameHint: String, extraFiles: Map<String, ByteArray> = emptyMap()): CustomPieces {
        val src = try { JSONObject(text.trim()) } catch (e: Exception) { throw CustomException("JSON không hợp lệ: ${e.message?.take(80)}") }
        val id = UUID.randomUUID().toString().take(8)
        val dir = File(piecesDir, id).also { it.mkdirs() }
        try {
            val out = JSONObject()
            val name = src.optString("name").ifBlank { nameHint }.ifBlank { "Bộ quân của tôi" }.take(30)
            out.put("name", name)
            src.optString("base").takeIf { it.isNotBlank() }?.let { out.put("base", it) }
            src.optJSONObject("colors")?.let { out.put("colors", it) }
            src.optJSONObject("style")?.let { out.put("style", it) }

            val paths = JSONObject()
            src.optJSONObject("paths")?.let { p ->
                for (k in p.keys()) {
                    val key = normKey(k) ?: continue
                    val d = p.optString(k)
                    if (d.length in 4..4000) paths.put(key, d)
                }
            }
            if (paths.length() > 0) out.put("paths", paths)

            val images = JSONObject()
            val imgSrc = src.optJSONObject("images")
            val keys = LinkedHashSet<String>()
            imgSrc?.keys()?.forEach { k -> normKey(k)?.let { keys.add(it) } }
            // ảnh đi kèm trong ZIP có tên là mã quân
            extraFiles.keys.forEach { fn ->
                val base = fn.substringBeforeLast('.')
                normKey(base)?.let { keys.add(it) }
            }
            for (key in keys) {
                val rawKey = imgSrc?.keys()?.asSequence()?.firstOrNull { normKey(it) == key }
                val ref = rawKey?.let { imgSrc.optString(it) }.orEmpty()
                val bytes: ByteArray? = when {
                    ref.startsWith("data:") -> Base64.decode(ref.substringAfter("base64,"), Base64.DEFAULT)
                    ref.startsWith("http://") || ref.startsWith("https://") -> download(ref, 4 * 1024 * 1024)
                    ref.isNotEmpty() && extraFiles.containsKey(ref.lowercase()) -> extraFiles[ref.lowercase()]
                    ref.isNotEmpty() && ref.length > 200 -> Base64.decode(ref, Base64.DEFAULT)
                    else -> extraFiles.entries.firstOrNull { normKey(it.key.substringBeforeLast('.')) == key }?.value
                }
                if (bytes == null) continue
                val bmp = decodeScaled(bytes, 384) ?: throw CustomException("Ảnh quân $key không đọc được")
                File(dir, "$key.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                images.put(key, "$key.png")
            }
            if (images.length() > 0) out.put("images", images)

            if (!out.has("base") && paths.length() == 0 && images.length() == 0 && !out.has("colors")) {
                throw CustomException("Bộ quân trống: cần ít nhất một trong các trường base, colors, paths hoặc images")
            }
            File(dir, "set.json").writeText(out.toString())
            return CustomPieces(id, name)
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw if (e is CustomException) e else CustomException("Không nhập được bộ quân: ${e.message ?: "lỗi"}")
        }
    }

    /** Nhập bộ quân từ file ZIP: set.json (tùy chọn) và các ảnh đặt tên wP.png ... bK.png. */
    fun addPiecesZip(bytes: ByteArray, nameHint: String): CustomPieces {
        val files = HashMap<String, ByteArray>()
        var json: String? = null
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                var n = 0
                while (true) {
                    val e: ZipEntry = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    if (++n > 60) throw CustomException("ZIP có quá nhiều tệp")
                    val fn = e.name.substringAfterLast('/').lowercase()
                    val data = readLimited(z, 4 * 1024 * 1024)
                    if (fn == "set.json" || fn.endsWith(".json")) json = String(data, Charsets.UTF_8)
                    else if (fn.endsWith(".png") || fn.endsWith(".webp") || fn.endsWith(".jpg") || fn.endsWith(".jpeg")) files[fn] = data
                }
            }
        } catch (e: CustomException) {
            throw e
        } catch (e: Exception) {
            throw CustomException("Không đọc được ZIP: ${e.message ?: "lỗi"}")
        }
        if (json == null && files.isEmpty()) throw CustomException("ZIP không có set.json hay ảnh quân nào")
        return addPiecesJson(json ?: "{}", nameHint.ifBlank { "Bộ quân ZIP" }, files)
    }

    private fun readLimited(z: ZipInputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = z.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > limit) throw CustomException("Một tệp trong ZIP quá lớn")
        }
        return out.toByteArray()
    }

    /** Mẫu JSON để người dùng chỉnh sửa. */
    fun template(): String = """{
  "name": "Bộ quân của tôi",
  "base": "royal",
  "colors": {
    "whiteTop": "#FFFBF1", "whiteBottom": "#E2D1AA",
    "blackTop": "#46403B", "blackBottom": "#141010",
    "whiteLine": "#3A2A1D", "blackLine": "#CDB783"
  },
  "style": { "lineWidth": 4.4, "fillAlpha": 1.0, "gloss": 0.16, "shadow": 0.2, "glow": false },
  "paths": {
    "wP": "M50 16 C58 16 62 22 62 28 C62 34 57 38 50 38 C43 38 38 34 38 28 C38 22 42 16 50 16 Z M30 84 H70 V90 H30 Z M40 38 H60 L66 84 H34 Z"
  },
  "images": {
    "bK": "https://example.com/bK.png"
  }
}"""

    // -------------------------------------------------------------------------------- sao lưu

    /**
     * Nén toàn bộ thư mục customs thành một ZIP (để đưa lên Google Drive). [meta] là các tệp phụ
     * (tiến độ, lựa chọn giao diện) được ghi vào thư mục "meta/" trong ZIP.
     */
    fun exportZip(meta: Map<String, ByteArray> = emptyMap()): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(root).path.replace('\\', '/')
                z.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
            for ((name, data) in meta) {
                z.putNextEntry(ZipEntry("meta/$name"))
                z.write(data)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun hasAnything(): Boolean = loadBoards().isNotEmpty() || loadPieces().isNotEmpty()

    /**
     * Khôi phục từ ZIP sao lưu: thay thế toàn bộ kho hiện tại. Trả về các tệp "meta/" (tên -> nội dung),
     * hoặc null nếu ZIP không hợp lệ.
     */
    fun restoreZip(bytes: ByteArray): Map<String, ByteArray>? {
        val meta = HashMap<String, ByteArray>()
        val staging = File(root.parentFile, "customs_restore").also { it.deleteRecursively(); it.mkdirs() }
        try {
            var count = 0
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    if (e.name.startsWith("meta/")) {
                        meta[e.name.removePrefix("meta/")] = readLimited(z, 2 * 1024 * 1024)
                        continue
                    }
                    val target = File(staging, e.name)
                    // chặn đường dẫn thoát khỏi thư mục (zip slip)
                    if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) continue
                    if (++count > 400) throw CustomException("Bản sao lưu có quá nhiều tệp")
                    target.parentFile?.mkdirs()
                    target.outputStream().use { o ->
                        val buf = ByteArray(16 * 1024)
                        var total = 0
                        while (true) {
                            val n = z.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > 20 * 1024 * 1024) throw CustomException("Một tệp trong bản sao lưu quá lớn")
                            o.write(buf, 0, n)
                        }
                    }
                }
            }
            if (count == 0 && meta.isEmpty()) return null
            root.deleteRecursively()
            if (!staging.renameTo(root)) {
                staging.copyRecursively(root, overwrite = true)
                staging.deleteRecursively()
            }
            boardsDir.mkdirs()
            piecesDir.mkdirs()
            return meta
        } catch (e: Exception) {
            staging.deleteRecursively()
            if (e is CustomException) throw e
            return null
        }
    }
}
