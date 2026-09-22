package com.xiangqi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.xiangqi.app.data.Profile
import com.xiangqi.app.ui.GameScreen
import com.xiangqi.app.ui.ProfileScreen
import com.xiangqi.app.ui.theme.XiangqiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XiangqiTheme {
                var activeProfile by remember { mutableStateOf<Profile?>(null) }
                if (activeProfile == null) {
                    ProfileScreen(onPlay = { activeProfile = it })
                } else {
                    GameScreen(profile = activeProfile!!, onBack = { activeProfile = null })
                }
            }
        }
    }
}
