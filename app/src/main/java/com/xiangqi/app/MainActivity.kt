import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiangqi.app.data.Profile
import com.xiangqi.app.data.SavedGame
import com.xiangqi.app.ui.GameScreen
import com.xiangqi.app.ui.ProfileScreen
import com.xiangqi.app.ui.SettingsScreen
import com.xiangqi.app.ui.SettingsViewModel
import com.xiangqi.app.ui.theme.XiangqiTheme

private sealed interface Screen {
    object Profiles : Screen
    data class Game(val profile: Profile, val resume: SavedGame? = null) : Screen
    object Settings : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XiangqiTheme {
                val settingsVm: SettingsViewModel = viewModel()
                val geminiApiKey by settingsVm.geminiApiKey.collectAsState()
                var screen by remember { mutableStateOf<Screen>(Screen.Profiles) }

                when (val s = screen) {
                    Screen.Profiles -> ProfileScreen(
                        onPlay = { screen = Screen.Game(it) },
                        onResume = { saved ->
                            val profile = Profile(
                                name = saved.profileName,
                                skillLevel = saved.skillLevel,
                                timeControlMinutes = saved.timeControlMinutes,
                                playAsRed = saved.playerIsRed
                            )
                            screen = Screen.Game(profile, resume = saved)
                        }
                    )
                    is Screen.Game  -> GameScreen(
                        profile = s.profile,
                        resume = s.resume,
                        onBack = { screen = Screen.Profiles },
                        onSettings = { screen = Screen.Settings },
                        geminiApiKey = geminiApiKey
                    )
                    Screen.Settings -> SettingsScreen(
                        onBack = { screen = Screen.Profiles },
                        vm = settingsVm
                    )
                }
            }
        }
    }
}
