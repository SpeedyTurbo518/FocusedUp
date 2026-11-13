package com.focusedup.app.focus

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusedup.shared.ui.FColors
import kotlinx.coroutines.delay

private const val NOTION_HOST = "https://app.notion.com"

/**
 * Notion's real sign-in page inside the app. The user signs in exactly as on notion.so;
 * once Notion sets its session cookie we hand it back and close.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NotionLoginDialog(onResult: (String?) -> Unit) {
    val done by rememberUpdatedState(onResult)
    var finished by remember { mutableStateOf(false) }

    // Notion is a single-page app, so watch for the session cookie instead of page loads.
    LaunchedEffect(Unit) {
        while (!finished) {
            delay(1000)
            val cookie = CookieManager.getInstance().getCookie(NOTION_HOST)
            if (cookie != null && "token_v2=" in cookie) {
                finished = true
                CookieManager.getInstance().flush()
                done(cookie)
            }
        }
    }

    Dialog(onDismissRequest = { done(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(FColors.Black).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sign in to Notion", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tap \"Email\" and enter your Gmail. Notion emails you a code. The Google button doesn't work inside apps.",
                        color = FColors.TextSecondary, fontSize = 13.sp,
                    )
                }
                IconButton(onClick = { done(null) }) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
                factory = { ctx ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        loadUrl("$NOTION_HOST/login")
                    }
                },
            )
        }
    }
}

fun clearNotionCookies() {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
}
