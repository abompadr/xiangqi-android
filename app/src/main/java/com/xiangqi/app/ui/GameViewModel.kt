package com.xiangqi.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiangqi.app.data.Profile
import com.xiangqi.app.engine.PikafishEngine
import com.xiangqi.app.engine.XiangqiBoard
import com.xiangqi.app.engine.XqSquare
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GameStatus { PLAYING, RED_WIN, BLACK_WIN, DRAW, ENGINE_ERROR }

data class GameState(
    val board: XiangqiBoard = XiangqiBoard(),
    val selected: XqSquare? = null,
    val redTimeMs: Long = 0,
    val blackTimeMs: Long = 0,
    val status: GameStatus = GameStatus.PLAYING,
    val engineThinking: Boolean = false,
    val lastMove: Pair<XqSquare, XqSquare>? = null,
    val errorMessage: String = ""
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = PikafishEngine(app)
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state

    private var timerJob: Job? = null
    private var skillLevel: Int = 10
    private var playerIsRed: Boolean = true
    private var engineReady = false

    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        _state.value = _state.value.copy(
            engineThinking = false,
            status = GameStatus.ENGINE_ERROR,
            errorMessage = "${t.javaClass.simpleName}: ${t.message}"
        )
    }

    fun startGame(p: Profile) {
        skillLevel = p.skillLevel
        playerIsRed = p.playAsRed
        val timeMs = if (p.timeControlMinutes == 0) Long.MAX_VALUE / 2
                     else p.timeControlMinutes * 60_000L
        val board = XiangqiBoard()
        _state.value = GameState(
            board = board,
            redTimeMs = timeMs,
            blackTimeMs = timeMs
        )
        startTimer()
        viewModelScope.launch(exceptionHandler) {
            withContext(Dispatchers.IO) {
                engine.start()
                engine.setSkillLevel(skillLevel)
                engine.setPosition(XiangqiBoard.START_FEN)
            }
            engineReady = true
            // If player chose Black, engine plays first as Red
            if (!playerIsRed) engineMove()
        }
    }

    fun onSquareTapped(sq: XqSquare) {
        val s = _state.value
        if (s.status != GameStatus.PLAYING) return
        if (s.engineThinking) return
        if (s.board.redToMove != playerIsRed) return   // not player's turn

        val piece = s.board.get(sq)
        val isOwnPiece = if (playerIsRed) piece > 0 else piece < 0

        if (s.selected == null) {
            if (isOwnPiece) _state.value = s.copy(selected = sq)
        } else {
            when {
                s.selected == sq -> _state.value = s.copy(selected = null)
                isOwnPiece       -> _state.value = s.copy(selected = sq)
                else             -> {
                    val legal = s.board.legalMovesFrom(s.selected)
                    if (sq in legal) applyPlayerMove(s.selected, sq)
                    else if (piece < 0) _state.value = s.copy(selected = null)  // tapped empty/enemy but illegal
                    // else: tapped own piece already handled above
                }
            }
        }
    }

    private fun applyPlayerMove(from: XqSquare, to: XqSquare) {
        val s = _state.value
        val uci = "${from}${to}"
        s.board.applyUci(uci)
        _state.value = s.copy(selected = null, lastMove = from to to)
        checkGameOver()
        if (_state.value.status == GameStatus.PLAYING) {
            viewModelScope.launch(exceptionHandler) { engineMove() }
        }
    }

    private suspend fun engineMove() {
        if (!engineReady) return
        _state.value = _state.value.copy(engineThinking = true)
        delay(600)
        val s = _state.value
        // Update engine position with full move history
        val uci = withContext(Dispatchers.IO) {
            try {
                engine.setPosition(XiangqiBoard.START_FEN, s.board.moveHistory)
                val timeLimitMs = when {
                    skillLevel <= 3  -> 200
                    skillLevel <= 7  -> 500
                    skillLevel <= 12 -> 1000
                    skillLevel <= 17 -> 2000
                    else             -> 3000
                }
                engine.getBestMove(timeLimitMs)
            } catch (t: Throwable) {
                "ERROR:${t.javaClass.simpleName}:${t.message}"
            }
        }

        if (uci.startsWith("ERROR:")) {
            _state.value = _state.value.copy(
                engineThinking = false,
                status = GameStatus.ENGINE_ERROR,
                errorMessage = uci
            )
            return
        }

        if (uci.isEmpty() || uci == "(none)") {
            // Engine has no moves — Red wins
            _state.value = _state.value.copy(
                engineThinking = false,
                status = GameStatus.RED_WIN
            )
            timerJob?.cancel()
            return
        }

        try {
            val from = XqSquare.fromAlg(uci.substring(0, 2))
            val to   = XqSquare.fromAlg(uci.substring(2, 4))
            _state.value.board.applyUci(uci)
            _state.value = _state.value.copy(
                engineThinking = false,
                lastMove = from to to
            )
            checkGameOver()
        } catch (t: Throwable) {
            _state.value = _state.value.copy(
                engineThinking = false,
                status = GameStatus.ENGINE_ERROR,
                errorMessage = "applyUci($uci): ${t.javaClass.simpleName}: ${t.message}"
            )
        }
    }

    private fun checkGameOver() {
        val s = _state.value
        // Check if either General has been captured
        var redGeneral = false; var blackGeneral = false
        for (r in 0..9) for (f in 0..8) {
            val p = s.board.get(r, f)
            if (p == com.xiangqi.app.engine.Piece.GENERAL) redGeneral = true
            if (p == -com.xiangqi.app.engine.Piece.GENERAL) blackGeneral = true
        }
        when {
            !blackGeneral -> { _state.value = s.copy(status = GameStatus.RED_WIN);   timerJob?.cancel(); return }
            !redGeneral   -> { _state.value = s.copy(status = GameStatus.BLACK_WIN); timerJob?.cancel(); return }
        }
        // Checkmate: current side to move has no legal moves
        if (!s.board.hasAnyLegalMove()) {
            val winner = if (s.board.redToMove) GameStatus.BLACK_WIN else GameStatus.RED_WIN
            _state.value = s.copy(status = winner)
            timerJob?.cancel()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val s = _state.value
                if (s.status != GameStatus.PLAYING) break
                if (s.board.redToMove) {
                    val newTime = s.redTimeMs - 100
                    if (newTime <= 0) {
                        _state.value = s.copy(redTimeMs = 0, status = GameStatus.BLACK_WIN)
                        break
                    }
                    _state.value = s.copy(redTimeMs = newTime)
                } else {
                    val newTime = s.blackTimeMs - 100
                    if (newTime <= 0) {
                        _state.value = s.copy(blackTimeMs = 0, status = GameStatus.RED_WIN)
                        break
                    }
                    _state.value = s.copy(blackTimeMs = newTime)
                }
            }
        }
    }

    fun playAgain() {
        val current = _state.value
        // Reverse colors and restart with same profile settings
        val reversedProfile = com.xiangqi.app.data.Profile(
            id = 0,
            name = "",
            skillLevel = skillLevel,
            timeControlMinutes = if (_state.value.redTimeMs > 30 * 60_000L * 10) 0
                                  else (_state.value.redTimeMs / 60_000L).toInt(),
            playAsRed = !playerIsRed
        )
        startGame(reversedProfile)
    }

    fun resign() {
        _state.value = _state.value.copy(status = GameStatus.BLACK_WIN)
        timerJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        engine.stop()
    }
}
