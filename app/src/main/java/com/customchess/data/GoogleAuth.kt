package com.customchess.data

import android.app.Activity
import android.content.Context
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

class AuthException(message: String, val cancelled: Boolean = false) : Exception(message)

/** Đăng nhập Google bằng Credential Manager (cửa sổ chọn tài khoản của hệ thống). */
object GoogleAuth {

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
            throw AuthException("Đăng nhập Google thất bại: ${e.message ?: e.type}")
        }
    }

    suspend fun signOut(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Không có gì để xóa cũng không sao.
        }
    }
}
