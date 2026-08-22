package com.undy.tdaid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.undy.tdaid.ui.nav.TDAidNavHost
import com.undy.tdaid.ui.theme.TDAidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android enforces edge-to-edge for apps targeting API 35+ regardless of this call —
        // it can't be opted out of. Each screen consumes WindowInsets.systemBars itself so
        // content clears the status bar and gesture nav bar; this just gets proper status-bar
        // icon contrast.
        enableEdgeToEdge()
        setContent {
            TDAidApp()
        }
    }
}

@Composable
fun TDAidApp() {
    TDAidTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            TDAidNavHost()
        }
    }
}
