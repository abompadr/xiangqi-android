package com.xiangqi.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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

// Western labels: K=King/General, R=Rook/Chariot, N=Knight/Horse,
// B=Bishop/Elephant, A=Advisor, C=Cannon, P=Pawn/Soldier
private val RED_LABELS_WESTERN = mapOf(
    Piece.SOLDIER  to "P", Piece.HORSE    to "N", Piece.ELEPHANT to "B",
    Piece.ADVISOR  to "A", Piece.CHARIOT  to "R", Piece.CANNON   to "C",
    Piece.GENERAL  to "K"
)
private val BLACK_LABELS_WESTERN = mapOf(
    Piece.SOLDIER  to "p", Piece.HORSE    to "n", Piece.ELEPHANT to "b",
    Piece.ADVISOR  to "a", Piece.CHARIOT  to "r", Piece.CANNON   to "c",
    Piece.GENERAL  to "k"
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

        // Fixed-height slot for opening name + game-over banner.
        // Always reserves space so the board never resizes when messages appear/disappear.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            contentAlignment = Alignment.Center
        ) {
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
            } else {
                val openingName = state.openingName
                if (openingName != null) {
                    Text(
                        text = openingName,
                        color = Color(0xFFE8B84B),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // When the player is Black the board is flipped: Black pieces at bottom, Red at top.
        val flipped = !state.playerIsRed

        // Timer for the side at the top of the board (opponent when not flipped, player when flipped)
        val topIsRed = flipped        // when flipped, Red is at top
        val topTimeMs = if (topIsRed) state.redTimeMs else state.blackTimeMs
        val topActive = if (topIsRed) state.board.redToMove else !state.board.redToMove
        val topLabel  = if (topIsRed) "Red ▲" else "Black ▲"
        TimerBar(topTimeMs, topActive && state.status == GameStatus.PLAYING, topLabel)

        // Board
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(4.dp),
            contentAlignment = Alignment.Center
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
            XiangqiBoardViewPublic(
                state = state,
                flipped = flipped,
                modifier = Modifier.size(boardW + padH * 2, boardH + padV * 2),
                onSquare = { vm.onSquareTapped(it) },
                onAnimationDone = { vm.onAnimationDone() }
            )
        }

        // Timer for the side at the bottom of the board
        val botIsRed = !flipped
        val botTimeMs = if (botIsRed) state.redTimeMs else state.blackTimeMs
        val botActive = if (botIsRed) state.board.redToMove else !state.board.redToMove
        val botLabel  = if (botIsRed) "Red ▼" else "Black ▼"
        TimerBar(botTimeMs, botActive && state.status == GameStatus.PLAYING, botLabel)

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
internal fun XiangqiBoardViewPublic(
    state: GameState,
    flipped: Boolean,
    modifier: Modifier,
    onSquare: (XqSquare) -> Unit,
    onAnimationDone: () -> Unit = {},
) {
    val animProgress = remember { Animatable(0f) }
    val anim = state.animatingMove
    LaunchedEffect(anim) {
        if (anim != null) {
            animProgress.snapTo(0f)
            animProgress.animateTo(1f, animationSpec = tween(durationMillis = 200))
            onAnimationDone()
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val spacingW = maxWidth  / 10
        val spacingH = maxHeight / 11
        val marginX  = spacingW
        val marginY  = spacingH

        Canvas(modifier = Modifier.fillMaxSize()) {
            val sW = size.width  / 10f
            val sH = size.height / 11f
            val mX = sW
            val mY = sH

            drawRect(color = BOARD_BG)

            drawRect(
                color = RIVER_COLOR,
                topLeft = Offset(0f, mY + 4 * sH),
                size = androidx.compose.ui.geometry.Size(size.width, sH)
            )

            for (f in 0..8) {
                val x = mX + f * sW
                drawLine(LINE_COLOR, Offset(x, mY),            Offset(x, mY + 4 * sH), strokeWidth = 1.5f)
                drawLine(LINE_COLOR, Offset(x, mY + 5 * sH),   Offset(x, mY + 9 * sH), strokeWidth = 1.5f)
                if (f == 0 || f == 8) {
                    drawLine(LINE_COLOR, Offset(x, mY + 4 * sH), Offset(x, mY + 5 * sH), strokeWidth = 1.5f)
                }
            }

            for (r in 0..9) {
                val y = mY + r * sH
                drawLine(LINE_COLOR, Offset(mX, y), Offset(mX + 8 * sW, y), strokeWidth = 1.5f)
            }

            // Palace diagonals — top palace (rows 0-2, files 3-5)
            drawLine(LINE_COLOR, Offset(mX + 3*sW, mY),        Offset(mX + 5*sW, mY + 2*sH), strokeWidth = 1.5f)
            drawLine(LINE_COLOR, Offset(mX + 5*sW, mY),        Offset(mX + 3*sW, mY + 2*sH), strokeWidth = 1.5f)

            // Palace diagonals — bottom palace (rows 7-9, files 3-5)
            drawLine(LINE_COLOR, Offset(mX + 3*sW, mY + 7*sH), Offset(mX + 5*sW, mY + 9*sH), strokeWidth = 1.5f)
            drawLine(LINE_COLOR, Offset(mX + 5*sW, mY + 7*sH), Offset(mX + 3*sW, mY + 9*sH), strokeWidth = 1.5f)
        }

        // River labels
        val leftRiver  = if (!flipped) "楚  河" else "漢  界"
        val rightRiver = if (!flipped) "漢  界" else "楚  河"
        Box(
            modifier = Modifier
                .offset(x = marginX, y = marginY + spacingH * 4)
                .width(spacingW * 4f)
                .height(spacingH),
            contentAlignment = Alignment.Center
        ) {
            Text(leftRiver, color = LINE_COLOR, fontSize = (spacingW.value * 0.5f).sp,
                fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .offset(x = marginX + spacingW * 4.5f, y = marginY + spacingH * 4)
                .width(spacingW * 3.5f)
                .height(spacingH),
            contentAlignment = Alignment.Center
        ) {
            Text(rightRiver, color = LINE_COLOR, fontSize = (spacingW.value * 0.5f).sp,
                fontWeight = FontWeight.Bold)
        }

        val redLabels   = if (state.useWesternPieces) RED_LABELS_WESTERN   else RED_LABELS
        val blackLabels = if (state.useWesternPieces) BLACK_LABELS_WESTERN else BLACK_LABELS

        for (r in 0..9) {
            for (f in 0..8) {
                val displayRank = if (flipped) r else 9 - r
                val sq = XqSquare(f, displayRank)
                val isSelected = state.selected == sq
                val isLastMove = state.lastMove?.let { it.first == sq || it.second == sq } == true
                val suppressPiece = anim != null && (sq == anim.from || sq == anim.to)
                val piece = if (suppressPiece) Piece.EMPTY else state.board.get(sq)

                val cx = marginX + spacingW * f
                val cy = marginY + spacingH * r

                if (isSelected || isLastMove) {
                    val hlSize = minOf(spacingW.value, spacingH.value).dp
                    Box(
                        modifier = Modifier
                            .offset(x = cx - hlSize / 2, y = cy - hlSize / 2)
                            .size(width = hlSize, height = hlSize)
                            .background(if (isSelected) SELECT_COL else LAST_COL)
                    )
                }

                Box(
                    modifier = Modifier
                        .offset(x = cx - spacingW / 2, y = cy - spacingH / 2)
                        .size(spacingW, spacingH)
                        .clickable { onSquare(sq) }
                )

                if (piece != Piece.EMPTY) {
                    val isRed = piece > 0
                    val absPiece = kotlin.math.abs(piece)
                    val label = if (isRed) redLabels[absPiece] ?: "?" else blackLabels[absPiece] ?: "?"
                    val pieceSize = min(spacingW.value, spacingH.value) * 0.82f

                    Box(
                        modifier = Modifier
                            .offset(x = cx - (pieceSize / 2).dp, y = cy - (pieceSize / 2).dp)
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

        // Floating piece drawn on top during animation
        if (anim != null) {
            val progress = animProgress.value
            val isRed = anim.piece > 0
            val absPiece = kotlin.math.abs(anim.piece)
            val label = if (isRed) redLabels[absPiece] ?: "?" else blackLabels[absPiece] ?: "?"
            val pieceSize = min(spacingW.value, spacingH.value) * 0.82f

            // Convert board square to display row (same formula as above)
            fun squareToRow(sq: XqSquare): Int = if (flipped) sq.rank else 9 - sq.rank

            val fromRow = squareToRow(anim.from)
            val toRow   = squareToRow(anim.to)
            val fromCx = marginX + spacingW * anim.from.file
            val fromCy = marginY + spacingH * fromRow
            val toCx   = marginX + spacingW * anim.to.file
            val toCy   = marginY + spacingH * toRow

            val cx = fromCx + (toCx - fromCx) * progress
            val cy = fromCy + (toCy - fromCy) * progress

            Box(
                modifier = Modifier
                    .offset(x = cx - (pieceSize / 2).dp, y = cy - (pieceSize / 2).dp)
                    .size(pieceSize.dp)
                    .clip(CircleShape)
                    .background(PIECE_BG),
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
