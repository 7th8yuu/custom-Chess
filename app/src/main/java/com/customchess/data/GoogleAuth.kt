package com.customchess.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import java.security.MessageDigest

/** [needsSetup]: lỗi do Google chưa nhận ra ứng dụng (thiếu OAuth client), nên hiện hướng dẫn cấu hình. */
class AuthException(message: String, val cancelled: Boolean = false, val needsSetup: Boolean = false) : Exception(message)

/**
 * Đăng nhập Google. Có hai đường:
 * - Credential Manager (cần Web client ID, build với secret GOOGLE_WEB_CLIENT_ID),
 * - Google Sign-In cổ điển, chỉ cần một OAuth client kiểu Android khớp tên gói và SHA-1 (không cần secret).
 */
object GoogleAuth {

    const val PACKAGE = "com.customchess.app"

    /** Intent mở cửa sổ chọn tài khoản của Google Sign-In cổ điển. */
    fun legacyIntent(context: Context): Intent {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestProfile().build()
        return GoogleSignIn.getClient(context, options).signInIntent
    }

    /** Đọc kết quả trả về của [legacyIntent]. */
    fun userFromLegacy(data: Intent?): User {
        try {
            val acc = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            val email = acc.email.orEmpty()
            val name = acc.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore('@').ifBlank { "Người chơi" }
            return User(UserKind.GOOGLE, name, email)
        } catch (e: ApiException) {
            when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> throw AuthException("Đã hủy đăng nhập", cancelled = true)
                CommonStatusCodes.DEVELOPER_ERROR -> throw AuthException(
                    "Google chưa nhận ra ứng dụng này (lỗi 10). Cần tạo OAuth client kiểu Android với đúng tên gói và SHA-1.",
                    needsSetup = true,
                )
                CommonStatusCodes.NETWORK_ERROR -> throw AuthException("Không có mạng để đăng nhập Google")
                else -> throw AuthException("Đăng nhập Google thất bại (mã ${e.statusCode})", needsSetup = e.statusCode == GoogleSignInStatusCodes.SIGN_IN_FAILED)
            }
        }
    }

    /** SHA-1 của chữ ký APK đang chạy, dạng AA:BB:..., để dán vào Google Cloud Console. */
    fun signingSha1(context: Context): String? = try {
        val pm = context.packageManager
        val sigs: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        sigs?.firstOrNull()?.let { s ->
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString(":") { "%02X".format(it) }
        }
    } catch (_: Exception) {
        null
    }

    suspend fun signIn(activity: Activity, webClientId: String): User {
        if (webClientId.isBlank()) {
            throw AuthException("Chưa cấu hình GOOGLE_WEB_CLIENT_ID (xem README, phần Đăng nhập Google)")
        }
        val manager = CredentialManager.create(activity)
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        try {
            val result = manager.getCredential(activity, request)
            val cred = result.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(cred.data)
                val name = google.displayName?.takeIf { it.isNotBlank() } ?: google.id.substringBefore('@')
                return User(UserKind.GOOGLE, name, google.id)
            }
            throw AuthException("Không nhận được thông tin tài khoản Google")
        } catch (e: GetCredentialCancellationException) {
            throw AuthException("Đã hủy đăng nhập", cancelled = true)
        } catch (e: NoCredentialException) {
            throw AuthException("Máy chưa có tài khoản Google nào. Hãy thêm tài khoản trong Cài đặt rồi thử lại.")
        } catch (e: GoogleIdTokenParsingException) {
            throw AuthException("Dữ liệu tài khoản Google không hợp lệ")
        } catch (e: GetCredentialException) {
            throw AuthException("Đăng nhập Google thất bại: ${e.message ?: e.type}", needsSetup = true)
        }
    }

    suspend fun signOut(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Không có gì để xóa cũng không sao.
        }
        try {
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(context, options).signOut()
        } catch (_: Exception) {
        }
    }
}
