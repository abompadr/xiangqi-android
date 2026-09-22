package com.xiangqi.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiangqi.app.data.Profile
import com.xiangqi.app.engine.Piece
import com.xiangqi.app.engine.XiangqiBoard
import com.xiangqi.app.engine.XqSquare
import java.util.Locale
import kotlin.math.min

// ── Colours ──────────────────────────────────────────────────────────────────
private val BG          = Color(0xFF1A1A2E)
private val BOARD_BG    = Color(0xFFDEB887)   // tan / burlywood
private val LINE_COLOR  = Color(0xFF5C3A1E)   // dark brown
private val RIVER_COLOR = Color(0xFF9BBE8B)   // muted green for river area
private val RED_PIECE   = Color(0xFFCC2222)
private val BLACK_PIECE = Color(0xFF111111)
private val PIECE_BG    = Color(0xFFF5DEB3)   // wheat
private val SELECT_COL  = Color(0x9900CC44)
private val LAST_COL    = Color(0x88CCCC00)

// ── Piece labels ─────────────────────────────────────────────────────────────
// Red pieces use traditional characters; Black uses variant characters
private val RED_LABELS = mapOf(
    Piece.SOLDIER  to "兵", Piece.HORSE    to "馬", Piece.ELEPHANT to "相",
    Piece.ADVISOR  to "仕", Piece.CHARIOT  to "車", Piece.CANNON   to "炮",
    Piece.GENERAL  to "帥"
)
private val BLACK_LABELS = mapOf(
    Piece.SOLDIER  to "卒", Piece.HORSE    to "馬", Piece.ELEPHANT to "象",
    Piece.ADVISOR  to "士", Piece.CHARIOT  to "車", Piece.CANNON   to "砲",
    Piece.GENERAL  to "將"
)

@Composable
fun GameScreen(profile: Profile, onBack: () -> Unit, vm: GameViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    LaunchedEffect(profile) { vm.startGame(profile) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Color(0xFF8888AA)) }
            Spacer(Modifier.weight(1f))
            Text(profile.name, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.resign() }) { Text("Resign", color = Color(0xFFE74C3C)) }
        }

        // Black timer (top — engine side)
        TimerBar(state.blackTimeMs, !state.board.redToMove && state.status == GameStatus.PLAYING, "Black ▲")

        // Board
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            // Board is 9 cols × 10 rows; keep aspect ratio
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
            XiangqiBoardView(
                state = state,
                modifier = Modifier.size(boardW, boardH),
                onSquare = { vm.onSquareTapped(it) }
            )
        }

        // Red timer (bottom — player side)
        TimerBar(state.redTimeMs, state.board.redToMove && state.status == GameStatus.PLAYING, "Red ▼")

        Spacer(Modifier.height(16.dp))
    }

    // Game-over overlay
    if (state.status != GameStatus.PLAYING) {
        val msg = when (state.status) {
            GameStatus.RED_WIN     -> "Red wins! 🎉"
            GameStatus.BLACK_WIN   -> "Black wins!"
            GameStatus.ENGINE_ERROR -> "Engine error:\n${state.errorMessage}"
            else                   -> "Draw"
        }
        GameOverOverlay(msg, onBack)
    }
}

// ── Timer bar ─────────────────────────────────────────────────────────────────
@Composable
private fun TimerBar(timeMs: Long, active: Boolean, label: String) {
    val unlimited = timeMs > 30 * 60_000L * 10
    val display = if (unlimited) "∞" else formatTime(timeMs)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(
                if (active) Color(0xFF2A4A2A) else Color(0xFF2A2A4A),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Text(display,
            color = if (active) Color(0xFF4CAF50) else Color.White,
            fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60; val s = total % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

// ── Board view ────────────────────────────────────────────────────────────────
@Composable
private fun XiangqiBoardView(
    state: GameState,
    modifier: Modifier,
    onSquare: (XqSquare) -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val cellW = maxWidth  / 9
        val cellH = maxHeight / 10

        // Draw board lines and river via Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cW = size.width / 9
            val cH = size.height / 10

            // Board background
            drawRect(color = BOARD_BG)

            // River (ranks 4-5 gap)
            drawRect(
                color = RIVER_COLOR,
                topLeft = Offset(0f, 4 * cH),
                size = androidx.compose.ui.geometry.Size(size.width, 2 * cH)
            )

            val stroke = Stroke(width = 1.5f)

            // Vertical lines
            for (f in 0..8) {
                val x = f * cW
                // Black side: full column
                drawLine(LINE_COLOR, Offset(x, 0f), Offset(x, 4 * cH), strokeWidth = 1.5f)
                // Red side: full column
                drawLine(LINE_COLOR, Offset(x, 6 * cH), Offset(x, 10 * cH), strokeWidth = 1.5f)
                // River: only edge columns continue through
                if (f == 0 || f == 8) {
                    drawLine(LINE_COLOR, Offset(x, 4 * cH), Offset(x, 6 * cH), strokeWidth = 1.5f)
                }
            }

            // Horizontal lines
            for (r in 0..9) {
                val y = r * cH
                drawLine(LINE_COLOR, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
            }

            // Palace diagonals — Black (ranks 7-9, files d-f = 3-5)
            drawLine(LINE_COLOR, Offset(3*cW, 7*cH), Offset(5*cW, 9*cH), strokeWidth = 1.5f)
            drawLine(LINE_COLOR, Offset(5*cW, 7*cH), Offset(3*cW, 9*cH), strokeWidth = 1.5f)

            // Palace diagonals — Red (ranks 0-2, files d-f = 3-5)
            drawLine(LINE_COLOR, Offset(3*cW, 0*cH), Offset(5*cW, 2*cH), strokeWidth = 1.5f)
            drawLine(LINE_COLOR, Offset(5*cW, 0*cH), Offset(3*cW, 2*cH), strokeWidth = 1.5f)
        }

        // River labels
        Box(
            modifier = Modifier
                .offset(x = 0.dp, y = cellH * 4)
                .width(cellW * 4.5f)
                .height(cellH * 2)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("楚  河", color = LINE_COLOR, fontSize = (cellW.value * 0.5f).sp,
                fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .offset(x = cellW * 4.5f, y = cellH * 4)
                .width(cellW * 4.5f)
                .height(cellH * 2)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("漢  界", color = LINE_COLOR, fontSize = (cellW.value * 0.5f).sp,
                fontWeight = FontWeight.Bold)
        }

        // Squares with highlight and piece tap areas
        // Board is displayed rank 9 (black back rank) at top, rank 0 (red back rank) at bottom
        for (r in 0..9) {
            for (f in 0..8) {
                val displayRank = 9 - r   // rank 9 at top row, rank 0 at bottom
                val sq = XqSquare(f, displayRank)
                val isSelected = state.selected == sq
                val isLastMove = state.lastMove?.let { it.first == sq || it.second == sq } == true
                val piece = state.board.get(sq)

                // Highlight box
                if (isSelected || isLastMove) {
                    Box(
                        modifier = Modifier
                            .offset(x = cellW * f, y = cellH * r)
                            .size(cellW, cellH)
                            .background(if (isSelected) SELECT_COL else LAST_COL)
                    )
                }

                // Tap area (full cell)
                Box(
                    modifier = Modifier
                        .offset(x = cellW * f, y = cellH * r)
                        .size(cellW, cellH)
                        .clickable { onSquare(sq) }
                )

                // Piece
                if (piece != Piece.EMPTY) {
                    val isRed = piece > 0
                    val absPiece = kotlin.math.abs(piece)
                    val label = if (isRed) RED_LABELS[absPiece] ?: "?" else BLACK_LABELS[absPiece] ?: "?"
                    val pieceSize = min(cellW.value, cellH.value) * 0.82f

                    Box(
                        modifier = Modifier
                            .offset(
                                x = cellW * f + cellW / 2 - (pieceSize / 2).dp,
                                y = cellH * r + cellH / 2 - (pieceSize / 2).dp
                            )
                            .size(pieceSize.dp)
                            .clip(CircleShape)
                            .background(PIECE_BG)
                            .clickable { onSquare(sq) },
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer ring
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val ringColor = if (isRed) RED_PIECE else BLACK_PIECE
                            drawCircle(color = ringColor, style = Stroke(width = size.width * 0.07f))
                        }
                        Text(
                            text = label,
                            color = if (isRed) RED_PIECE else BLACK_PIECE,
                            fontSize = (pieceSize * 0.52f).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = (pieceSize * 0.52f).sp
                        )
                    }
                }
            }
        }
    }
}

// ── Game-over overlay ─────────────────────────────────────────────────────────
@Composable
private fun GameOverOverlay(message: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = Color.White, fontSize = 30.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onBack) { Text("Back to Profiles") }
        }
    }
}
