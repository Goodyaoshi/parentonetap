package com.goodyaoshi.parentonetap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.goodyaoshi.parentonetap.core.ui.ParentOneTapTheme
import com.goodyaoshi.parentonetap.core.ui.ParentOneTapNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35 强制 edge-to-edge，各页面用 Scaffold/WindowInsets 自行避让
        enableEdgeToEdge()
        setContent {
            ParentOneTapTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ParentOneTapNavHost()
                }
            }
        }
    }
}
