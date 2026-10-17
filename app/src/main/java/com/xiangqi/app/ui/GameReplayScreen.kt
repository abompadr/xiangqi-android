package com.xiangqi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiangqi.app.engine.Puzzle
import com.xiangqi.app.engine.XiangqiBoard
import com.xiangqi.app.engine.XqSquare

private val BG   = Color(0xFF1A1A2E)
private val GOLD = Color(0xFFE8B84B)

@Composable
fun GameReplayScreen(
    game: Puzzle,
    onBack: () -> Unit,
    onNext: (() -> Unit)? = null,
) {
    // Board state driven by step index into game.solution
    var stepIndex by remember(game) { mutableIntStateOf(0) }
    val board = remember(game) { XiangqiBoard().also { it.loadFen(game.fen) } }
    val appliedUpto = remember(game) { mutableIntStateOf(-1) }

    // Advance or rewind board to match stepIndex
    LaunchedEffect(game, stepIndex) {
        val target = stepIndex
        val current = appliedUpto.value
        if (target > current) {
            for (i in (current + 1)..target) {
                board.applyUci(game.solution[i])
            }
        } else if (target < current) {
            // Rewind: reload from FEN and replay up to target
            board.loadFen(game.fen)
            for (i in 0 until target) {
                board.applyUci(game.solution[i])
            }
        }
        appliedUpto.value = target
    }

    val lastMove: Pair<XqSquare, XqSquare>? = if (stepIndex > 0) {
        val uci = game.solution[stepIndex - 1]
        XqSquare.fromAlg(uci.substring(0, 2)) to XqSquare.fromAlg(uci.substring(2, 4))
    } else null

    val totalMoves = game.solution.size
    val moveNum = stepIndex          // moves applied so far
    val sideLabel = if (moveNum % 2 == 0) "Red to move" else "Black to move"

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
            Text("Full Game", color = GOLD, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
        }

        // Title
        Text(
            text = game.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
        )
        Text(
            text = game.hint,
            color = Color(0xFF8888AA),
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, bottom = 4.dp),
        )

        // Move counter
        Text(
            text = if (stepIndex == totalMoves) "Game over · $totalMoves moves"
                   else "Move $moveNum / $totalMoves · $sideLabel",
            color = GOLD,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )

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
                board    = board,
                lastMove = lastMove,
            )
            XiangqiBoardViewPublic(
                state    = boardState,
                flipped  = false,
                modifier = Modifier.size(boardW + padH * 2, boardH + padV * 2),
                onSquare = {},
            )
        }

        // Navigation controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { stepIndex = 0 },
                enabled = stepIndex > 0,
            ) { Text("|◀ Start", color = if (stepIndex > 0) Color(0xFF8888AA) else Color(0xFF444466)) }

            TextButton(
                onClick = { if (stepIndex > 0) stepIndex-- },
                enabled = stepIndex > 0,
            ) { Text("◀ Prev", color = if (stepIndex > 0) Color(0xFF4A90D9) else Color(0xFF444466)) }

            TextButton(
                onClick = { if (stepIndex < totalMoves) stepIndex++ },
                enabled = stepIndex < totalMoves,
            ) { Text("Next ▶", color = if (stepIndex < totalMoves) GOLD else Color(0xFF444466)) }

            TextButton(
                onClick = { stepIndex = totalMoves },
                enabled = stepIndex < totalMoves,
            ) { Text("End ▶|", color = if (stepIndex < totalMoves) Color(0xFF8888AA) else Color(0xFF444466)) }
        }

        // Next game button
        if (onNext != null) {
            TextButton(onClick = onNext) {
                Text("Next game →", color = GOLD, fontSize = 14.sp)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
