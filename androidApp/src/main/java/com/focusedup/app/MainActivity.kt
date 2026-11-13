package com.focusedup.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.focusedup.app.focus.Graph
import com.focusedup.app.focus.Notifications
import com.focusedup.shared.ui.FocusedUpApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        Notifications.ensureChannels(this)
        val engine = Graph.engine(this)
        if (savedInstanceState == null) handleLink(intent)
        setContent { FocusedUpApp(engine) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    /** Opened from a shared profile link (focusedup://profile#p=...). */
    private fun handleLink(intent: Intent?) {
        val link = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        Graph.engine(this).receiveLink(link)
    }

    override fun onResume() {
        super.onResume()
        Graph.engine(this).tick()
    }
}
