package com.customchess

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.customchess.ui.AppRoot
import com.customchess.vm.AppViewModel

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleIntent(intent)
        setContent { AppRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Trình duyệt chuyển hướng về customchess://oauth sau khi người dùng cấp quyền Lichess. */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "customchess") vm.handleOAuthRedirect(data)
    }
}
