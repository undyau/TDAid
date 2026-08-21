package com.undy.tdaid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.undy.tdaid.ui.nav.TDAidNavHost
import com.undy.tdaid.ui.theme.TDAidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Deliberately NOT edge-to-edge: screens position their own icon buttons right at the
        // top of the content, and drawing under the status bar made those unreachable.
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
