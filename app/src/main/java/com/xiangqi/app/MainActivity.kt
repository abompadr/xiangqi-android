package com.xiangqi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.xiangqi.app.data.Profile
import com.xiangqi.app.engine.Puzzle
import com.xiangqi.app.engine.PuzzleBook
import com.xiangqi.app.ui.GameListScreen
import com.xiangqi.app.ui.GameReplayScreen
import com.xiangqi.app.ui.GameScreen
import com.xiangqi.app.ui.GameSetupScreen
import com.xiangqi.app.ui.PuzzleListScreen
import com.xiangqi.app.ui.PuzzleScreen
import com.xiangqi.app.ui.theme.XiangqiTheme

private sealed interface Screen {
    object Setup : Screen
    data class Game(val profile: Profile) : Screen
    object PuzzleList : Screen
    data class Puzzle(val puzzle: com.xiangqi.app.engine.Puzzle, val index: Int) : Screen
    object GameList : Screen
    data class GameReplay(val game: com.xiangqi.app.engine.Puzzle, val index: Int) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PuzzleBook.load(this)
        enableEdgeToEdge()
        setContent {
            XiangqiTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Setup) }

                when (val s = screen) {
                    Screen.Setup -> GameSetupScreen(
                        onPlay    = { screen = Screen.Game(it) },
                        onPuzzles = { screen = Screen.PuzzleList },
                        onGames   = { screen = Screen.GameList },
                    )
                    is Screen.Game -> GameScreen(
                        profile = s.profile,
                        onBack  = { screen = Screen.Setup },
                    )
                    Screen.PuzzleList -> PuzzleListScreen(
                        onPuzzle = { puzzle ->
                            val idx = PuzzleBook.puzzles.indexOf(puzzle)
                            screen = Screen.Puzzle(puzzle, idx)
                        },
                        onBack = { screen = Screen.Setup },
                    )
                    is Screen.Puzzle -> {
                        val puzzles = PuzzleBook.puzzles
                        val nextIndex = s.index + 1
                        PuzzleScreen(
                            puzzle = s.puzzle,
                            onBack = { screen = Screen.PuzzleList },
                            onNext = if (nextIndex < puzzles.size) {
                                { screen = Screen.Puzzle(puzzles[nextIndex], nextIndex) }
                            } else null,
                        )
                    }
                    Screen.GameList -> GameListScreen(
                        onGame = { game ->
                            val idx = PuzzleBook.games.indexOf(game)
                            screen = Screen.GameReplay(game, idx)
                        },
                        onBack = { screen = Screen.Setup },
                    )
                    is Screen.GameReplay -> {
                        val games = PuzzleBook.games
                        val nextIndex = s.index + 1
                        GameReplayScreen(
                            game   = s.game,
                            onBack = { screen = Screen.GameList },
                            onNext = if (nextIndex < games.size) {
                                { screen = Screen.GameReplay(games[nextIndex], nextIndex) }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}
