package com.xiangqi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.xiangqi.app.data.Profile
import com.xiangqi.app.ui.GameScreen
import com.xiangqi.app.ui.GameSetupScreen
import com.xiangqi.app.ui.theme.XiangqiTheme

private sealed interface Screen {
    object Setup : Screen
    data class Game(val profile: Profile) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XiangqiTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Setup) }

                when (val s = screen) {
                    Screen.Setup   -> GameSetupScreen(
                        onPlay = { screen = Screen.Game(it) }
                    )
                    is Screen.Game -> GameScreen(
                        profile = s.profile,
                        onBack  = { screen = Screen.Setup },
                    )
                }
            }
        }
    }
}
