package com.xiangqi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiangqi.app.engine.Puzzle
import com.xiangqi.app.engine.PuzzleBook
import com.xiangqi.app.engine.XqSquare

private val BG   = Color(0xFF1A1A2E)
private val GOLD = Color(0xFFE8B84B)

@Composable
fun PuzzleScreen(
    puzzle: Puzzle,
    onBack: () -> Unit,
    onNext: (() -> Unit)? = null,
    vm: PuzzleViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(puzzle) { vm.loadPuzzle(puzzle) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Color(0xFF8888AA)) }
            Spacer(Modifier.weight(1f))
            Text("棋题  Puzzle", color = GOLD, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
        }

        // Puzzle title + difficulty badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = puzzle.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .background(difficultyColor(puzzle.category), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    PuzzleBook.categoryLabel(puzzle.category),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Status / hint banner (fixed height so board doesn't jump)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (state.status) {
                PuzzleStatus.CORRECT -> StatusBanner("Correct! 正确！", Color(0xFF2A5A2A))
                PuzzleStatus.WRONG   -> StatusBanner("Wrong move. 走错了。", Color(0xFF5A1A1A))
                PuzzleStatus.PLAYING ->
                    if (state.showHint) {
                        Text(
                            text = puzzle.hint,
                            color = GOLD,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    } else {
                        Text("Red to move — find the checkmate", color = Color(0xFF8888AA), fontSize = 12.sp)
                    }
            }
        }

        // Board
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            val boardAspect = 9f / 10f
            val availW = maxWidth.value
            val availH = maxHeight.value
            val boardW: Dp
            val boardH: Dp
            if (availW / availH < boardAspect) {
                boardW = maxWidth
                boardH = (availW / boardAspect).dp
            } else {
                boardH = maxHeight
                boardW = (availH * boardAspect).dp
            }
            val padH = boardW / 16
            val padV = boardH / 18

            val boardState = GameState(
                board            = state.board,
                selected         = state.selected,
                lastMove         = state.lastMove,
                useWesternPieces = state.useWesternPieces,
            )
            PuzzleBoardView(
                state    = boardState,
                modifier = Modifier.size(boardW + padH * 2, boardH + padV * 2),
                onSquare = { sq: XqSquare ->
                    if (state.status == PuzzleStatus.PLAYING) vm.onSquareTapped(sq)
                },
            )
        }

        // Action row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.toggleHint() }) {
                Text(
                    if (state.showHint) "Hide Hint" else "? Hint",
                    color = Color(0xFF8888AA),
                )
            }
            TextButton(onClick = { vm.retry() }) {
                Text("↺ Retry", color = Color(0xFF4A90D9))
            }
            if (onNext != null) {
                TextButton(onClick = onNext) {
                    Text("Next →", color = GOLD)
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(text: String, bgColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

private fun difficultyColor(cat: Int) = when (cat) {
    1    -> Color(0xFF4CAF50)
    2    -> Color(0xFF4A90D9)
    3    -> Color(0xFFE8A020)
    else -> Color(0xFFCC2222)
}

// Thin wrapper so PuzzleScreen can reuse the board drawing without coupling to GameViewModel
@Composable
private fun PuzzleBoardView(
    state: GameState,
    modifier: Modifier,
    onSquare: (XqSquare) -> Unit,
) {
    // Puzzles are always from Red's perspective (Red at bottom, Black at top)
    XiangqiBoardViewPublic(state = state, flipped = false, modifier = modifier, onSquare = onSquare)
}
