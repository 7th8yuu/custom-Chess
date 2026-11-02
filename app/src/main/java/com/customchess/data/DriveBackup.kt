package com.customchess.data

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class DriveAuth {
    class Token(val value: String) : DriveAuth()
    class NeedsConsent(val pending: PendingIntent) : DriveAuth()
}

class DriveException(message: String, val code: Int = 0) : Exception(message)

/**
 * Sao lưu lên thư mục ẩn của ứng dụng trên Google Drive (appDataFolder): người dùng không thấy tệp trong Drive của mình,
 * chỉ ứng dụng này đọc được. Chỉ xin quyền drive.appdata nên không chạm vào tệp khác của người dùng.
 */
object DriveBackup {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    const val NAME = "customchess-backup.zip"

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
    }

    /** Xin quyền truy cập Drive. Lần đầu cần người dùng bấm đồng ý ([DriveAuth.NeedsConsent]), các lần sau lấy thẳng token. */
    suspend fun authorize(context: Context, email: String?): DriveAuth = suspendCancellableCoroutine { cont ->
        val builder = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE)))
        if (!email.isNullOrBlank()) builder.setAccount(Account(email, "com.google"))
        Identity.getAuthorizationClient(context.applicationContext).authorize(builder.build())
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) cont.resume(DriveAuth.NeedsConsent(pi))
                else {
                    val t = r.accessToken
                    if (t.isNullOrBlank()) cont.resumeWithException(DriveException("Google không cấp quyền truy cập Drive"))
                    else cont.resume(DriveAuth.Token(t))
                }
            }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    /** Lấy token từ kết quả của màn hình đồng ý. */
    fun tokenFromIntent(context: Context, data: Intent): String {
        val r = Identity.getAuthorizationClient(context.applicationContext).getAuthorizationResultFromIntent(data)
        return r.accessToken ?: throw DriveException("Google không cấp quyền truy cập Drive")
    }

    private fun call(req: Request): String {
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
                val hint = when (r.code) {
                    401 -> "Phiên Google đã hết hạn, thử lại."
                    403 -> "Google từ chối. Hãy bật Google Drive API và thêm phạm vi drive.appdata cho ứng dụng trong Google Cloud. $msg"
                    else -> msg
                }
                throw DriveException("Drive lỗi ${r.code}: $hint".trim(), r.code)
            }
            return body
        }
    }

    /** Mã tệp sao lưu hiện có, hoặc null nếu chưa có. */
    fun findId(token: String): String? {
        val q = URLEncoder.encode("name='$NAME'", "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id,modifiedTime)&pageSize=5"
        val body = call(Request.Builder().url(url).header("Authorization", "Bearer $token").build())
        val a: JSONArray = JSONObject(body).optJSONArray("files") ?: return null
        return if (a.length() == 0) null else a.getJSONObject(0).optString("id").ifBlank { null }
    }

    /** Tải lên (tạo mới hoặc ghi đè). Trả về mã tệp. */
    fun upload(token: String, bytes: ByteArray, existingId: String?): String {
        val meta = JSONObject().put("name", NAME)
        if (existingId == null) meta.put("parents", JSONArray().put("appDataFolder"))
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(meta.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(bytes.toRequestBody("application/zip".toMediaType()))
            .build()
        val url = if (existingId == null) "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id"
        else "https://www.googleapis.com/upload/drive/v3/files/$existingId?uploadType=multipart&fields=id"
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .method(if (existingId == null) "POST" else "PATCH", body).build()
        return JSONObject(call(req)).optString("id").ifBlank { existingId ?: "" }
    }

    fun download(token: String, id: String): ByteArray {
        val req = Request.Builder().url("https://www.googleapis.com/drive/v3/files/$id?alt=media").header("Authorization", "Bearer $token").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw DriveException("Không tải được bản sao lưu (lỗi ${r.code})", r.code)
            return r.body?.bytes() ?: throw DriveException("Bản sao lưu rỗng")
        }
    }
}
