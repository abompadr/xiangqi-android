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

private sealed interface Screen {
    object Profiles : Screen
    data class Game(val profile: Profile) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XiangqiTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Profiles) }

                when (val s = screen) {
                    Screen.Profiles -> ProfileScreen(
                        onPlay = { screen = Screen.Game(it) },
                    )
                    is Screen.Game  -> GameScreen(
                        profile = s.profile,
                        onBack = { screen = Screen.Profiles },
                    )
                }
            }
        }
    }
}
