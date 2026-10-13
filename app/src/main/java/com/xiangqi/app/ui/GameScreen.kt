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
fun GameScreen(
    profile: Profile,
    onBack: () -> Unit,
    vm: GameViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    var showRules by remember { mutableStateOf(false) }

    LaunchedEffect(profile) { vm.startGame(profile) }

    Scaffold(
        containerColor = BG,
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .padding(innerPadding)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar — row 1: navigation + profile name
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Color(0xFF8888AA)) }
            Spacer(Modifier.weight(1f))
            Text("象棋  Xiangqi", color = Color(0xFFE8B84B), fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
        }
        // Top bar — row 2: actions (hidden during replay)
        if (!state.replayMode) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { vm.takeBack() }) { Text("↩ Take Back", color = Color(0xFF4A90D9)) }
            TextButton(onClick = { vm.resign() }) { Text("Resign", color = Color(0xFFE74C3C)) }
            TextButton(onClick = { showRules = true }) { Text("? Rules", color = Color(0xFF8888AA)) }
        }
        }

        // Opening name label
        val openingName = state.openingName
        if (openingName != null) {
            Text(
                text = openingName,
                color = Color(0xFFE8B84B),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp)
            )
        }

        // Game-over banner (shown above the board, board stays visible)
        if (state.status != GameStatus.PLAYING) {
            val msg = when (state.status) {
                GameStatus.RED_WIN      -> "Red wins!"
                GameStatus.BLACK_WIN    -> "Black wins!"
                GameStatus.ENGINE_ERROR -> "Engine error"
                else                    -> "Draw"
            }
            val bannerColor = when (state.status) {
                GameStatus.RED_WIN   -> Color(0xFF6B1A1A)
                GameStatus.BLACK_WIN -> Color(0xFF1A1A1A)
                else                 -> Color(0xFF2A3A2A)
            }
            GameOverBanner(
                message = msg,
                bannerColor = bannerColor,
                replayMode = state.replayMode,
                onReplay = { vm.startReplay() },
                onPlayAgain = { vm.playAgain() },
                onBack = onBack
            )
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
            // Board canvas: 9 vertical lines × 10 horizontal lines.
            // Add half-a-spacing padding on every side so edge pieces are never clipped.
            // The playable grid spans 8 horizontal gaps and 9 vertical gaps.
            // With padding p_h = boardW/16 left+right and p_v = boardH/18 top+bottom,
            // the canvas is boardW × boardH but the lines start half a gap from each edge.
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
            val padH = boardW / 16   // half of one horizontal spacing (boardW/8 / 2)
            val padV = boardH / 18   // half of one vertical spacing (boardH/9 / 2)
            // Expand the canvas by one full spacing in each dimension so the wooden
            // board extends beyond the outermost grid lines, while the grid itself
            // stays the same size. The view is bigger; pieces/lines are unchanged.
            XiangqiBoardView(
                state = state,
                modifier = Modifier.size(boardW + padH * 2, boardH + padV * 2),
                onSquare = { vm.onSquareTapped(it) }
            )
        }

        // Red timer (bottom — player side)
        TimerBar(state.redTimeMs, state.board.redToMove && state.status == GameStatus.PLAYING, "Red ▼")

        // Replay nav row (replaces the bottom spacer when in replay mode)
        if (state.replayMode) {
            ReplayNavRow(
                index = state.replayIndex,
                total = state.completedMoveHistory.size,
                onPrev = { vm.replayPrev() },
                onNext = { vm.replayNext() }
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }

    // Rules dialog
    if (showRules) {
        RulesDialog(onDismiss = { showRules = false })
    }

    } // end Scaffold
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
        // The canvas is larger than the grid by one full spacing on each axis.
        // Grid spacing is derived from the inner playable area (canvas minus margins).
        val spacingW = maxWidth  / 10  // canvas = 8 gaps + 2 half-margins → 10 units wide
        val spacingH = maxHeight / 11  // canvas = 9 gaps + 2 half-margins → 11 units tall
        val marginX  = spacingW          // half-spacing margin on left and right
        val marginY  = spacingH          // half-spacing margin on top and bottom

        // Draw board background, river, grid lines, and palace diagonals
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sW = size.width  / 10f
            val sH = size.height / 11f
            val mX = sW   // left/right margin
            val mY = sH   // top/bottom margin

            // Wooden board background — full canvas including margins
            drawRect(color = BOARD_BG)

            // River band between horizontal lines 4 and 5
            drawRect(
                color = RIVER_COLOR,
                topLeft = Offset(0f, mY + 4 * sH),
                size = androidx.compose.ui.geometry.Size(size.width, sH)
            )

            // Vertical lines (interrupted at river for inner files)
            for (f in 0..8) {
                val x = mX + f * sW
                drawLine(LINE_COLOR, Offset(x, mY),            Offset(x, mY + 4 * sH), strokeWidth = 1.5f)
                drawLine(LINE_COLOR, Offset(x, mY + 5 * sH),   Offset(x, mY + 9 * sH), strokeWidth = 1.5f)
                if (f == 0 || f == 8) {
                    drawLine(LINE_COLOR, Offset(x, mY + 4 * sH), Offset(x, mY + 5 * sH), strokeWidth = 1.5f)
                }
            }

            // Horizontal lines
            for (r in 0..9) {
                val y = mY + r * sH
                drawLine(LINE_COLOR, Offset(mX, y), Offset(mX + 8 * sW, y), strokeWidth = 1.5f)
            }

            // Palace diagonals — Black (rows 0-2, files 3-5)
            drawLine(LINE_COLOR, Offset(mX + 3*sW, mY),        Offset(mX + 5*sW, mY + 2*sH), strokeWidth = 1.5f)
            drawLine(LINE_COLOR, Offset(mX + 5*sW, mY),        Offset(mX + 3*sW, mY + 2*sH), strokeWidth = 1.5f)

            // Palace diagonals — Red (rows 7-9, files 3-5)
            drawLine(LINE_COLOR, Offset(mX + 3*sW, mY + 7*sH), Offset(mX + 5*sW, mY + 9*sH), strokeWidth = 1.5f)
            drawLine(LINE_COLOR, Offset(mX + 5*sW, mY + 7*sH), Offset(mX + 3*sW, mY + 9*sH), strokeWidth = 1.5f)
        }

        // River labels — centred in the river band
        Box(
            modifier = Modifier
                .offset(x = marginX, y = marginY + spacingH * 4)
                .width(spacingW * 4f)
                .height(spacingH),
            contentAlignment = Alignment.Center
        ) {
            Text("楚  河", color = LINE_COLOR, fontSize = (spacingW.value * 0.5f).sp,
                fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .offset(x = marginX + spacingW * 4.5f, y = marginY + spacingH * 4)
                .width(spacingW * 3.5f)
                .height(spacingH),
            contentAlignment = Alignment.Center
        ) {
            Text("漢  界", color = LINE_COLOR, fontSize = (spacingW.value * 0.5f).sp,
                fontWeight = FontWeight.Bold)
        }

        // Pieces, highlights, and tap areas — centred on intersections
        // Display: rank 9 (black back rank) at top (r=0), rank 0 (red) at bottom (r=9)
        for (r in 0..9) {
            for (f in 0..8) {
                val displayRank = 9 - r
                val sq = XqSquare(f, displayRank)
                val isSelected = state.selected == sq
                val isLastMove = state.lastMove?.let { it.first == sq || it.second == sq } == true
                val piece = state.board.get(sq)

                // Intersection pixel centre (offset by margin)
                val cx = marginX + spacingW * f
                val cy = marginY + spacingH * r

                // Highlight: square centred on the intersection
                if (isSelected || isLastMove) {
                    val hlSize = minOf(spacingW.value, spacingH.value).dp
                    Box(
                        modifier = Modifier
                            .offset(x = cx - hlSize / 2, y = cy - hlSize / 2)
                            .size(width = hlSize, height = hlSize)
                            .background(if (isSelected) SELECT_COL else LAST_COL)
                    )
                }

                // Tap area: half-spacing pad around each intersection
                Box(
                    modifier = Modifier
                        .offset(x = cx - spacingW / 2, y = cy - spacingH / 2)
                        .size(spacingW, spacingH)
                        .clickable { onSquare(sq) }
                )

                // Piece — centred on the intersection
                if (piece != Piece.EMPTY) {
                    val isRed = piece > 0
                    val absPiece = kotlin.math.abs(piece)
                    val label = if (isRed) RED_LABELS[absPiece] ?: "?" else BLACK_LABELS[absPiece] ?: "?"
                    val pieceSize = min(spacingW.value, spacingH.value) * 0.82f

                    Box(
                        modifier = Modifier
                            .offset(
                                x = cx - (pieceSize / 2).dp,
                                y = cy - (pieceSize / 2).dp
                            )
                            .size(pieceSize.dp)
                            .clip(CircleShape)
                            .background(PIECE_BG)
                            .clickable { onSquare(sq) },
                        contentAlignment = Alignment.Center
                    ) {
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

// ── Game-over banner ──────────────────────────────────────────────────────────
@Composable
private fun GameOverBanner(
    message: String,
    bannerColor: Color,
    replayMode: Boolean,
    onReplay: () -> Unit,
    onPlayAgain: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .background(bannerColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.weight(1f))
        if (!replayMode) {
            TextButton(onClick = onReplay) { Text("↺ Replay", color = Color(0xFFE8B84B), fontSize = 13.sp) }
        }
        TextButton(onClick = onPlayAgain) { Text("Play Again", color = Color(0xFF4CAF50), fontSize = 13.sp) }
        TextButton(onClick = onBack) { Text("← Back", color = Color(0xFF8888AA), fontSize = 13.sp) }
    }
}

// ── Replay navigation row ─────────────────────────────────────────────────────
@Composable
private fun ReplayNavRow(index: Int, total: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPrev, enabled = index > 0) {
            Text("‹ Prev", color = if (index > 0) Color(0xFF4A90D9) else Color(0xFF444466))
        }
        Text("Move $index / $total", color = Color.White, fontSize = 14.sp)
        TextButton(onClick = onNext, enabled = index < total) {
            Text("Next ›", color = if (index < total) Color(0xFF4A90D9) else Color(0xFF444466))
        }
    }
}
