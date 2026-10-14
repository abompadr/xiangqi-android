package com.xiangqi.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.xiangqi.app.engine.Puzzle
import com.xiangqi.app.engine.PuzzleBook
import com.xiangqi.app.engine.XiangqiBoard
import com.xiangqi.app.engine.XqSquare
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class PuzzleStatus { PLAYING, CORRECT, WRONG }

data class PuzzleState(
    val puzzle: Puzzle? = null,
    val board: XiangqiBoard = XiangqiBoard(),
    val selected: XqSquare? = null,
    val lastMove: Pair<XqSquare, XqSquare>? = null,
    val status: PuzzleStatus = PuzzleStatus.PLAYING,
    val moveIndex: Int = 0,       // index into solution; even = player's turn, odd = engine reply
    val showHint: Boolean = false,
    val useWesternPieces: Boolean = false,
)

class PuzzleViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PuzzleState())
    val state: StateFlow<PuzzleState> = _state

    init { PuzzleBook.load(app) }

    fun loadPuzzle(puzzle: Puzzle, useWesternPieces: Boolean = false) {
        val board = XiangqiBoard()
        board.loadFen(puzzle.fen)
        _state.value = PuzzleState(
            puzzle           = puzzle,
            board            = board,
            status           = PuzzleStatus.PLAYING,
            moveIndex        = 0,
            useWesternPieces = useWesternPieces,
        )
    }

    fun onSquareTapped(sq: XqSquare) {
        val s = _state.value
        if (s.status != PuzzleStatus.PLAYING) return
        if (s.puzzle == null) return
        if (s.moveIndex >= s.puzzle.solution.size) return
        // Only allow tapping on player's turns (even indices)
        if (s.moveIndex % 2 != 0) return

        val piece = s.board.get(sq)
        val playerIsRed = s.board.redToMove
        val isOwnPiece = if (playerIsRed) piece > 0 else piece < 0

        if (s.selected == null) {
            if (isOwnPiece) _state.value = s.copy(selected = sq)
            return
        }

        if (s.selected == sq) {
            _state.value = s.copy(selected = null)
            return
        }

        if (isOwnPiece) {
            _state.value = s.copy(selected = sq)
            return
        }

        val legal = s.board.legalMovesFrom(s.selected)
        if (sq !in legal) {
            _state.value = s.copy(selected = null)
            return
        }

        val uci = "${s.selected}${sq}"
        val expected = s.puzzle.solution[s.moveIndex]
        if (uci != expected) {
            _state.value = s.copy(selected = null, status = PuzzleStatus.WRONG)
            return
        }

        applyMove(uci)
    }

    private fun applyMove(uci: String) {
        val s = _state.value
        val puzzle = s.puzzle ?: return
        val from = XqSquare.fromAlg(uci.substring(0, 2))
        val to   = XqSquare.fromAlg(uci.substring(2, 4))
        s.board.applyUci(uci)
        val nextIndex = s.moveIndex + 1

        if (nextIndex >= puzzle.solution.size) {
            _state.value = s.copy(
                selected  = null,
                lastMove  = from to to,
                moveIndex = nextIndex,
                status    = PuzzleStatus.CORRECT,
            )
            return
        }

        _state.value = s.copy(
            selected  = null,
            lastMove  = from to to,
            moveIndex = nextIndex,
        )

        // Apply engine reply (odd index) automatically
        if (nextIndex % 2 == 1 && nextIndex < puzzle.solution.size) {
            val replyUci = puzzle.solution[nextIndex]
            val rFrom = XqSquare.fromAlg(replyUci.substring(0, 2))
            val rTo   = XqSquare.fromAlg(replyUci.substring(2, 4))
            s.board.applyUci(replyUci)
            _state.value = _state.value.copy(
                lastMove  = rFrom to rTo,
                moveIndex = nextIndex + 1,
            )
        }
    }

    fun retry() {
        val s = _state.value
        val puzzle = s.puzzle ?: return
        loadPuzzle(puzzle, s.useWesternPieces)
    }

    fun toggleHint() {
        _state.value = _state.value.copy(showHint = !_state.value.showHint)
    }
}
